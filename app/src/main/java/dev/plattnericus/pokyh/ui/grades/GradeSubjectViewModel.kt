package dev.plattnericus.pokyh.ui.grades

import dev.plattnericus.pokyh.core.util.SchoolDates
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.storage.GradeDraftEntry
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fach-Detail mit Notenrechner + Zielnote-Rechner — port of `GradeSubjectView`'s state.
 *
 * iOS receives the already-loaded `subjects: [SubjectGrades]` array straight from `GradesView`'s
 * `@State`; Compose Navigation gives each destination its own ViewModel instance instead, so this
 * loads the current school year's grades itself (exactly what `GradesView` would have loaded for
 * the subject list it navigated from) and picks out [subjectId]'s entry.
 */
@HiltViewModel
class GradeSubjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val prefsStore: PreferencesStore,
) : ViewModel() {

    val subjectId: Int = savedStateHandle.get<Int>("subjectId") ?: 0

    /** The school year the Noten screen was showing when this subject was opened. Without it
     * this screen always loaded the CURRENT year, so any subject reached from a past year came
     * back empty and the screen said "Fach nicht gefunden". */
    private val year: Int = savedStateHandle.get<Int>("year") ?: SchoolDates.currentSchoolYear

    private val _subject = MutableStateFlow<SubjectGrades?>(null)
    val subject: StateFlow<SubjectGrades?> = _subject.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** `GradeDraft` (GradeMath.swift) for this lesson — persisted via [PreferencesStore]. */
    private val _draft = MutableStateFlow(GradeDraftEntry())
    val draft: StateFlow<GradeDraftEntry> = _draft.asStateFlow()

    init {
        load()
        viewModelScope.launch { _draft.value = prefsStore.gradeDrafts.first()[subjectId] ?: GradeDraftEntry() }
    }

    fun retry() = load()

    private fun load() {
        val session = appState.session.value ?: run { _loading.value = false; return }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val all = untisClient.grades(session, session.studentId, year)
                _subject.value = all.firstOrNull { it.lessonId == subjectId }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _error.value = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Unbekannter Fehler."
            } finally {
                _loading.value = false
            }
        }
    }

    /** `addCustom()` — validated via `GradeMath.parseGradeInput`. */
    fun addCustomGrade(rawInput: String) {
        val value = GradeMath.parseGradeInput(rawInput) ?: return
        persist(_draft.value.copy(customGrades = _draft.value.customGrades + value))
    }

    fun removeCustomGrade(index: Int) {
        val list = _draft.value.customGrades.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        persist(_draft.value.copy(customGrades = list))
    }

    /** Toggles a teacher grade between removed/restored (row's ✕ / ↩ action). */
    fun toggleTeacherGradeRemoved(gradeId: Int) {
        val ids = _draft.value.removedTeacherGradeIds
        val next = if (ids.contains(gradeId)) ids - gradeId else ids + gradeId
        persist(_draft.value.copy(removedTeacherGradeIds = next))
    }

    /** "Rechner zurücksetzen". */
    fun resetDraft() = persist(GradeDraftEntry())

    private fun persist(newDraft: GradeDraftEntry) {
        _draft.value = newDraft
        viewModelScope.launch {
            val all = prefsStore.gradeDrafts.first().toMutableMap()
            if (newDraft.isEmpty) all.remove(subjectId) else all[subjectId] = newDraft
            prefsStore.setGradeDrafts(all)
        }
    }
}
