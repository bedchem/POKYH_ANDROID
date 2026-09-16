package dev.plattnericus.pokyh.data.model

/**
 * Mirrors the iOS `AppState.backendStatus` enum — drives [dev.plattnericus.pokyh.ui.components.
 * BackendUnavailableView]. `ok` means the POKYH backend account (todos/reminders/class) is
 * usable; the other cases explain exactly why a screen can't show backend-only data.
 */
sealed interface BackendStatus {
    data object Unknown : BackendStatus
    data object Ok : BackendStatus
    /** Teacher/admin WebUntis account — the backend deliberately creates no POKYH account. */
    data object NotStudent : BackendStatus
    /** WebUntis returned no usable klasseId and the timetable-derived fallback found nothing. */
    data object NoClass : BackendStatus
    /** Signed in offline from the stored session — nothing is wrong with the account, the
     * POKYH server just cannot be asked right now. Screens show what is stored on the device. */
    data object Offline : BackendStatus
    data class Failed(val message: String) : BackendStatus
}
