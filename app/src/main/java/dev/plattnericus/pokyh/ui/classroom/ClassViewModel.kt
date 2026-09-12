package dev.plattnericus.pokyh.ui.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.util.toYyyyMMdd
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.ApiClass
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * Klasse. Two independent halves, deliberately kept separate so one failing doesn't blank the
 * other:
 *
 *  - the POKYH backend's class record ([klass]) — name, join code, member list;
 *  - a WebUntis **overview** ([overview]) mirroring the web frontend's class page — recent
 *    class-register entries, still-open absences, and this/next week's exams.
 *
 * The overview's three WebUntis calls run concurrently and each swallow their own failure: a
 * locked `classregevents` permission shouldn't cost you the exam list.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ClassViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val untisClient: UntisClient,
) : ViewModel() {

    /** `var hasBackend: Bool { app.session?.apiToken != nil }`. */
    val hasBackend: Boolean get() = appState.session.value?.apiToken != null
    val backendStatus = appState.backendStatus

    private val _klass = MutableStateFlow<ApiClass?>(null)
    val klass: StateFlow<ApiClass?> = _klass.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _overview = MutableStateFlow(ClassOverview())
    val overview: StateFlow<ClassOverview> = _overview.asStateFlow()

    val klasseName: String get() = appState.session.value?.klasseName.orEmpty()
    val schoolYear: Int get() = SchoolDates.currentSchoolYear

    init {
        if (hasBackend) load()
        loadOverview()
    }

    fun retry() {
        load()
        loadOverview()
    }

    private fun load() {
        val token = appState.session.value?.apiToken ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _klass.value = backendClient.myClass(token)
            } catch (e: AppError) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Unbekannter Fehler."
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * The WebUntis half. Runs whether or not the POKYH backend is reachable — these come from
     * WebUntis, so they're available even for an account with no POKYH class.
     */
    private fun loadOverview() {
        val session = appState.session.value ?: return
        if (!session.hasUntis) return
        val studentId = session.studentId

        viewModelScope.launch {
            _overview.value = _overview.value.copy(loading = true)
            val today = todayLocalDate()
            val thisMonday = mondayOfWeek(today)
            val nextMonday = thisMonday.plus(7, DateTimeUnit.DAY)
            val weekAfter = thisMonday.plus(14, DateTimeUnit.DAY)
            // "Letzte 3 Monate", as a YYYYMMDD threshold.
            val threeMonthsAgo = today.plus(-3, DateTimeUnit.MONTH).toYyyyMMdd()

            val result = coroutineScope {
                val eventsJob = async {
                    runCatching { untisClient.classregEvents(session, studentId, schoolYear) }.getOrDefault(emptyList())
                }
                val absencesJob = async {
                    runCatching {
                        untisClient.absences(
                            session,
                            studentId,
                            SchoolDates.yearStart(schoolYear).toString(),
                            SchoolDates.yearEnd(schoolYear).toString(),
                        )
                    }.getOrDefault(emptyList())
                }
                val thisWeekJob = async {
                    runCatching { untisClient.timetable(session, studentId, thisMonday.toString()) }
                        .getOrDefault(emptyList())
                }
                val nextWeekJob = async {
                    runCatching { untisClient.timetable(session, studentId, nextMonday.toString()) }
                        .getOrDefault(emptyList())
                }

                val events = eventsJob.await()
                    .filter { it.createDate >= threeMonthsAgo }
                    .sortedByDescending { it.createDate }
                    .take(5)

                val openAbsences = absencesJob.await()
                    .filter { !it.isExcused }
                    .sortedByDescending { it.startDate }

                fun examsIn(entries: List<TimetableEntry>, fromInclusive: Int, toExclusive: Int) = entries
                    .filter { it.isExam && it.date >= fromInclusive && it.date < toExclusive }
                    .distinctBy { it.date to it.startTime to it.subjectName }
                    .sortedWith(compareBy({ it.date }, { it.startTime }))

                ClassOverview(
                    loading = false,
                    recentEvents = events,
                    openAbsences = openAbsences,
                    examsThisWeek = examsIn(thisWeekJob.await(), thisMonday.toYyyyMMdd(), nextMonday.toYyyyMMdd()),
                    examsNextWeek = examsIn(nextWeekJob.await(), nextMonday.toYyyyMMdd(), weekAfter.toYyyyMMdd()),
                )
            }
            _overview.value = result
        }
    }
}

/**
 * The WebUntis-sourced part of the Klasse screen.
 *
 * Klassendienste and Hausaufgaben are on the screen as sections but have no fields here: this
 * app's [UntisClient] has no endpoint for either yet (the web frontend reads WebUntis'
 * `classreg/duties` and `homework` routes, which aren't ported). Their sections therefore always
 * render their empty state — deliberately present so the screen matches the web's shape, and so
 * wiring them up later is a data change rather than a layout change.
 */
data class ClassOverview(
    val loading: Boolean = false,
    val recentEvents: List<ClassregEvent> = emptyList(),
    val openAbsences: List<AbsenceEntry> = emptyList(),
    val examsThisWeek: List<TimetableEntry> = emptyList(),
    val examsNextWeek: List<TimetableEntry> = emptyList(),
)
