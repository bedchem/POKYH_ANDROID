package dev.plattnericus.pokyh.data.untis

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signing in with the app key of a WebUntis account: the way in for schools that sign their
 * people in with Office 365, who have no WebUntis password (web lib/app-access.ts, backend
 * webuntis/appKey.ts). WebUntis shows every account its key under Profil → Freigaben →
 * "Zugriff über Untis Mobile"; the sign-in sends the one-time code of the moment (TOTP, RFC 6238),
 * as the official app does.
 *
 * A key account keeps "appkey:<KEY>" where a password account keeps its password, so every
 * re-login (fingerprint, silent renewal) takes the same way in.
 */
object AppKey {
    const val PREFIX = "appkey:"

    fun isKey(secret: String) = secret.startsWith(PREFIX)

    /** The key as WebUntis shows it, normalised; "" when it is not one. */
    fun normalise(text: String): String {
        val key = text.uppercase().replace(Regex("[\\s-]"), "").trimEnd('=')
        return if (Regex("^[A-Z2-7]{16,128}$").matches(key)) key else ""
    }

    private val USER_LABEL = Regex("(?:^|\\n)\\s*(?:Benutzer|User|Utente)\\s*:?[ \\t]*(\\S+)", RegexOption.IGNORE_CASE)
    private val KEY_LABEL = Regex("(?:^|\\n)\\s*(?:Schlüssel|Key|Chiave)\\s*:?[ \\t]*([A-Za-z2-7 =-]{16,})", RegexOption.IGNORE_CASE)

    /** User and key out of whatever was pasted: the key alone, WebUntis' table, or the QR code's link. */
    fun parse(text: String): Pair<String?, String?> {
        val pasted = text.trim()
        Regex("untis://setschool\\?(\\S+)", RegexOption.IGNORE_CASE).find(pasted)?.let { m ->
            val params = m.groupValues[1].split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") } }.toMap()
            return params["user"]?.trim()?.ifEmpty { null } to normalise(params["key"].orEmpty()).ifEmpty { null }
        }
        KEY_LABEL.find(pasted)?.let { m ->
            return USER_LABEL.find(pasted)?.groupValues?.get(1) to normalise(m.groupValues[1].lineSequence().first()).ifEmpty { null }
        }
        return null to normalise(pasted).ifEmpty { null }
    }

    private fun base32(text: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var value = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in text.uppercase()) {
            val i = alphabet.indexOf(c)
            if (i < 0) continue
            value = (value shl 5) or i
            bits += 5
            if (bits >= 8) {
                out.write((value ushr (bits - 8)) and 255)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** The 6-digit code of the 30-second step at [nowMs] (HMAC-SHA1). */
    fun totp(key: String, nowMs: Long = System.currentTimeMillis()): String {
        val step = nowMs / 1000 / 30
        val msg = ByteArray(8) { i -> (step ushr (8 * (7 - i))).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(base32(key), "HmacSHA1")) }
        val h = mac.doFinal(msg)
        val o = h.last().toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }
}
