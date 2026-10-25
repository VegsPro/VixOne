package com.vix.one.ms

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UiState(
    val accountStatus: String = "Не подключено",
    val folderUri: String? = null,
    val folderName: String = "Папка не выбрана",
    val syncStatus: String = "Ожидание...",
    val lastSync: String = "—",
    val log: String = "Лог операций появится здесь...",
    val isBusy: Boolean = false,
    val isConnected: Boolean = false
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("vix_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    init {
        loadLocalState()
        viewModelScope.launch { checkConnection() }
    }

    private fun loadLocalState() {
        val folder = prefs.getString("folder_uri", null)
        val last = prefs.getString("last_sync", "—") ?: "—"
        _state.update {
            it.copy(
                folderUri = folder,
                folderName = prefs.getString("folder_name", "Папка не выбрана") ?: "Папка не выбрана",
                lastSync = last,
                accountStatus = if (prefs.getBoolean("registered", false)) "Устройство: ${deviceId.take(8)}…" else "Не подключено"
            )
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _state.update { s ->
            val newLog = "$time  $msg\n${s.log}".take(4000)
            s.copy(log = newLog)
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, syncStatus = "Проверка сервера...") }
            val result = ApiClient.getBaseUrl()
            result.onSuccess { url ->
                appendLog("Сервер: $url")
                // Register device in DB
                val reg = ApiClient.dbWrite("vix_devices", deviceId, JSONObject().apply {
                    put("last_seen", System.currentTimeMillis())
                    put("name", android.os.Build.MODEL)
                }.toString())
                reg.onSuccess {
                    prefs.edit().putBoolean("registered", true).apply()
                    _state.update {
                        it.copy(
                            isConnected = true,
                            accountStatus = "Устройство: ${deviceId.take(8)}…",
                            syncStatus = "Подключено",
                            isBusy = false
                        )
                    }
                    appendLog("Устройство зарегистрировано")
                }.onFailure { e ->
                    _state.update { it.copy(isBusy = false, syncStatus = "Ошибка регистрации") }
                    appendLog("Ошибка: ${e.message}")
                }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, syncStatus = "Нет связи", isConnected = false) }
                appendLog("Ошибка base URL: ${e.message}")
            }
        }
    }

    fun setFolder(uri: Uri, name: String) {
        prefs.edit()
            .putString("folder_uri", uri.toString())
            .putString("folder_name", name)
            .apply()
        _state.update { it.copy(folderUri = uri.toString(), folderName = name) }
        appendLog("Выбрана папка: $name")
    }

    fun startSync() {
        val folderUriStr = _state.value.folderUri
        if (folderUriStr.isNullOrBlank()) {
            appendLog("Сначала выберите папку")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, syncStatus = "Синхронизация...") }
            appendLog("=== Начало синхронизации ===")

            try {
                val ctx = getApplication<Application>()
                val treeUri = Uri.parse(folderUriStr)
                val doc = DocumentFile.fromTreeUri(ctx, treeUri)
                if (doc == null || !doc.canRead()) {
                    appendLog("Не удалось открыть папку")
                    _state.update { it.copy(isBusy = false, syncStatus = "Ошибка папки") }
                    return@launch
                }

                // 1. List remote files
                val remoteResult = ApiClient.listImages()
                val remote = remoteResult.getOrElse {
                    appendLog("Ошибка списка: ${it.message}")
                    emptyMap()
                }
                appendLog("На сервере файлов: ${remote.size}")

                // 2. Local files
                val localFiles = doc.listFiles().filter { it.isFile && it.name != null }
                appendLog("Локально файлов: ${localFiles.size}")

                // Simple strategy: upload all local that are not present by name
                var uploaded = 0
                for (file in localFiles) {
                    val name = file.name ?: continue
                    val already = remote.values.any { it.contains(name) || it.endsWith(name) }
                    if (!already) {
                        appendLog("Загрузка: $name")
                        // Copy to cache
                        val cache = File(ctx.cacheDir, name)
                        ctx.contentResolver.openInputStream(file.uri)?.use { input ->
                            cache.outputStream().use { output -> input.copyTo(output) }
                        }
                        val up = ApiClient.uploadFile(cache, name)
                        up.onSuccess {
                            uploaded++
                            appendLog("OK: $name → $it")
                        }.onFailure { e ->
                            appendLog("FAIL $name: ${e.message}")
                        }
                        cache.delete()
                    }
                }

                // Save sync meta
                val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                prefs.edit().putString("last_sync", now).apply()
                ApiClient.dbWrite("vix_sync", deviceId, JSONObject().apply {
                    put("last_sync", System.currentTimeMillis())
                    put("uploaded", uploaded)
                    put("local_count", localFiles.size)
                    put("remote_count", remote.size)
                }.toString())

                _state.update {
                    it.copy(
                        isBusy = false,
                        syncStatus = "Готово (+$uploaded)",
                        lastSync = now
                    )
                }
                appendLog("=== Синхронизация завершена ===")
            } catch (e: Exception) {
                appendLog("Критическая ошибка: ${e.message}")
                _state.update { it.copy(isBusy = false, syncStatus = "Ошибка") }
            }
        }
    }

    fun clearLog() {
        _state.update { it.copy(log = "") }
    }
}
