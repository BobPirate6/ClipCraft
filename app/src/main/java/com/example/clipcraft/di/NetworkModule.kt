package com.example.clipcraft.di

import com.example.clipcraft.config.Config
import com.example.clipcraft.data.remote.ClipCraftApiService
import com.example.clipcraft.data.remote.WhisperApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Interceptor для автоматического повтора неудачных запросов
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 1000
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var exception: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                response?.close() // Закрываем предыдущий ответ если был
                response = chain.proceed(request)
                
                // Если запрос успешен или это клиентская ошибка (4xx), не повторяем
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }
                
                // Для серверных ошибок (5xx) и других проблем - повторяем
                exception = Exception("HTTP ${response.code}: ${response.message}")
                
            } catch (e: Exception) {
                exception = e
            }
            
            // Ждем перед повтором (кроме последней попытки)
            if (attempt < maxRetries - 1) {
                Thread.sleep(retryDelayMillis * (attempt + 1)) // Экспоненциальная задержка
            }
        }
        
        // Если все попытки провалились
        response?.close()
        throw exception ?: Exception("Unknown error after $maxRetries retries")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideRetryInterceptor(): RetryInterceptor {
        return RetryInterceptor(maxRetries = 3, retryDelayMillis = 1000)
    }

    @Provides
    @Singleton
    @Named("MainOkHttpClient")
    fun provideMainOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        retryInterceptor: RetryInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS) // 2 минуты для соединения
            .readTimeout(600, TimeUnit.SECONDS) // 10 минут для чтения - для обработки видео
            .writeTimeout(600, TimeUnit.SECONDS) // 10 минут для записи - для загрузки видео
            .addInterceptor(retryInterceptor) // Добавляем retry до logging
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("WhisperOkHttpClient")
    fun provideWhisperOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        retryInterceptor: RetryInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS) // 2 минуты для соединения
            .readTimeout(1200, TimeUnit.SECONDS) // 20 минут для чтения - для длинных аудио
            .writeTimeout(1200, TimeUnit.SECONDS) // 20 минут для записи - для загрузки аудио
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("MainRetrofit")
    fun provideMainRetrofit(
        @Named("MainOkHttpClient") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Config.MAIN_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("WhisperRetrofit")
    fun provideWhisperRetrofit(
        @Named("WhisperOkHttpClient") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Config.WHISPER_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideClipCraftApiService(@Named("MainRetrofit") retrofit: Retrofit): ClipCraftApiService {
        return retrofit.create(ClipCraftApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWhisperApiService(@Named("WhisperRetrofit") retrofit: Retrofit): WhisperApiService {
        return retrofit.create(WhisperApiService::class.java)
    }
}
