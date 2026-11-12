package dev.plattnericus.pokyh.data.network

import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.model.MenuResponse
import dev.plattnericus.pokyh.data.model.toDomain
import java.net.URI
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendUrlsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://api.example.test"
    private val dishPath = "/dishes/45695d67-8ba5-4787-8d7a-db5f8abf680f/image?v=1794397056101"

    @Test
    fun absoluteHttpsUrlStaysAsItIs() {
        val url = "https://www.example.org/uploads/rezepte/rez-hirten-makkaroni-e-h-1-1.jpg"
        assertEquals(url, BackendUrls.resolve(url, base))
    }

    @Test
    fun absoluteUrlKeepsItsQueryAndCase() {
        assertEquals("http://cdn.example.org/a.jpg?w=200&h=100", BackendUrls.resolve("http://cdn.example.org/a.jpg?w=200&h=100", base))
        // Only the scheme goes to lower case: Coil loads nothing for "HTTPS".
        assertEquals("https://Cdn.Example.org/A.jpg", BackendUrls.resolve("HTTPS://Cdn.Example.org/A.jpg", base))
    }

    @Test
    fun pathGoesBehindTheBackendWithItsQuery() {
        assertEquals("$base$dishPath", BackendUrls.resolve(dishPath, base))
    }

    @Test
    fun pathAgainstBaseWithTrailingSlashHasNoDoubleSlash() {
        assertEquals("$base$dishPath", BackendUrls.resolve(dishPath, "$base/"))
    }

    @Test
    fun pathWithoutLeadingSlashIsStillOnTheBackend() {
        assertEquals("$base/dishes/x/image?v=2", BackendUrls.resolve("dishes/x/image?v=2", base))
    }

    @Test
    fun pathKeepsThePrefixOfABackendBehindAPath() {
        assertEquals("https://host.example.test/api$dishPath", BackendUrls.resolve(dishPath, "https://host.example.test/api"))
    }

    @Test
    fun protocolRelativeTakesTheBackendScheme() {
        assertEquals("https://cdn.example.org/x.jpg?v=3", BackendUrls.resolve("//cdn.example.org/x.jpg?v=3", base))
        assertEquals("http://cdn.example.org/x.jpg", BackendUrls.resolve("//cdn.example.org/x.jpg", "http://localhost:3000"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals("$base$dishPath", BackendUrls.resolve("  $dishPath\n", base))
        assertEquals("https://www.example.org/a.jpg", BackendUrls.resolve(" https://www.example.org/a.jpg ", base))
    }

    @Test
    fun emptyOrBlankIsNull() {
        assertNull(BackendUrls.resolve(null, base))
        assertNull(BackendUrls.resolve("", base))
        assertNull(BackendUrls.resolve("   ", base))
        assertNull(BackendUrls.resolve("\t\n", base))
    }

    @Test
    fun otherSchemesAreDropped() {
        listOf(
            "file:///sdcard/Pictures/dish.jpg",
            "content://media/external/images/media/12",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:image/png;base64,iVBORw0KGgo=",
            "ftp://example.org/dish.jpg",
            "mailto:someone@example.org",
        ).forEach { assertNull(it, BackendUrls.resolve(it, base)) }
    }

    @Test
    fun brokenHttpUrlIsDropped() {
        assertNull(BackendUrls.resolve("https://", base))
    }

    @Test
    fun pathWithoutUsableBaseIsDropped() {
        assertNull(BackendUrls.resolve(dishPath, ""))
        assertNull(BackendUrls.resolve(dishPath, "api.example.test"))
        assertNull(BackendUrls.resolve("//cdn.example.org/x.jpg", ""))
    }

    @Test
    fun resolvingTwiceChangesNothing() {
        listOf(dishPath, "//cdn.example.org/x.jpg", "https://www.example.org/a.jpg").forEach { raw ->
            val once = BackendUrls.resolve(raw, base)
            assertEquals(once, BackendUrls.resolve(once, base))
        }
    }

    @Test
    fun defaultBaseIsTheConfiguredBackend() {
        assertEquals(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + dishPath, BackendUrls.resolve(dishPath))
    }

    @Test
    fun htmlBaseIsTheConfiguredBackendWithSlash() {
        val htmlBase = BackendUrls.htmlBase
        assertEquals(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/", htmlBase)
        assertTrue(htmlBase.startsWith("https://") || htmlBase.startsWith("http://"))
        assertEquals(
            BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/popup-images/0b6c0a64-7a43-4d2e-9a52-1f0e4c6d2a11",
            URI(htmlBase).resolve("/popup-images/0b6c0a64-7a43-4d2e-9a52-1f0e4c6d2a11").toString(),
        )
    }

    // A menu saved on disk before the fix: the raw body with paths, read back through the mapper.
    @Test
    fun cachedMenuPayloadGetsAbsolutePictures() {
        val raw = """
            {"available":true,"menu":{"dishes":[
              {"id":"a","name":"Pasta","imageUrl":"$dishPath","date":"2026-11-11"},
              {"id":"b","name":"Makkaroni","imageUrl":"https://www.example.org/m.jpg","date":"2026-11-11"},
              {"id":"c","name":"Suppe","imageUrl":"","date":"2026-11-11"},
              {"id":"d","name":"Salat","date":"2026-11-11"},
              {"id":"e","name":"Kuchen","imageUrl":"javascript:alert(1)","date":"2026-11-11"}
            ]}}
        """.trimIndent()
        val dishes = json.decodeFromString<MenuResponse>(raw).menu.dishes.map { it.toDomain(base) }
        assertEquals(
            listOf("$base$dishPath", "https://www.example.org/m.jpg", null, null, null),
            dishes.map { it.imageUrl },
        )
    }
}
