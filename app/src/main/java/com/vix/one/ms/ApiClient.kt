package com.vix.one.ms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    // Base URL is fetched dynamically from Firebase RTDB
    private const val CONFIG_URL = "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"

    // Secret key is split and reconstructed at runtime to avoid plain-text in APK
    private fun secretKey(): String {
        val parts = arrayOf(
            "xQ9_mL2p",
            "R5tY_7vB",
            "3nM8kL1j",
            "H4gF6dS2a"
        )
        return parts.joinToString("")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedBaseUrl: String? = null

    suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        cachedBaseUrl?.let { return@withContext it }
        val req = Request.Builder().url(CONFIG_URL).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()?.trim()?.trim('"') ?: throw Exception("Не удалось получить base URL")
            if (!body.startsWith("http")) throw Exception("Некорректный base URL: $body")
            cachedBaseUrl = body.trimEnd('/')
            body.trimEnd('/')
        }
    }

    private fun authRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${secretKey()}")
            .header("X-Api-Key", secretKey())
    }

    suspend fun listImages(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val req = authRequest("$base/images").get().build()
            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                if (json.optString("status") != "ok") throw Exception(json.optString("message", "Ошибка списка"))
                val images = json.optJSONObject("images") ?: JSONObject()
                val map = mutableMapOf<String, String>()
                images.keys().forEach { key ->
                    map[key] = images.getString(key)
                }
                map
            }
        }
    }

    suspend fun uploadFile(file: File, fileName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val name = fileName ?: file.name
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    name,
                    file.asRequestBody("application/octet-stream".toMediaType())
                )
                .build()
            val req = authRequest("$base/upload")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val json = JSONObject(text)
                if (!resp.isSuccessful) throw Exception(json.optString("message", "Ошибка загрузки ${resp.code}"))
                // Try to extract id or name
                json.optString("id").ifEmpty {
                    json.optString("name").ifEmpty {
                        json.optString("filename").ifEmpty { name }
                    }
                }
            }
        }
    }

    suspend fun downloadImage(id: String, dest: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val req = authRequest("$base/image/$id").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Ошибка скачивания: ${resp.code}")
                dest.outputStream().use { out ->
                    resp.body?.byteStream()?.copyTo(out)
                }
                dest
            }
        }
    }

    suspend fun dbRead(name: String, path: String = ""): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val url = if (path.isBlank()) "$base/db/$name" else "$base/db/$name/$path"
            val req = authRequest(url).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("DB read error ${resp.code}: $text")
                text
            }
        }
    }

    suspend fun dbWrite(name: String, path: String, value: Any): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val url = "$base/db/$name/$path"
            val bodyStr = when (value) {
                is String -> "\"$value\""
                is Number, is Boolean -> value.toString()
                else -> value.toString()
            }
            val body = bodyStr.toRequestBody("application/json".toMediaType())
            val req = authRequest(url).post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("DB write error ${resp.code}: $text")
                text
            }
        }
    }

    suspend fun dbWriteJson(name: String, path: String, json: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val url = "$base/db/$name/$path"
            val body = json.toRequestBody("application/json".toMediaType())
            val req = authRequest(url).post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("DB write error ${resp.code}: $text")
                text
            }
        }
    }
}
