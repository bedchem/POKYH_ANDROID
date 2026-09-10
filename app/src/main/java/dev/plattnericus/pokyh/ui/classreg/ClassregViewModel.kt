package dev.plattnericus.pokyh.ui.classreg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Port of `ClassregEventsView`'s `@State`/`load()` (ClassregEventsView.swift). */
@HiltViewModel
class ClassregViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
) : ViewModel() {

    /** Available school years for the picker menu, newest first — `SchoolDates.availableYears`. */
    val availableYears: List<Int> = SchoolDates.availableYears

    private val _year = MutableStateFlow(SchoolDates.currentSchoolYear)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _events = MutableStateFlow<List<ClassregEvent>>(emptyList())
    val events: StateFlow<List<ClassregEvent>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    /** Menu selection ("Button(\"\(y)/\(...)\") { year = y; Task { await load() } }"). */
    fun selectYear(y: Int) {
        if (y == _year.value) return
        _year.value = y
        load()
    }

    fun retry() = load()

    private fun load() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _events.value = untisClient.classregEvents(session, session.studentId, _year.value)
            } catch (e: AppError) {
                if (e.isSessionExpired) {
                    // Same handling as ClassregEventsView.swift's `app.handleSessionExpired()`.
                    appState.handleSessionExpired()
                    _error.value = "Sitzung abgelaufen. Bitte erneut anmelden."
                } else {
                    _error.value = e.message
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unbekannter Fehler."
            } finally {
                _loading.value = false
            }
        }
    }
}
