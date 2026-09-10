package dev.plattnericus.pokyh.data.backend

import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.model.ApiComment
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.data.model.DishRatingsResponse
import dev.plattnericus.pokyh.data.network.Config
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live-Updates via Server-Sent Events (POKYH-Backend, kein iOS-Pendant — reines
 * Android-Feature). Baut pro Aufruf eine eigene [EventSource]-Verbindung auf; kein
 * eigenes Reconnect/Backoff jenseits dessen, was OkHttp's `EventSource` bereits mitbringt
 * — bei endgültigem Fehler wird der Flow geschlossen, Aufrufer starten durch erneutes
 * Sammeln (`collect`) neu.
 */
@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val factory = EventSources.createFactory(okHttpClient)

    fun sseTodos(token: String): Flow<List<ApiTodo>> =
        connect(Config.Routes.sseTodos, token) { json.decodeFromString(it) }

    fun sseReminders(classId: String, token: String): Flow<List<ApiReminder>> =
        connect(Config.Routes.sseReminders(classId), token) { json.decodeFromString(it) }

    fun sseReminderComments(reminderId: String, token: String): Flow<List<ApiComment>> =
        connect(Config.Routes.sseReminderComments(reminderId), token) { json.decodeFromString(it) }

    fun sseDishRatings(dishId: String, token: String): Flow<DishRatingsResponse> =
        connect(Config.Routes.sseDishRatings(dishId), token) { json.decodeFromString(it) }

    fun sseDishComments(dishId: String, token: String): Flow<List<ApiComment>> =
        connect(Config.Routes.sseDishComments(dishId), token) { json.decodeFromString(it) }

    /**
     * Baut eine [EventSource]-Verbindung zu `Config.backendURL + path` auf. Header werden bei
     * SSE-Verbindungen nicht zuverlässig übertragen, daher `apiKey`/`token` als Query-Parameter.
     * Jedes benannte Event (`event: <name>`) wird mit [decode] geparst und emittiert; unbenannte
     * Events (Heartbeat-Payload `{"type":"heartbeat"}`, ohne `event:`-Zeile) werden ignoriert.
     */
    private fun <T> connect(path: String, token: String, decode: (String) -> T): Flow<T> = callbackFlow {
        val url = (Config.backendURL + path).toHttpUrl().newBuilder()
            .addQueryParameter("apiKey", BuildConfig.BACKEND_API_KEY)
            .addQueryParameter("token", token)
            .build()
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == null) return // unbenannt -> Heartbeat, ignorieren
                val value = runCatching { decode(data) }.getOrNull() ?: return
                trySend(value)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }
        val eventSource = factory.newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }
}
