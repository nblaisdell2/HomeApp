package com.nblaisdell.home.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nblaisdell.home.BuildConfig
import com.nblaisdell.home.data.remote.AuthInterceptor
import com.nblaisdell.home.data.remote.generated.api.HealthApi
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import com.nblaisdell.home.data.remote.generated.infrastructure.Serializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client for the long-lived Server-Sent Events stream. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SseClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBaseUrl(): HttpUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    /** The generated client's JSON setup (registers the UUID adapter, ignores unknown keys). */
    @Provides
    @Singleton
    fun provideJson(): Json = Serializer.kotlinxSerializationJson

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC logs method, URL, status and timing only, never headers (the API key).
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @SseClient
    fun provideSseClient(client: OkHttpClient): OkHttpClient =
        // The server sends a heartbeat every 15s. Networks (and the emulator) can silently drop
        // a long-lived connection; with no data for 45s the read times out and LiveUpdates
        // reconnects, pulling anything missed in between.
        client.newBuilder().readTimeout(45, TimeUnit.SECONDS).build()

    @Provides
    @Singleton
    fun provideRetrofit(baseUrl: HttpUrl, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideShoppingApi(retrofit: Retrofit): ShoppingApi = retrofit.create(ShoppingApi::class.java)

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
