package dev.plattnericus.pokyh.core.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Dünner Wrapper um `androidx.biometric.BiometricPrompt` — Android-Pendant zu iOS' `Biometric`
 * enum (Security.swift), das `LAContext`/Face ID/Touch ID kapselt.
 *
 * Erlaubt sowohl starke Biometrie als auch Geräte-Code (PIN/Muster/Passwort) als Fallback,
 * analog zu iOS' `.deviceOwnerAuthentication`-Policy.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {
    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        prompt.authenticate(promptInfo)
    }
}
