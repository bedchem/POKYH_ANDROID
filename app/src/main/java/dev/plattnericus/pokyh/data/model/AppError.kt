package dev.plattnericus.pokyh.data.model

/** Mirrors `Models.swift`'s `AppError` — a typed exception carrying a backend/domain message. */
class AppError(
    message: String,
    val isSessionExpired: Boolean = false,
    /**
     * This started life as an [java.io.IOException] — the network was unreachable, not the
     * request wrong.
     *
     * **Losing that fact is what broke offline sign-in.** The clients translate connection
     * failures into an `AppError` with a readable German message, and every caller that wanted
     * to know "was this the network?" was testing `is IOException`, which by then was false. So
     * `AppState.performLogin` treated an unreachable WebUntis as a credentials-style failure,
     * refused to fall back to the stored session, and left a saved account unable to sign in
     * offline — while showing it the words "Keine Verbindung".
     */
    val isNetwork: Boolean = false,
) : Exception(message) {
    companion object {
        val sessionExpired = AppError("session_expired", isSessionExpired = true)
        fun noConnection(message: String = "Keine Verbindung") = AppError(message, isNetwork = true)
    }
}
