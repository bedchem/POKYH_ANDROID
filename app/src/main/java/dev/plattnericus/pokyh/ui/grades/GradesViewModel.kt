package dev.plattnericus.pokyh.ui.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Port of `GradesView`'s state/loading (SwiftUI `@State` + `.task(id: year)`). */
@HiltViewModel
class GradesViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
) : ViewModel() {

    enum class SortMode(val label: String) {
        NAME("Name"),
        AVG_DESC("Schnitt ↓"),
        AVG_ASC("Schnitt ↑"),
        RECENT("Letzte Note"),
    }

    /** Available school years for the toolbar picker, newest first — `SchoolDates.availableYears`. */
    val availableYears: List<Int> = SchoolDates.availableYears

    private val _year = MutableStateFlow(SchoolDates.currentSchoolYear)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _sort = MutableStateFlow(SortMode.NAME)
    val sort: StateFlow<SortMode> = _sort.asStateFlow()

    private val _subjects = MutableStateFlow<List<SubjectGrades>>(emptyList())
    val subjects: StateFlow<List<SubjectGrades>> = _subjects.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    /** Menu selection ("Button(\"\(y)/...\") { year = y; Task { await load() } }"). */
    fun selectYear(y: Int) {
        if (y == _year.value) return
        _year.value = y
        load()
    }

    fun selectSort(mode: SortMode) {
        _sort.value = mode
    }

    fun retry() = load()

    private fun load() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // `year == currentSchoolYear ? nil : year` — the client resolves `null` to the
                // WebUntis "current" schoolyear itself, exactly like the iOS call site.
                val requestedYear = if (_year.value == SchoolDates.currentSchoolYear) null else _year.value
                _subjects.value = untisClient.grades(session, session.studentId, requestedYear)
            } catch (e: AppError) {
                _error.value = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Unbekannter Fehler."
            } finally {
                _loading.value = false
            }
        }
    }
}
