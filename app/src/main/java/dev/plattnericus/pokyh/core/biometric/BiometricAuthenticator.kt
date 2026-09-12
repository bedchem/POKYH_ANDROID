package dev.plattnericus.pokyh.core.biometric

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Was der Prompt akzeptieren darf.
 *
 * Der Geräte-Code (PIN/Muster/Passwort) darf erst ab API 30 mit Biometrie kombiniert werden —
 * auf 28/29 lehnt `PromptInfo` die Kombination ab, darunter gibt es ihn gar nicht. Ältere Geräte
 * bekommen deshalb nur Biometrie (plus eigenen Abbrechen-Button, siehe unten).
 *
 * `BIOMETRIC_WEAK` statt `STRONG`, weil hier kein Schlüssel an die Authentifizierung gebunden ist
 * (siehe [dev.plattnericus.pokyh.data.storage.SecureCredentialStore]): der Prompt ist ein
 * App-Gate, kein Krypto-Gate — und Gesichtsentsperrung gilt auf vielen Geräten als „weak".
 */
private val allowedAuthenticators: Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

/** Ob das Gerät den Entsperr-Prompt überhaupt anzeigen kann — eine Definition für UI und
 * ViewModel, damit nie die eine Stelle prüft, was die andere gar nicht anfordert. */
fun biometricUnlockAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Dünner Wrapper um `androidx.biometric.BiometricPrompt` — Android-Pendant zu iOS' `Biometric`
 * enum (Security.swift), das `LAContext`/Face ID/Touch ID kapselt.
 *
 * Erlaubt sowohl Biometrie als auch Geräte-Code (PIN/Muster/Passwort) als Fallback, analog zu
 * iOS' `.deviceOwnerAuthentication`-Policy — soweit die API-Version das hergibt.
 *
 * Braucht zwingend eine [FragmentActivity]: `BiometricPrompt` hängt sich als Fragment in deren
 * FragmentManager. Genau daran scheiterte der Fingerabdruck früher lautlos.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean = biometricUnlockAvailable(activity)

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
            .apply {
                // Ohne Geräte-Code-Fallback verlangt PromptInfo einen eigenen Abbrechen-Button —
                // sonst wirft `build()`.
                if (allowedAuthenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Abbrechen")
            }
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
