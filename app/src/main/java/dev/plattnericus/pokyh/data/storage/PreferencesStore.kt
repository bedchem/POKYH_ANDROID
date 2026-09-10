package dev.plattnericus.pokyh.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.data.model.SavedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import javax.inject.Inject
import javax.inject.Singleton

/** File-scoped DataStore delegate — one instance per process, matches iOS' `UserDefaults.standard`. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pokyh_prefs")

/**
 * Typisierter Wrapper um Jetpack DataStore — Android-Pendant zu den `UserDefaults`-Zugriffen in
 * iOS' `CredentialStore`/`AppState` (Security.swift und andere). Schlüssel behalten bewusst das
 * `pokyh_`-Präfix der iOS-`UserDefaults`-Keys, damit Semantik/Absicht 1:1 nachvollziehbar bleibt.
 *
 * Enthält NIEMALS Zugangsdaten — Passwörter liegen ausschließlich in [SecureCredentialStore].
 */
@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object Keys {
        val ACCOUNTS = stringPreferencesKey("pokyh_accounts")
        val LAST_ACTIVE = stringPreferencesKey("pokyh_last_active")
        val DEFAULT_ACCOUNT = stringPreferencesKey("pokyh_default_account")
        val THEME = stringPreferencesKey("pokyh_theme")
        val AVATAR_HUES = stringPreferencesKey("pokyh_avatar_hues")
        val GRADE_DRAFTS = stringPreferencesKey("pokyh_grade_drafts")
        val SEEN_MESSAGE_IDS = stringPreferencesKey("pokyh_seen_message_ids")
        val NOTIF_ASKED = booleanPreferencesKey("pokyh_notif_asked")

        const val KEY_PREFIX = "pokyh_"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── Gespeicherte Konten ─────────────────────────────────────────────────

    val accounts: Flow<List<SavedAccount>> = context.dataStore.data.map { prefs ->
        prefs[ACCOUNTS]?.let { decodeOrNull(it, SavedAccountsSerializer) } ?: emptyList()
    }

    suspend fun setAccounts(accounts: List<SavedAccount>) {
        context.dataStore.edit { it[ACCOUNTS] = json.encodeToString(SavedAccountsSerializer, accounts) }
    }

    // ── Zuletzt aktives Konto ───────────────────────────────────────────────

    val lastActive: Flow<String?> = context.dataStore.data.map { it[LAST_ACTIVE] }

    suspend fun setLastActive(username: String?) {
        context.dataStore.edit { prefs ->
            if (username == null) prefs.remove(LAST_ACTIVE) else prefs[LAST_ACTIVE] = username
        }
    }

    // ── Standard-Konto ──────────────────────────────────────────────────────

    val defaultAccount: Flow<String?> = context.dataStore.data.map { it[DEFAULT_ACCOUNT] }

    suspend fun setDefaultAccount(username: String?) {
        context.dataStore.edit { prefs ->
            if (username == null) prefs.remove(DEFAULT_ACCOUNT) else prefs[DEFAULT_ACCOUNT] = username
        }
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    /** "system" | "light" | "dark". */
    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "system" }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[THEME] = value }
    }

    /** Typed convenience over [theme]/[setTheme] for [dev.plattnericus.pokyh.state.AppState]. */
    val themeMode: Flow<PokyhThemeMode> = theme.map {
        when (it) {
            "light" -> PokyhThemeMode.Light
            "dark" -> PokyhThemeMode.Dark
            else -> PokyhThemeMode.System
        }
    }

    suspend fun setThemeMode(mode: PokyhThemeMode) = setTheme(
        when (mode) {
            PokyhThemeMode.Light -> "light"
            PokyhThemeMode.Dark -> "dark"
            PokyhThemeMode.System -> "system"
        },
    )

    // ── Avatar-Farbtöne (Username → Hue 0..1) ──────────────────────────────

    val avatarHues: Flow<Map<String, Double>> = context.dataStore.data.map { prefs ->
        prefs[AVATAR_HUES]?.let { decodeOrNull(it, MapStringDoubleSerializer) } ?: emptyMap()
    }

    suspend fun setAvatarHues(hues: Map<String, Double>) {
        context.dataStore.edit { it[AVATAR_HUES] = json.encodeToString(MapStringDoubleSerializer, hues) }
    }

    // ── Noten-Entwürfe (lessonId → Draft: entfernte Lehrer-Noten + eigene Noten) ──
    // Entspricht `GradeDraftStore` (GradeMath.swift), dort `[Int: GradeDraft]` als JSON in
    // UserDefaults unter demselben Key.

    val gradeDrafts: Flow<Map<Int, GradeDraftEntry>> = context.dataStore.data.map { prefs ->
        prefs[GRADE_DRAFTS]?.let { decodeOrNull(it, MapIntGradeDraftSerializer) } ?: emptyMap()
    }

    suspend fun setGradeDrafts(drafts: Map<Int, GradeDraftEntry>) {
        context.dataStore.edit { it[GRADE_DRAFTS] = json.encodeToString(MapIntGradeDraftSerializer, drafts) }
    }

    // ── Bereits gesehene Nachrichten-IDs ────────────────────────────────────

    val seenMessageIds: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[SEEN_MESSAGE_IDS]?.let { decodeOrNull(it, SetIntSerializer) } ?: emptySet()
    }

    suspend fun setSeenMessageIds(ids: Set<Int>) {
        context.dataStore.edit { it[SEEN_MESSAGE_IDS] = json.encodeToString(SetIntSerializer, ids) }
    }

    // ── Benachrichtigungs-Berechtigung bereits angefragt? ──────────────────

    val notifAsked: Flow<Boolean> = context.dataStore.data.map { it[NOTIF_ASKED] ?: false }

    suspend fun setNotifAsked(value: Boolean) {
        context.dataStore.edit { it[NOTIF_ASKED] = value }
    }

    // ── Alles löschen ────────────────────────────────────────────────────────

    /** Entfernt jeden Preference-Key mit dem `pokyh_`-Präfix — keine hartkodierte Liste. */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(KEY_PREFIX) }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    private fun <T> decodeOrNull(raw: String, serializer: KSerializer<T>): T? = try {
        json.decodeFromString(serializer, raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val SavedAccountsSerializer = ListSerializer(SavedAccount.serializer())
private val MapStringDoubleSerializer = MapSerializer(serializer<String>(), serializer<Double>())
private val SetIntSerializer = SetSerializer(serializer<Int>())

/** Port of `GradeDraft` (GradeMath.swift) — local "what-if" grades per lesson (lessonId), kept
 * out of `data.model` since it's calculator-scratch state, not a WebUntis/backend domain model. */
@kotlinx.serialization.Serializable
data class GradeDraftEntry(
    val removedTeacherGradeIds: List<Int> = emptyList(),
    val customGrades: List<Double> = emptyList(),
) {
    val isEmpty: Boolean get() = removedTeacherGradeIds.isEmpty() && customGrades.isEmpty()
}

private val MapIntGradeDraftSerializer = MapSerializer(serializer<Int>(), GradeDraftEntry.serializer())
