package dev.plattnericus.pokyh.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sicherer Passwortspeicher — Android-Pendant zu iOS' `Keychain` (Security.swift).
 *
 * App-Ebene: das Passwort wird mit AES-256-GCM verschlüsselt. Der Schlüssel selbst lebt
 * ausschließlich im Android Keystore (Hardware-/StrongBox-gestützt wo verfügbar) und
 * verlässt das Gerät nie — nur das Chiffrat wird persistiert.
 *
 * Bewusst KEIN `setUserAuthenticationRequired(true)` am Schlüssel: das würde einen
 * Geräte-Passcode/Biometrie an jede Entschlüsselung binden und ist kontext-/reuse-abhängig
 * (bricht Kontowechsel, erzwingt System-Prompts an unerwarteten Stellen). Biometrisches
 * Gating passiert stattdessen app-seitig auf Navigations-Ebene via [dev.plattnericus.pokyh.
 * core.biometric.BiometricAuthenticator], exakt spiegelnd wie iOS' `Biometric.authenticate`
 * vor jedem Lesen in `AppState` — nicht durch eine `SecAccessControl`-artige Bindung am
 * Schlüssel-Objekt selbst.
 *
 * Ein WebUntis-Passwort muss zum Re-Login im Klartext vorliegen und kann daher nicht als
 * Einweg-Hash gespeichert werden.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "pokyh_master_key"
        private const val PREFS_NAME = "pokyh_secure_creds"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Öffentliche API ─────────────────────────────────────────────────────

    suspend fun saveCredentials(username: String, password: String) = withContext(Dispatchers.IO) {
        val key = normalize(username)
        val encrypted = encrypt(password.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
        saveOfflineVerifier(key, password)
    }

    // ── Offline-Passwortprüfung ─────────────────────────────────────────────

    /**
     * Ein Einweg-Hash (PBKDF2, gesalzen) des zuletzt erfolgreich verwendeten Passworts — nur, um
     * offline prüfen zu können, ob das eingetippte Passwort stimmt.
     *
     * **Getrennt vom Passwort, weil „Abmelden" das Passwort löscht.** Ohne diesen Hash hatte ein
     * abgemeldetes Konto offline nichts mehr, womit die Eingabe verglichen werden konnte, und die
     * Anmeldung wurde abgelehnt, obwohl der Stundenplan noch auf dem Gerät lag. Er verschwindet
     * erst mit dem Konto selbst ([deleteOfflineVerifier]) oder mit [deleteAll].
     */
    private val verifierPrefs = context.getSharedPreferences("pokyh_offline_verifiers", Context.MODE_PRIVATE)

    private fun saveOfflineVerifier(key: String, password: String) {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        verifierPrefs.edit()
            .putString(key, Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    /** Stimmt [password] mit dem zuletzt online bestätigten Passwort für [username] überein? */
    suspend fun verifyOfflinePassword(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val stored = verifierPrefs.getString(normalize(username), null) ?: return@withContext false
        val parts = stored.split(":")
        if (parts.size != 2) return@withContext false
        try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val expected = Base64.decode(parts[1], Base64.NO_WRAP)
            java.security.MessageDigest.isEqual(expected, pbkdf2(password, salt))
        } catch (_: Exception) {
            false
        }
    }

    /** Für eine Anmeldung, die das Passwort nicht neu speichert (stiller Re-Login): ein Konto aus
     * der Zeit vor dem Hash bekommt ihn nachträglich — damit es auch nach dem Abmelden offline aufgeht. */
    suspend fun ensureOfflineVerifier(username: String, password: String) = withContext(Dispatchers.IO) {
        val key = normalize(username)
        if (!verifierPrefs.contains(key)) saveOfflineVerifier(key, password)
    }

    suspend fun deleteOfflineVerifier(username: String) = withContext(Dispatchers.IO) {
        verifierPrefs.edit().remove(normalize(username)).apply()
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Entschlüsseltes Passwort für `username`, als (normalisierter Benutzername, Passwort). */
    suspend fun getCredentials(username: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val key = normalize(username)
        val stored = prefs.getString(key, null) ?: return@withContext null
        val combined = try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return@withContext null
        }
        val plain = try {
            decrypt(combined)
        } catch (_: Exception) {
            return@withContext null
        } ?: return@withContext null
        key to String(plain, Charsets.UTF_8)
    }

    /** Fast synchronous existence check (no decryption) — for UI badges like ProfileScreen's
     * "Passwort nötig" that need an answer without a suspend round-trip. Plain SharedPreferences
     * reads are memory-resident after first load, so this is safe to call from composition. */
    fun hasCredentialsSync(username: String): Boolean = prefs.contains(normalize(username))

    suspend fun deleteCredentials(username: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(normalize(username)).apply()
    }

    /** Entfernt ALLE gespeicherten Zugangsdaten (alle Konten). Der Master-Key bleibt bestehen. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        verifierPrefs.edit().clear().apply()
    }

    // ── Verschlüsselung ─────────────────────────────────────────────────────

    private fun normalize(username: String) = username.trim().lowercase()

    /** Rückgabe = iv (12 Byte) || ciphertext || tag. */
    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decrypt(combined: ByteArray): ByteArray? {
        if (combined.size <= GCM_IV_LENGTH) return null
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .setKeySize(256)
            .build()

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(spec)
        return generator.generateKey()
    }
}
