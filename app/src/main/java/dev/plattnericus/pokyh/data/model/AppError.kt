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
    /** The HTTP status when the server answered with an error, so a caller can tell "try again
     * later" (5xx, 401, 429) from "this request is wrong". */
    val httpCode: Int? = null,
) : Exception(message) {
    companion object {
        val sessionExpired = AppError("session_expired", isSessionExpired = true)
        fun noConnection(message: String = "Keine Verbindung") = AppError(message, isNetwork = true)

        /**
         * Signed in offline, and this piece of data was never stored on the device.
         *
         * Thrown instead of answering with an empty list: an empty answer can't be told apart
         * from "the server says there is nothing", which is how an offline session showed
         * "Ferien" in the timetable and "Noch keine Noten" for past years — and it got written
         * over the cached copy that would otherwise have been shown.
         */
        const val OFFLINE_NOT_SAVED = "Du bist offline, und das wurde auf diesem Gerät noch nicht " +
            "gespeichert. Sobald du wieder Internet hast, wird es geladen."
        val offlineNotSaved: AppError get() = AppError(OFFLINE_NOT_SAVED, isNetwork = true)
    }
}
