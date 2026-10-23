package dev.plattnericus.pokyh.core.bell

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.core.util.GradeEntryOrder
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the bell shows ([BellFeed]), read from where the rest of the app reads it — the web's
 * `components/app/useBell.ts`: the stored grades first, then WebUntis; the inbox; the to-dos and
 * the class's reminders from the backend; the streak from the account's activity. Opening the
 * bell reads again what is old enough. What was seen, and what was cleared away, is remembered
 * per account on the device and with the account (pokyh-backend `/users/me/bell`), so every
 * device — web and app — shows the same. One store for the whole app: every tab's bell is this one.
 */
@Singleton
class BellStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
    private val offlineStore: OfflineStore,
) {
    data class StreakView(val days: Int, val longest: Int, val week: List<Pair<String, Boolean>>)

    data class State(
        val feed: BellFeed.Feed = BellFeed.Feed(emptyList(), emptyList()),
        val fresh: Set<String> = emptySet(),
        /** New since the bell was last opened; 0 until the account's marks are in. */
        val newCount: Int = 0,
        val streak: StreakView? = null,
        /** Nothing has arrived yet. */
        val loading: Boolean = true,
        val now: Long = System.currentTimeMillis(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("pokyh_bell", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var owner = ""
    private var input = BellFeed.Input()
    private var seen = BellFeed.SeenState()
    private var synced = false
    private val asked = HashMap<BellFeed.Source, Long>()
    private val again = mapOf(
        BellFeed.Source.Todos to 30_000L, BellFeed.Source.Reminders to 30_000L,
        BellFeed.Source.Grades to 60_000L, BellFeed.Source.Messages to 60_000L, BellFeed.Source.Streak to 600_000L,
    )

    // The account's copy of the marks: what it is known to hold, when it was last read.
    private val remoteSeen = HashSet<String>()
    private val remoteCleared = HashSet<String>()
    private var remoteRead = false
    private var pulledAt = 0L
    private var pushJob: Job? = null

    /** Starts (or switches to) the signed-in account and reads what isn't read yet. */
    fun start() {
        val session = appState.session.value ?: return
        val who = session.username.lowercase()
        if (who != owner) adopt(who)
        load(session, force = false)
        pull(session)
    }

    /** On opening the bell: everything old enough again, and the account's marks. */
    fun refresh() {
        val session = appState.session.value ?: return
        if (session.username.lowercase() != owner) adopt(session.username.lowercase())
        load(session, force = true)
        pull(session)
    }

    /** Everything shown counts as seen. */
    fun seeAll() {
        saveSeen(BellFeed.markSeen(BellFeed.settleSeen(seen, current().feed, loaded(), System.currentTimeMillis()), current().feed))
    }

    /** These items leave the feed for good, on every device of the account. */
    fun clear(keys: List<String>) {
        saveSeen(BellFeed.clearKeys(seen, keys))
    }

    private fun current() = _state.value

    private fun adopt(who: String) {
        owner = who
        input = BellFeed.Input()
        seen = readSeen(who)
        synced = false
        asked.clear()
        remoteSeen.clear()
        remoteCleared.clear()
        remoteRead = false
        pulledAt = 0L
        publish()
    }

    private fun loaded(): List<BellFeed.Source> = buildList {
        if (input.grades != null) add(BellFeed.Source.Grades)
        if (input.messages != null) add(BellFeed.Source.Messages)
        if (input.reminders != null) add(BellFeed.Source.Reminders)
        if (input.todos != null) add(BellFeed.Source.Todos)
        if (input.streakRead) add(BellFeed.Source.Streak)
    }

    private fun due(source: BellFeed.Source, force: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val at = asked[source]
        if (at != null && !(force && now - at > (again[source] ?: 60_000L))) return false
        asked[source] = now
        return true
    }

    private fun patch(who: String, change: (BellFeed.Input) -> BellFeed.Input, streak: StreakView? = null) {
        // An answer for an account that has since signed out is dropped.
        if (who != owner) return
        input = change(input)
        if (streak != null) _state.update { it.copy(streak = streak) }
        publish()
    }

    private fun load(session: UserSession, force: Boolean) {
        val who = owner
        if (session.hasUntis) {
            if (due(BellFeed.Source.Grades, force)) scope.launch {
                val key = "grades-${session.studentId}-${SchoolDates.currentSchoolYear}"
                val serializer = ListSerializer(SubjectGrades.serializer())
                // What the Noten tab stored counts at once; the fresh answer replaces it.
                runCatching { offlineStore.peek(key, serializer) }.getOrNull()?.let { patch(who, { i -> i.copy(grades = gradesFrom(it.value)) }) }
                runCatching { offlineStore.load(key, serializer) { untisClient.grades(session, session.studentId, null) } }
                    .onSuccess { r ->
                        GradeEntryOrder.observe(r.value)
                        patch(who, { i -> i.copy(grades = gradesFrom(r.value)) })
                    }
                    .onFailure { asked.remove(BellFeed.Source.Grades) }
            }
            if (due(BellFeed.Source.Messages, force)) scope.launch {
                runCatching { untisClient.messages(session, MessageFolder.Inbox) }
                    .onSuccess { list ->
                        patch(who, { i ->
                            i.copy(messages = list.map { m -> BellFeed.MessageIn(m.id, m.subject, m.senderName, BellFeed.parseTime(m.sentDate) ?: 0L, m.isRead) })
                        })
                    }
                    .onFailure { asked.remove(BellFeed.Source.Messages) }
            }
        }
        val token = session.apiToken ?: return
        if (due(BellFeed.Source.Todos, force)) scope.launch {
            runCatching { backendClient.todos(session.username, token) }
                .onSuccess { list ->
                    patch(who, { i -> i.copy(todos = list.map { BellFeed.TodoIn(it.id, it.title, BellFeed.parseTime(it.dueAt), it.done) }) })
                }
                .onFailure { asked.remove(BellFeed.Source.Todos) }
        }
        val classId = session.classId
        if (classId != null && due(BellFeed.Source.Reminders, force)) scope.launch {
            runCatching { backendClient.reminders(classId, token) }
                .onSuccess { list ->
                    patch(who, { i ->
                        i.copy(reminders = list.map {
                            BellFeed.ReminderIn(
                                id = it.id,
                                title = it.title,
                                remindAt = BellFeed.parseTime(it.remindAt) ?: 0L,
                                createdAt = BellFeed.parseTime(it.createdAt) ?: 0L,
                                by = it.createdByUsername.ifEmpty { it.createdByName },
                                mine = (session.stableUid != null && it.createdBy == session.stableUid) || it.createdByUsername == session.username,
                            )
                        })
                    })
                }
                .onFailure { asked.remove(BellFeed.Source.Reminders) }
        }
        if (due(BellFeed.Source.Streak, force)) scope.launch {
            runCatching { backendClient.accountActivity(token) }
                .onSuccess { a ->
                    val today = BellFeed.parseTime(a.today) ?: BellFeed.startOfDay(System.currentTimeMillis())
                    val todayActive = a.days.lastOrNull()?.active ?: false
                    val active = a.days.filter { it.active }.mapNotNull { BellFeed.parseTime(it.date) }
                    val streak = BellFeed.StreakIn(
                        days = a.totals.currentStreak,
                        end = if (todayActive) today else BellFeed.addDays(today, -1),
                        lost = BellFeed.lostStreak(active, today),
                    )
                    val view = StreakView(a.totals.currentStreak, a.totals.longestStreak, a.days.takeLast(7).map { it.date to it.active })
                    patch(who, { i -> i.copy(streak = streak, streakRead = true) }, view)
                }
                .onFailure { patch(who, { i -> i.copy(streakRead = true) }) }
        }
    }

    private fun gradesFrom(subjects: List<SubjectGrades>): List<BellFeed.GradeIn> = subjects.flatMap { s ->
        s.grades.map { g ->
            BellFeed.GradeIn(g.id, s.subjectName, s.lessonId, g.markDisplayValue, g.date, g.examType, GradeEntryOrder.enteredAt(g))
        }
    }

    /** Builds the feed from what is read; a source read for the first time settles what is old. */
    private fun publish() {
        val now = System.currentTimeMillis()
        val feed = BellFeed.withoutCleared(BellFeed.build(input, now), seen.cleared)
        val settled = BellFeed.settleSeen(seen, feed, loaded(), now)
        if (settled != seen) saveSeen(settled, republish = false)
        val fresh = BellFeed.freshKeys(feed, seen)
        _state.update {
            it.copy(feed = feed, fresh = fresh, newCount = if (synced) fresh.size else 0, loading = loaded().isEmpty(), now = now)
        }
    }

    private fun saveSeen(next: BellFeed.SeenState, republish: Boolean = true) {
        if (next == seen) return
        seen = next
        writeSeen(owner, next)
        if (republish) publish()
        schedulePush()
    }

    // ── The account's marks ─────────────────────────────────────────────────

    private fun pull(session: UserSession) {
        val token = session.apiToken
        if (token == null) {
            synced = true
            publish()
            return
        }
        if (System.currentTimeMillis() - pulledAt < 30_000L) return
        pulledAt = System.currentTimeMillis()
        val who = owner
        // The badge waits for the account's marks (at most 4 s), so another device's clearing
        // never flashes up as new.
        scope.launch {
            delay(4_000)
            if (who == owner && !synced) { synced = true; publish() }
        }
        scope.launch {
            runCatching { backendClient.bellMarks(token) }
                .onSuccess { marks ->
                    if (who != owner) return@onSuccess
                    remoteSeen.addAll(marks.seen)
                    remoteCleared.addAll(marks.cleared)
                    remoteRead = true
                    seen = BellFeed.mergeRemote(seen, marks.seen, marks.cleared)
                    writeSeen(owner, seen)
                    schedulePush()
                }
            if (who == owner) { synced = true; publish() }
        }
    }

    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(800)
            val session = appState.session.value ?: return@launch
            val token = session.apiToken ?: return@launch
            if (!remoteRead || session.username.lowercase() != owner) return@launch
            val seenDue = seen.keys.filter { it !in remoteSeen && it !in seen.cleared }.take(400)
            val clearedDue = seen.cleared.filter { it !in remoteCleared }.take(400)
            if (seenDue.isEmpty() && clearedDue.isEmpty()) return@launch
            runCatching { backendClient.markBell(BackendClient.BellMarks(seenDue, clearedDue), token) }
                .onSuccess {
                    remoteSeen.addAll(seenDue)
                    remoteSeen.addAll(clearedDue)
                    remoteCleared.addAll(clearedDue)
                }
        }
    }

    // ── On the device ──────────────────────────────────────────────────────

    private fun readSeen(who: String): BellFeed.SeenState = runCatching {
        val o = JSONObject(prefs.getString("seen_$who", null) ?: return BellFeed.SeenState())
        fun list(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        BellFeed.SeenState(
            keys = list("keys"),
            sources = list("sources").mapNotNull { s -> BellFeed.Source.entries.firstOrNull { it.name == s } },
            cleared = list("cleared"),
        )
    }.getOrDefault(BellFeed.SeenState())

    private fun writeSeen(who: String, s: BellFeed.SeenState) {
        if (who.isEmpty()) return
        val o = JSONObject()
            .put("keys", JSONArray(s.keys))
            .put("sources", JSONArray(s.sources.map { it.name }))
            .put("cleared", JSONArray(s.cleared))
        prefs.edit().putString("seen_$who", o.toString()).apply()
    }
}
