package com.example.m3u8player.di

import com.example.m3u8player.data.api.OkzywApi
import com.example.m3u8player.data.api.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val OKZYW_BASE_URL = "https://api.okzyw.net/"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class OkzywRetrofit

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class TmdbRetrofit
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    @OkzywRetrofit
    fun provideOkzywRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OKZYW_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOkzywApi(@OkzywRetrofit retrofit: Retrofit): OkzywApi {
        return retrofit.create(OkzywApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTmdbApi(@TmdbRetrofit retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }
}
