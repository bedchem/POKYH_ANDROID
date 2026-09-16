package dev.plattnericus.pokyh.data.storage

import dev.plattnericus.pokyh.data.model.AppError
import kotlinx.serialization.KSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A value plus where it came from.
 *
 * [savedAt] is what the "Stand …" label on a screen reads, and it is meaningful for fresh values
 * too — a screen loaded two minutes ago and one restored from last Tuesday look identical
 * otherwise, and the difference is the whole point of showing cached data at all.
 */
data class CachedValue<T>(
    val value: T,
    /** Epoch millis. For [stale] values, when this copy was written; otherwise now. */
    val savedAt: Long,
    /** True when the network failed and this came off the disk. */
    val stale: Boolean,
)

/**
 * Fetch-then-fall-back-to-disk, in one place.
 *
 * Every screen wants the same three things — show the server's answer, keep a copy, and show
 * that copy (with its age) when the server can't be reached — and before this each of them had
 * to build that itself, so none of them did. The result was an app that logged in offline and
 * then showed empty screens.
 *
 * **The copy is written on every success, not on a TTL.** Disk here is a fallback, not a cache
 * to serve from: the network answer always wins when there is one, so there is no staleness
 * window to tune and no risk of showing yesterday's timetable to someone who is online.
 *
 * **A session expiry is never absorbed.** It is not a connectivity problem, and swallowing it
 * would leave the user looking at last week's data with no way to understand why nothing
 * refreshes — the caller has to see it so it can send them to re-authenticate.
 */
@Singleton
class OfflineStore @Inject constructor(
    private val diskCache: DiskCache,
) {

    suspend fun <T> load(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
    ): CachedValue<T> {
        try {
            val fresh = fetch()
            diskCache.write(key, fresh, serializer)
            return CachedValue(fresh, System.currentTimeMillis(), stale = false)
        } catch (e: Throwable) {
            if (e is AppError && e.isSessionExpired) throw e
            val cached = diskCache.read(key, serializer) ?: throw e
            return CachedValue(cached, diskCache.savedAt(key) ?: 0L, stale = true)
        }
    }

    /**
     * What is on disk, without touching the network.
     *
     * For painting a screen from its last known state *before* the request comes back, so a
     * cold start on a slow connection shows the timetable rather than a spinner.
     */
    /** Store a value that arrived some other way than [load] — an SSE push, say — so the offline
     * copy is as current as what was last on screen. */
    suspend fun <T> save(key: String, value: T, serializer: KSerializer<T>) {
        runCatching { diskCache.write(key, value, serializer) }
    }

    suspend fun <T> peek(key: String, serializer: KSerializer<T>): CachedValue<T>? {
        val cached = diskCache.read(key, serializer) ?: return null
        return CachedValue(cached, diskCache.savedAt(key) ?: 0L, stale = true)
    }
}
