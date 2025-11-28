package com.example.android.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.example.android.BuildConfig
import com.example.android.data.api.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 의존성 주입 모듈
 * - Retrofit 클라이언트 설정
 * - OkHttp 인터셉터 설정
 * - API 서비스 제공
 */

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Gson 인스턴스 제공
     * - 날짜 포맷 설정
     * - Null 값 직렬화 설정
     */
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .setLenient()
        .create()

    /**
     * OkHttpClient 제공
     * - 로깅 인터셉터 추가 (디버그 모드에서만)
     * - 타임아웃 설정
     * - 공통 헤더 추가 인터셉터
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // 디버그 모드에서만 로깅 인터셉터 추가
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        // 공통 헤더 추가 인터셉터
        builder.addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }

        return builder.build()
    }

    /**
     * Retrofit 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    /**
     * StockLabApiService 제공
     */
    @Provides
    @Singleton
    fun provideStockLabApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}

/**
 * Repository 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Repository 인스턴스는 나중에 추가
}