package dev.plattnericus.pokyh.data.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Picture addresses as the backend sends them, made loadable.
 *
 * Without a PUBLIC_BASE_URL the backend names the pictures it keeps itself by path only
 * ("/dishes/<id>/image?v=…"), so a path goes behind [Config.backendURL], the same way every
 * request URL is built. Links to other sites stay as they are. Whatever is not http(s) after
 * that is dropped, so a file:, content: or javascript: address never reaches the image loader.
 */
object BackendUrls {

    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /** The backend address with a trailing slash: the base for HTML the backend renders, so
     * `<img src="/popup-images/…">` in it loads from the backend. */
    val htmlBase: String get() = Config.backendURL.trim().trimEnd('/') + "/"

    /** [raw] as an absolute http(s) URL on [Config.backendURL], or null when there is none. */
    fun resolve(raw: String?): String? = resolve(raw, Config.backendURL)

    fun resolve(raw: String?, base: String): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("//")) {
            val baseScheme = base.trim().toHttpUrlOrNull()?.scheme ?: return null
            return "$baseScheme:$value".toHttpUrlOrNull()?.toString()
        }
        scheme.find(value)?.let { found ->
            // Only checked, not rewritten: another site's link loads as it was sent, its scheme
            // in lower case (Coil picks its network fetcher only for "http" and "https").
            val link = found.value.lowercase() + value.substring(found.value.length)
            return link.takeIf { it.toHttpUrlOrNull() != null }
        }
        val joined = base.trim().trimEnd('/') + "/" + value.removePrefix("/")
        return joined.toHttpUrlOrNull()?.toString()
    }
}
