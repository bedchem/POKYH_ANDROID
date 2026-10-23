package dev.plattnericus.pokyh.core.util

import java.text.Normalizer

/**
 * Whether a text contains a word the sign-in mascot must not say — the web frontend's
 * `lib/word-filter.ts` with its lists (the JSON files in `lib/word-lists`, all languages at once, since a
 * name can be in any of them). The mascot greets you by the name you type, so whatever is typed
 * would come out of its mouth. Caught also when written with digits or signs ("sh1t", "f*ck",
 * "a.r.s.c.h") or with letters repeated ("fuuuck").
 *
 * Two kinds of words, so ordinary names are not caught: [ANYWHERE] words count inside longer
 * words too; [AS_WORD] words only as a word of their own ("Hancock" stays, "cock" alone does not).
 */
object WordFilter {
    private val ANYWHERE = listOf(
        // de
        "arschloch", "arschgeige", "hurensohn", "huresohn", "fotze", "ficken", "ficker", "wichser", "wixer", "schlampe",
        "missgeburt", "spasti", "schwuchtel", "nutte", "scheisse", "scheiss", "pisser", "vollidiot", "judensau", "kanake",
        "titten", "muschi", "pimmel", "drecksau",
        // en
        "fuck", "bitch", "whore", "asshole", "bastard", "motherf", "retard", "faggot", "nigger", "nigga", "wanker", "penis",
        "vagina", "pussy", "porno", "rapist", "hitler", "heilhitler", "siegheil",
        // it
        "cazzo", "minchia", "stronzo", "stronza", "puttana", "vaffanculo", "fanculo", "coglion", "merda", "frocio",
        "ricchione", "porcodio", "diocane", "mignotta", "zoccola", "bastardo", "bastarda", "pompino",
    )
    private val AS_WORD = listOf(
        "arsch", "fick", "hure", "spast", "mongo", "neger", "kacke",
        "nazi", "porn", "sex", "cunt", "cock", "shit", "fag", "slut", "twat", "rape", "kkk", "piss", "anal", "tits",
        "culo", "figa", "fica", "tette", "troia", "negro",
    )

    // Digits and signs people put in place of letters.
    private val LEET = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b',
        '@' to 'a', '$' to 's', '!' to 'i', '|' to 'i', '€' to 'e',
    )
    private val VOWELS = listOf('a', 'e', 'i', 'o', 'u')
    private val COMBINING = Regex("[\\u0300-\\u036f]")
    private val NOT_LETTER = Regex("[^a-z]")
    private val NOT_LETTERS = Regex("[^a-z]+")
    private val REPEATS = Regex("(.)\\1+")

    private fun normalise(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .replace("ß", "ss")
            .map { LEET[it] ?: it }
            .joinToString("")

    /** Letters only, repeated letters once ("fuuuck" → "fuck"). */
    private fun squash(s: String) = s.replace(NOT_LETTER, "").replace(REPEATS, "$1")

    private fun clean(words: List<String>) = words.map { normalise(it).replace(NOT_LETTER, "") }.filter { it.isNotEmpty() }.distinct()

    private val anywhere = clean(ANYWHERE)
    private val anywhereSquashed = anywhere.map(::squash)
    private val asWord: Set<String> = clean(AS_WORD).flatMap { listOf(it, squash(it)) }.toSet()

    /** Every way the stars of a text can stand for vowels ("f*ck" → "fack", "feck", …). */
    private fun variants(text: String): List<String> {
        val i = text.indexOf('*')
        if (i < 0) return listOf(text)
        return VOWELS.flatMap { v -> variants(text.substring(0, i) + v + text.substring(i + 1)) }
    }

    private fun check(text: String): Boolean {
        val norm = normalise(text)
        val words = norm.split(NOT_LETTERS).filter { it.isNotEmpty() }
        val plain = norm.replace(NOT_LETTER, "")
        val all = squash(norm)
        if (anywhere.indices.any { plain.contains(anywhere[it]) || all.contains(anywhereSquashed[it]) }) return true
        if (words.any { asWord.contains(it) || asWord.contains(squash(it)) }) return true
        // "a.r.s.c.h", "f i c k": single letters spelling a word.
        val spelled = if (words.size > 1 && words.all { it.length == 1 }) words.joinToString("") else ""
        return spelled.isNotEmpty() && (asWord.contains(spelled) || asWord.contains(squash(spelled)) || anywhere.any { spelled.contains(it) })
    }

    /** True when [text] should not be said. */
    fun isOffensive(text: String): Boolean {
        // "f*ck", "sch**sse": each star stands for a vowel (up to three stars).
        val stars = text.count { it == '*' }
        if (stars in 1..3) return variants(text).any(::check)
        return check(if (stars > 0) text.replace("*", "") else text)
    }
}
