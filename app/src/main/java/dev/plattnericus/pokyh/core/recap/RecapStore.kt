package dev.plattnericus.pokyh.core.recap

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recaps in the app — the web's `lib/recap.ts`, `lib/recap-data.ts` and `RecapHost`: the
 * recaps the account may open now (pokyh-backend `/recaps`), which one is on screen, and the
 * WebUntis figures of a recap's period, worked out on this device and sent nowhere.
 *
 * A recap no device of the account has shown yet opens by itself after signing in, once the app
 * has been quiet for a moment. Whether it was shown — and watched to the end — is kept on the
 * POKYH server (`POST /recaps/:id/view`), so a recap opened on the web is the header button here.
 */
@Singleton
class RecapStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val untisClient: UntisClient,
) {
    data class State(
        /** null until the first answer. */
        val recaps: List<BackendClient.Recap>? = null,
        /** The recap on screen. */
        val open: String? = null,
    )

    data class Progress(val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var started = false
    private var refresher: Job? = null
    private val autoTried = HashSet<String>()
    private val running = HashMap<String, Deferred<RecapFigures.Untis>>()
    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    private val zone = ZoneId.of("Europe/Rome")

    /** Follows the signed-in account: its recaps, every 15 minutes again. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            appState.session.distinctUntilChangedBy { it?.username }.collect { s ->
                refresher?.cancel()
                _state.value = State()
                if (s == null) return@collect
                refresher = scope.launch {
                    while (true) {
                        refresh()
                        maybeAutoOpen()
                        delay(15 * 60_000L)
                    }
                }
            }
        }
    }

    /** The account's recaps; an error leaves the last answer (or none) in place. */
    suspend fun refresh(): List<BackendClient.Recap> {
        val list = runCatching { appState.withFreshApiToken { backendClient.recaps(it) } }.getOrNull()
        if (list != null) _state.update { it.copy(recaps = list) }
        return _state.value.recaps.orEmpty()
    }

    // Opens by itself once nothing else is on screen for a moment (the web's SETTLE_MS, then quiet).
    private suspend fun maybeAutoOpen() {
        val due = _state.value.recaps?.firstOrNull { it.openedAt == null } ?: return
        if (due.id in autoTried || _state.value.open != null) return
        delay(2200)
        if (appState.phase.value != AppState.Phase.Authed || appState.busy.value) return
        autoTried.add(due.id)
        open(due.id)
    }

    fun open(id: String) = _state.update { it.copy(open = id) }

    fun close() = _state.update { it.copy(open = null) }

    suspend fun detail(id: String): BackendClient.RecapDetail = appState.withFreshApiToken { backendClient.recap(id, it) }

    /** Opened (and, [finished], watched to the end), on every device of the account. */
    fun markView(id: String, finished: Boolean) {
        scope.launch {
            val view = runCatching { appState.withFreshApiToken { backendClient.markRecapView(id, finished, it) } }.getOrNull() ?: return@launch
            _state.update { s -> s.copy(recaps = s.recaps?.map { r -> if (r.id == id) r.copy(openedAt = view.openedAt, finishedAt = view.finishedAt) else r }) }
        }
    }

    // ── The WebUntis figures (lib/recap-data.ts) ───────────────────────────────

    private val prefs get() = context.getSharedPreferences("pokyh_recapweeks", Context.MODE_PRIVATE)

    /** Per school and student: two accounts at different schools can share a student id. */
    private fun weeksKey(session: UserSession) = "${session.schoolRef.server}/${session.schoolRef.loginName}:${session.studentId}"

    private fun readWeeks(key: String): MutableMap<String, RecapFigures.Week> = runCatching {
        val o = JSONObject(prefs.getString(key, null) ?: return@runCatching LinkedHashMap())
        val out = LinkedHashMap<String, RecapFigures.Week>()
        for (monday in o.keys()) {
            val w = o.getJSONObject(monday)
            val days = HashMap<Int, List<RecapFigures.Lesson>>()
            val d = w.getJSONObject("days")
            for (day in d.keys()) {
                val arr = d.getJSONArray(day)
                days[day.toInt()] = (0 until arr.length()).map { i ->
                    val l = arr.getJSONArray(i)
                    RecapFigures.Lesson(l.getInt(0), l.getInt(1), l.getString(2), l.getString(3), l.getString(4), l.getInt(5))
                }
            }
            out[monday] = RecapFigures.Week(w.getLong("at"), days)
        }
        out
    }.getOrDefault(LinkedHashMap())

    private fun writeWeeks(key: String, weeks: Map<String, RecapFigures.Week>) {
        // A school year and a bit.
        val kept = weeks.keys.sorted().takeLast(60)
        val o = JSONObject()
        for (monday in kept) {
            val w = weeks.getValue(monday)
            val days = JSONObject()
            for ((day, lessons) in w.days) {
                days.put(day.toString(), JSONArray().apply {
                    lessons.forEach { put(JSONArray().put(it.start).put(it.end).put(it.subject).put(it.teachers).put(it.room).put(it.state)) }
                })
            }
            o.put(monday, JSONObject().put("at", w.at).put("days", days))
        }
        prefs.edit().putString(key, o.toString()).apply()
    }

    private fun isDue(week: RecapFigures.Week?, monday: String, now: Long): Boolean {
        if (week == null) return true
        val over = LocalDate.parse(monday).plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        // Loaded before the week was over: lessons may have changed since.
        val ttl = when {
            week.at < over -> 30L * 60_000
            now - over > 14L * 24 * 60 * 60_000 -> 30L * 24 * 60 * 60_000
            else -> 24L * 60 * 60_000
        }
        return now - week.at > ttl
    }

    private fun lessonsOf(entries: List<TimetableEntry>): Map<Int, List<RecapFigures.Lesson>> {
        val byDay = LinkedHashMap<Int, MutableList<RecapFigures.Lesson>>()
        for (e in entries) {
            val subject = e.subjectLong.ifEmpty { e.subjectName }
            if (subject.isEmpty()) continue
            val start = RecapFigures.toMinutes(e.startTime)
            val end = RecapFigures.toMinutes(e.endTime)
            if (end <= start) continue
            val state = when {
                e.isCancelled -> RecapFigures.CANCELLED
                e.isExam -> RecapFigures.EXAM
                e.isSubstitution -> RecapFigures.SUBSTITUTION
                else -> RecapFigures.LESSON
            }
            byDay.getOrPut(e.date) { ArrayList() }.add(RecapFigures.Lesson(start, end, subject, e.teacherLongName ?: e.teacherName, e.roomName, state))
        }
        return byDay.mapValues { it.value.sortedBy { l -> l.start } }
    }

    /**
     * The WebUntis figures of a recap's period, for the parts it shows; the banner's preload and
     * the story share one load. Parts WebUntis does not give this account come back as null.
     */
    fun loadUntis(recap: BackendClient.Recap): Deferred<RecapFigures.Untis> {
        val session = appState.session.value ?: throw AppError.sessionExpired
        val key = "${session.studentId}:${recap.periodFrom}:${recap.periodTo}:${recap.sections.joinToString(",")}"
        running[key]?.let { if (!it.isCancelled) return it }
        val job = scope.async(Dispatchers.IO) { run(session, recap, key) }
        running[key] = job
        job.invokeOnCompletion { e -> if (e != null) running.remove(key) }
        return job
    }

    private suspend fun run(session: UserSession, recap: BackendClient.Recap, key: String): RecapFigures.Untis = coroutineScope {
        val sid = session.studentId
        val from = RecapFigures.numOfIso(recap.periodFrom)
        val to = RecapFigures.numOfIso(recap.periodTo)
        val now = LocalDateTime.now(zone)
        val until = minOf(to, RecapFigures.numOf(now.toLocalDate()))
        fun wants(s: String) = s in recap.sections
        val weeksKey = weeksKey(session)
        val stored = withContext(Dispatchers.IO) { readWeeks(weeksKey) }
        val mondays = if (wants("school") || wants("absences")) RecapFigures.periodWeeks(from, to, now) else emptyList()
        val due = mondays.filter { isDue(stored[it], it, System.currentTimeMillis()) }
        val others = listOf(wants("absences"), wants("absences"), wants("grades"), wants("exams"), wants("classreg")).count { it }
        val total = due.size + others
        var done = 0
        fun step() {
            done += 1
            _progress.update { it + (key to Progress(done, total)) }
        }
        _progress.update { it + (key to Progress(0, total)) }

        var expired: AppError? = null
        suspend fun <T> settle(task: suspend () -> T): T? = try {
            task()
        } catch (e: AppError) {
            if (e.isSessionExpired) expired = e
            null
        } catch (_: Exception) {
            null
        } finally {
            step()
        }

        val years = setOf(SchoolDates.schoolYearOf(from), SchoolDates.schoolYearOf(until)).toList()
        fun yearArg(y: Int) = if (y == SchoolDates.currentSchoolYear) null else y

        val absences = async { if (wants("absences")) settle { untisClient.absences(session, sid, from.toString(), to.toString()) } else null }
        val times = async { if (wants("absences")) settle { untisClient.absenceTimes(session, sid, from.toString(), to.toString()) } else null }
        val grades = async { if (wants("grades")) settle { years.flatMap { untisClient.grades(session, sid, yearArg(it)) } } else null }
        val homework = async { if (wants("exams")) settle { untisClient.homeworks(session, from, to) } else null }
        val classreg = async { if (wants("classreg")) settle { years.flatMap { untisClient.classregEvents(session, sid, it) } } else null }

        // The weeks, a few at a time; a failed one once more at the end.
        val queue = ArrayDeque(due)
        val failed = ArrayList<String>()
        suspend fun loadWeek(monday: String, retry: Boolean) {
            try {
                val entries = untisClient.timetable(session, sid, monday)
                synchronized(stored) { stored[monday] = RecapFigures.Week(System.currentTimeMillis(), lessonsOf(entries)) }
            } catch (e: AppError) {
                if (e.isSessionExpired) expired = e else if (!retry) synchronized(failed) { failed.add(monday) }
            } catch (_: Exception) {
                if (!retry) synchronized(failed) { failed.add(monday) }
            }
        }
        (0 until minOf(3, due.size)).map {
            async {
                while (expired == null) {
                    val monday = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                    loadWeek(monday, retry = false)
                    step()
                }
            }
        }.awaitAll()
        for (monday in failed.toList()) {
            if (expired != null) break
            loadWeek(monday, retry = true)
        }
        if (due.isNotEmpty()) withContext(Dispatchers.IO) { writeWeeks(weeksKey, stored) }
        val absencesList = absences.await()
        val timesList = times.await()
        val gradesList = grades.await()
        val homeworkList = homework.await()
        val classregList = classreg.await()
        expired?.let { throw it }

        RecapFigures.untis(
            RecapFigures.Sources(
                from = from,
                to = to,
                now = now,
                weeks = mondays.mapNotNull { m -> stored[m]?.let { m to it } }.toMap(),
                absences = absencesList?.map { RecapFigures.AbsenceIn(it.id, it.startDate, it.endDate, it.startTime, it.endTime, it.isExcused, it.hours, it.subjectName) },
                times = timesList?.map { RecapFigures.TimeIn(it.absenceId, it.minutes, it.subject, it.excused, it.date) },
                grades = gradesList?.map { s -> RecapFigures.GradeSubjectIn(s.subjectName, s.grades.map { it.date to it.markDisplayValue }) },
                homework = homeworkList?.map { it.id to it.dueDate },
                classreg = classregList?.map { it.createDate },
            ),
        )
    }
}
