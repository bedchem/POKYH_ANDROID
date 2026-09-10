package dev.plattnericus.pokyh.ui.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.ApiClass
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Port of `ClassView`'s `@State`/`load()` (ClassView.swift). */
@HiltViewModel
class ClassViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
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

    init {
        if (hasBackend) load()
    }

    fun retry() = load()

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
}
