package com.yenaly.han1meviewer.logic.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:35
 */
object ServiceCreator {

    // 怪不得無法更新呢！
    val json = Json {
        ignoreUnknownKeys = true
    }

    val cache = Cache(
        directory = File(applicationContext.cacheDir, "http_cache"),
        maxSize = 10 * 1024 * 1024
    )

    inline fun <reified T> create(baseUrl: String): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .build()
        .create(T::class.java)

    inline fun <reified T> createVersion(): T = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(T::class.java)

    /**
     * OkHttpClient
     */
    var okHttpClient: OkHttpClient = buildOkHttpClient()
        private set

    /**
     * Rebuild OkHttpClient
     */
    fun rebuildOkHttpClient() {
        okHttpClient = buildOkHttpClient()
    }

    /**
     * Build OkHttpClient
     */
    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request =
                    chain.request().newBuilder().addHeader("User-Agent", USER_AGENT).build()
                return@addInterceptor chain.proceed(request)
            }
            .cache(cache)
            .cookieJar(HCookieJar())
            .proxySelector(HProxySelector())
            .dns(HDns())
            .build()
    }

    /**
     * Suspend extension that allows suspend [Call] inside coroutine.
     */
    suspend fun Call.await(): okhttp3.Response {
        return suspendCancellableCoroutine { continuation ->
            enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    continuation.resume(response)
                }
            })
            continuation.invokeOnCancellation { cancel() }
        }
    }
}