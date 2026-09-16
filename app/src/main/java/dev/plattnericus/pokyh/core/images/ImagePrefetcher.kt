package dev.plattnericus.pokyh.core.images

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warms Coil's disk cache for images a screen the user has not opened yet is going to need.
 *
 * **Why bother:** every one of these images is served over the network on first sight, and the
 * two places they show up — the Mensa strip and a lesson's detail sheet — are exactly the places
 * that get opened on a school Wi-Fi that has just dropped, or on the walk in with no signal at
 * all. Fetching them while Home is on screen and the connection is still good means the picture
 * is already on the disk when it is needed, and it is there offline afterwards.
 *
 * **Deduplicated for the life of the process.** Coil would short-circuit a repeat request on its
 * own, but Home reloads on every tab return and each reload would otherwise enqueue the whole
 * strip again — dozens of no-op requests competing with whatever the visible screen is loading.
 *
 * Failures are silent by construction: nothing is waiting on these, and an image that does not
 * arrive simply falls back to the monogram or placeholder it would have shown anyway.
 */
@Singleton
class ImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val requested = mutableSetOf<String>()

    /**
     * Queue [urls] for download into the image cache.
     *
     * [headers] carries the POKYH API key for backend-served images (subject headers), which the
     * route rejects the request without.
     */
    fun prefetch(urls: List<String>, headers: Pair<String, String>? = null) {
        val fresh = synchronized(requested) {
            urls.filter { it.isNotBlank() && requested.add(it) }
        }
        if (fresh.isEmpty()) return
        val loader: ImageLoader = SingletonImageLoader.get(context)
        fresh.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .apply {
                    if (headers != null) {
                        httpHeaders(NetworkHeaders.Builder().add(headers.first, headers.second).build())
                    }
                }
                .build()
            loader.enqueue(request)
        }
    }

    /** Sign-out: the next account's images are not this one's, and nothing cached should be
     * treated as already requested. */
    fun reset() {
        synchronized(requested) { requested.clear() }
    }
}
