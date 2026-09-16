package dev.plattnericus.pokyh.core.status

import dev.plattnericus.pokyh.data.network.Config
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns every request the app makes into a reachability observation for [ServiceHealth].
 *
 * It sits on the single shared `OkHttpClient`, which is the point: the alternative is reporting
 * from each client method, and there are dozens of those across WebUntis and the POKYH backend.
 * One of them forgetting would leave the banner claiming a server is fine while the screen in
 * front of the user is empty.
 *
 * **A 5xx counts as down, a 4xx does not.** "Unreachable" here means *this server cannot serve
 * you right now* — a 500 or a 503 qualifies even though the socket opened, while a 401 or a 404
 * is the server working correctly and saying no, and flagging those would light the banner every
 * time a session expires or a subject has no image.
 */
@Singleton
class ServiceHealthInterceptor @Inject constructor(
    private val health: ServiceHealth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val service = serviceFor(chain.request().url.host)
            ?: return chain.proceed(chain.request())
        try {
            val response = chain.proceed(chain.request())
            if (response.code >= 500) {
                health.reportDown(service, "Server antwortet mit Fehler ${response.code}")
            } else {
                health.reportOk(service)
            }
            return response
        } catch (e: IOException) {
            health.reportDown(service, describe(e))
            throw e
        }
    }

    private fun serviceFor(host: String): PokyhService? = when (host) {
        untisHost -> PokyhService.UNTIS
        backendHost -> PokyhService.BACKEND
        else -> null
    }

    private fun describe(e: IOException): String = when (e) {
        is UnknownHostException -> "Keine Verbindung"
        is SocketTimeoutException -> "Zeitüberschreitung"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Nicht erreichbar"
    }

    private val untisHost: String? by lazy { Config.untisBase.toHttpUrlOrNull()?.host }
    private val backendHost: String? by lazy { Config.backendURL.toHttpUrlOrNull()?.host }
}
