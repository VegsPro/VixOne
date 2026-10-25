package com.vix.one.ms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val CONFIG_URL = "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"

    private fun secretKey(): String {
        val parts = arrayOf("xQ9_mL2p", "R5tY_7vB", "3nM8kL1j", "H4gF6dS2a")
        return parts.joinToString("")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedBaseUrl: String? = null

    suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        cachedBaseUrl?.let { return@withContext it }
        val req = Request.Builder().url(CONFIG_URL).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()?.trim()?.trim('"')
                ?: throw Exception("Не удалось получить base URL")
            if (!body.startsWith("http")) throw Exception("Некорректный base URL")
            cachedBaseUrl = body.trimEnd('/')
            body.trimEnd('/')
        }
    }

    private fun auth(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${secretKey()}")
        .header("X-Api-Key", secretKey())

    // ---------- DB helpers ----------

    suspend fun dbGet(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val req = auth("$base/db/$path").get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("GET $path → ${resp.code}")
                text
            }
        }
    }

    suspend fun dbSet(path: String, jsonValue: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val body = jsonValue.toRequestBody("application/json".toMediaType())
            val req = auth("$base/db/$path").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("SET $path → ${resp.code}: $text")
                text
            }
        }
    }

    suspend fun dbSetObject(path: String, obj: JSONObject): Result<String> =
        dbSet(path, obj.toString())

    // ---------- Users ----------

    suspend fun getUserByTag(tag: String): Result<User?> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dbGet("users/$tag").getOrNull() ?: return@runCatching null
            val json = JSONObject(raw)
            if (!json.has("id")) return@runCatching null
            User(
                id = json.getString("id"),
                tag = json.optString("tag", tag),
                name = json.optString("name", tag),
                created = json.optLong("created", 0L)
            )
        }
    }

    suspend fun createUser(tag: String, name: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = getUserByTag(tag).getOrNull()
            if (existing != null) throw Exception("Тег @$tag уже занят")
            val id = "u_" + System.currentTimeMillis()
            val user = User(id = id, tag = tag, name = name)
            val obj = JSONObject().apply {
                put("id", user.id)
                put("tag", user.tag)
                put("name", user.name)
                put("created", user.created)
            }
            dbSetObject("users/$tag", obj).getOrThrow()
            // also index by id
            dbSetObject("users_by_id/${user.id}", obj).getOrThrow()
            user
        }
    }

    // ---------- Chats / Groups ----------

    suspend fun createGroup(owner: User, title: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val groupId = "g_" + System.currentTimeMillis()
            val obj = JSONObject().apply {
                put("id", groupId)
                put("title", title)
                put("owner", owner.id)
                put("ownerTag", owner.tag)
                put("created", System.currentTimeMillis())
                put("members", JSONArray().put(owner.tag))
            }
            dbSetObject("groups/$groupId", obj).getOrThrow()
            // add to owner's chat list
            addChatToUser(owner.id, groupId, title, true)
            groupId
        }
    }

    suspend fun joinGroup(groupId: String, user: User): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dbGet("groups/$groupId").getOrThrow()
            val obj = JSONObject(raw)
            val members = obj.optJSONArray("members") ?: JSONArray()
            var already = false
            for (i in 0 until members.length()) {
                if (members.getString(i) == user.tag) already = true
            }
            if (!already) members.put(user.tag)
            obj.put("members", members)
            dbSetObject("groups/$groupId", obj).getOrThrow()
            addChatToUser(user.id, groupId, obj.optString("title", "Группа"), true)
        }
    }

    private suspend fun addChatToUser(userId: String, chatId: String, title: String, isGroup: Boolean) {
        val path = "user_chats/$userId/$chatId"
        val obj = JSONObject().apply {
            put("id", chatId)
            put("title", title)
            put("isGroup", isGroup)
            put("added", System.currentTimeMillis())
        }
        dbSetObject(path, obj)
    }

    suspend fun getUserChats(userId: String): Result<List<ChatPreview>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dbGet("user_chats/$userId").getOrNull() ?: return@runCatching emptyList()
            val root = JSONObject(raw)
            val list = mutableListOf<ChatPreview>()
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                list.add(
                    ChatPreview(
                        id = item.optString("id", key),
                        title = item.optString("title", "Чат"),
                        isGroup = item.optBoolean("isGroup", false),
                        timestamp = item.optLong("added", 0L)
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        }
    }

    // ---------- Messages ----------

    suspend fun sendMessage(
        chatId: String,
        sender: User,
        plainText: String,
        encrypt: Boolean = true
    ): Result<Message> = withContext(Dispatchers.IO) {
        runCatching {
            val msgId = "m_" + System.currentTimeMillis()
            val textToStore = if (encrypt) Crypto.encrypt(plainText, chatId) else plainText
            val obj = JSONObject().apply {
                put("id", msgId)
                put("senderId", sender.id)
                put("senderTag", sender.tag)
                put("text", textToStore)
                put("encrypted", encrypt)
                put("timestamp", System.currentTimeMillis())
                put("type", "text")
            }
            dbSetObject("messages/$chatId/$msgId", obj).getOrThrow()
            Message(
                id = msgId,
                senderId = sender.id,
                senderTag = sender.tag,
                text = plainText,
                encrypted = encrypt,
                timestamp = obj.getLong("timestamp")
            )
        }
    }

    suspend fun getMessages(chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dbGet("messages/$chatId").getOrNull() ?: return@runCatching emptyList()
            val root = JSONObject(raw)
            val list = mutableListOf<Message>()
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val encrypted = item.optBoolean("encrypted", true)
                val stored = item.optString("text", "")
                val plain = if (encrypted) Crypto.decrypt(stored, chatId) else stored
                list.add(
                    Message(
                        id = item.optString("id", key),
                        senderId = item.optString("senderId"),
                        senderTag = item.optString("senderTag"),
                        text = plain,
                        encrypted = encrypted,
                        timestamp = item.optLong("timestamp", 0L),
                        type = item.optString("type", "text")
                    )
                )
            }
            list.sortedBy { it.timestamp }
        }
    }

    // ---------- Files ----------

    suspend fun uploadFile(file: File, fileName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = getBaseUrl()
            val name = fileName ?: file.name
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", name, file.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            val req = auth("$base/upload").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) throw Exception("Upload failed ${resp.code}")
                val json = JSONObject(text)
                json.optString("id").ifEmpty { json.optString("name", name) }
            }
        }
    }
}
