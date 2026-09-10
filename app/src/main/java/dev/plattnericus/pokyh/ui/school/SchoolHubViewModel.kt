package dev.plattnericus.pokyh.ui.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.navigation.AppTab
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SchoolHubView.swift, ported. No network calls of its own — just the `isParent` flag that hides
 * the "Erinnerungen" row (parent accounts have no class reminders) and the tab-switch for "Noten".
 */
@HiltViewModel
class SchoolHubViewModel @Inject constructor(
    private val appState: AppState,
) : ViewModel() {

    data class UiState(val isParent: Boolean = false)

    private val _state = MutableStateFlow(UiState(isParent = appState.session.value?.isParent ?: false))
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appState.session.collect { s -> _state.update { it.copy(isParent = s?.isParent ?: false) } }
        }
    }

    /** "Noten" row — switches the bottom tab instead of pushing a route (HomeViewModel does the same). */
    fun selectGradesTab() = appState.selectTab(AppTab.Grades)
}
