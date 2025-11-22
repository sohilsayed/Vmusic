// File: java/com/example/holodex/di/NetworkModule.kt

package com.example.holodex.di

import android.content.SharedPreferences
import com.example.holodex.auth.TokenManager
import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.api.MusicdexApiService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Timber.tag("OkHttp-Holodex").i(message)
        }.apply { level = HttpLoggingInterceptor.Level.BODY }
    }

    // CLIENT FOR HOLODEX.NET API (GET requests, etc.)
    @Provides
    @Singleton
    @HolodexHttpClient
    fun provideHolodexOkHttpClient(
        sharedPreferences: SharedPreferences,
        tokenManager: TokenManager,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                requestBuilder.header("User-Agent", BROWSER_USER_AGENT)
                val apiKey = sharedPreferences.getString("API_KEY", "") ?: ""
                if (apiKey.isNotEmpty()) {
                    requestBuilder.header("X-APIKEY", apiKey)
                }
                // Also add JWT if available, for endpoints like GET /users/favorites
                tokenManager.getJwt()?.let { jwt ->
                    requestBuilder.header("Authorization", "Bearer $jwt")
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // CLIENT FOR PUBLIC MUSICDEX.NET API (API Key only)
    @Provides
    @Singleton
    @MusicdexHttpClient
    fun provideMusicdexOkHttpClient(
        sharedPreferences: SharedPreferences,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                requestBuilder.header("User-Agent", BROWSER_USER_AGENT)
                val apiKey = sharedPreferences.getString("API_KEY", "") ?: ""
                if (apiKey.isNotEmpty()) {
                    requestBuilder.header("X-APIKEY", apiKey)
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // CLIENT FOR AUTHENTICATED MUSICDEX.NET API (Likes, History, Playlists, AND NOW FAVORITES)
    // --- FIX: This client now sends both the API Key and the JWT ---
    // This makes it compatible with the favorites PATCH endpoint without breaking the likes endpoint.
    @Provides
    @Singleton
    @AuthenticatedMusicdexHttpClient
    fun provideAuthenticatedMusicdexOkHttpClient(
        sharedPreferences: SharedPreferences, // <-- ADDED
        tokenManager: TokenManager,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                requestBuilder.header("User-Agent", BROWSER_USER_AGENT)

                // Add API Key
                val apiKey = sharedPreferences.getString("API_KEY", "") ?: ""
                if (apiKey.isNotEmpty()) {
                    requestBuilder.header("X-APIKEY", apiKey)
                }

                // Add JWT
                tokenManager.getJwt()?.let { jwt ->
                    requestBuilder.header("Authorization", "Bearer $jwt")
                }
                requestBuilder.header("Referer", "https://music.holodex.net/")

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // --- RETROFIT SERVICE PROVIDERS (unchanged) ---

    @Provides
    @Singleton
    fun provideHolodexApiService(@HolodexHttpClient okHttpClient: OkHttpClient): HolodexApiService {
        return Retrofit.Builder()
            .baseUrl("https://holodex.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HolodexApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMusicdexApiService(@MusicdexHttpClient okHttpClient: OkHttpClient, gson: Gson): MusicdexApiService {
        return Retrofit.Builder()
            .baseUrl("https://music.holodex.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MusicdexApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthenticatedMusicdexApiService(@AuthenticatedMusicdexHttpClient okHttpClient: OkHttpClient, gson: Gson): AuthenticatedMusicdexApiService {
        return Retrofit.Builder()
            .baseUrl("https://music.holodex.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthenticatedMusicdexApiService::class.java)
    }
}