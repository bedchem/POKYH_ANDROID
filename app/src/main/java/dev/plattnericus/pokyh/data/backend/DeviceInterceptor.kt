package dev.plattnericus.pokyh.data.backend

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.network.Config
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the POKYH backend which device a request comes from, so the account's device list
 * (website → Einstellungen → Geräte & Anmeldungen) shows this phone as one entry —
 * "Android 15 (samsung SM-S918B) · POKYH-App 2.1.0" — and can sign it out on its own.
 *
 *  - `User-Agent: POKYH-Android/<version> (Android <release>; <manufacturer> <model>)`
 *  - `X-Pokyh-Device: <random id>`: made once on first use and kept in this app's private
 *    storage; it names nothing about the person or the phone.
 *
 * Only requests to the POKYH backend carry them — WebUntis never sees the device id.
 */
@Singleton
class DeviceInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
) : Interceptor {

    private val backendHost: String? = Config.backendURL.toHttpUrlOrNull()?.host

    private val userAgent: String by lazy {
        val device = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
        "POKYH-Android/${ascii(BuildConfig.VERSION_NAME)} (Android ${ascii(Build.VERSION.RELEASE)}; ${ascii(device)})"
    }

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.takeIf { ID.matches(it) } ?: run {
            val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            prefs.edit().putString(KEY_ID, id).apply()
            id
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (backendHost == null || request.url.host != backendHost) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgent)
                .header("X-Pokyh-Device", deviceId)
                .build(),
        )
    }

    /** Header values must be plain ASCII; a model name with other letters loses only those. */
    private fun ascii(value: String): String =
        value.filter { it.code in 0x20..0x7e && it != '(' && it != ')' && it != ';' }.trim().take(60)

    private companion object {
        const val PREFS = "pokyh_device"
        const val KEY_ID = "device_id"
        val ID = Regex("^[A-Za-z0-9_-]{16,64}$")
    }
}
