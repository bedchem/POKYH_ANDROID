package dev.plattnericus.pokyh.state

/**
 * The privacy notice one account still has to confirm (PrivacyNotice.swift `PrivacyNoticePrompt`).
 * [Kind.First] after a first sign-in, [Kind.Updated] for an account already signed in when the
 * notice changed. What the notice says is behind the dialog's link.
 */
data class PrivacyNoticePrompt(
    val kind: Kind,
    val version: String,
    val username: String,
) {
    enum class Kind { First, Updated }
}
