package dev.plattnericus.pokyh.data.model

/** Mirrors `Models.swift`'s `AppError` — a typed exception carrying a backend/domain message. */
class AppError(
    message: String,
    val isSessionExpired: Boolean = false,
) : Exception(message) {
    companion object {
        val sessionExpired = AppError("session_expired", isSessionExpired = true)
    }
}
