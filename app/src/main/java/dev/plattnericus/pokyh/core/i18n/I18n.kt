package dev.plattnericus.pokyh.core.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's languages — the web frontend's active locales (de, it, en). */
enum class Lang(val code: String, val nativeName: String, val locale: Locale) {
    De("de", "Deutsch", Locale.GERMAN),
    It("it", "Italiano", Locale.ITALIAN),
    En("en", "English", Locale.ENGLISH),
}

/**
 * The language the app speaks. By default it follows the phone (German when the phone speaks none
 * of the three); a choice in the settings overrides that and is kept on the device.
 *
 * Compose reads the texts through [LocalStrings] (see [strings]), so a change of language redraws
 * every screen at once. Code outside composition — view models, notifications, widgets — reads
 * [s]; text it already produced stays in the old language until it is produced again.
 */
object I18n {
    private const val PREFS = "pokyh_i18n"
    private const val KEY = "pokyh_language"

    private val _choice = MutableStateFlow<Lang?>(null)

    /** The language chosen in the settings, or null to follow the phone. */
    val choice: StateFlow<Lang?> = _choice.asStateFlow()

    private val _lang = MutableStateFlow(systemLang())

    /** The language in use. */
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    /** The texts in the language in use, for code outside composition. */
    val s: Strings get() = stringsFor(_lang.value)

    /** Reads the stored choice; called once when the app starts. */
    fun init(context: Context) {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _choice.value = Lang.entries.firstOrNull { it.code == stored }
        refresh()
    }

    /** A choice in the settings: a language, or null for the phone's. */
    fun choose(context: Context, lang: Lang?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lang == null) remove(KEY) else putString(KEY, lang.code)
        }.apply()
        _choice.value = lang
        refresh()
    }

    /** The phone's language changed (or the app's own one in the system settings). */
    fun refresh() {
        _lang.value = _choice.value ?: systemLang()
    }

    private fun systemLang(): Lang = when (Locale.getDefault().language) {
        "it" -> Lang.It
        "en" -> Lang.En
        else -> Lang.De
    }
}

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.De -> StringsDe
    Lang.It -> StringsIt
    Lang.En -> StringsEn
}

val LocalStrings = staticCompositionLocalOf { StringsDe }

/** The texts in the language in use. */
@Composable
@ReadOnlyComposable
fun strings(): Strings = LocalStrings.current
