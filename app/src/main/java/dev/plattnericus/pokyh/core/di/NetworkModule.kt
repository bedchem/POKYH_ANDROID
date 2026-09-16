package dev.plattnericus.pokyh.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.plattnericus.pokyh.core.status.ServiceHealthInterceptor
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
    fun provideOkHttpClient(healthInterceptor: ServiceHealthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Every call to either host is a reachability observation, and putting that here
            // rather than in the clients means no request can forget to make one. See
            // [ServiceHealthInterceptor].
            .addInterceptor(healthInterceptor)
            .build()
}
