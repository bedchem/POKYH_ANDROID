package dev.plattnericus.pokyh.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network-Layer DI. [dev.plattnericus.pokyh.data.backend.BackendClient],
 * [dev.plattnericus.pokyh.data.backend.SseClient] and the WebUntis client are
 * constructor-injected directly (`@Inject constructor`) — this module only supplies the
 * single shared [OkHttpClient] all of them are built on, matching `BackendClient.swift`'s
 * one ephemeral `URLSession` with a 20s request timeout.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(passthroughInterceptor)
        .build()

    // Reserved hook for future cross-cutting concerns (auth-refresh retry, etc.) — a
    // deliberate no-op for now. No logging interceptor in release builds.
    private val passthroughInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
}
