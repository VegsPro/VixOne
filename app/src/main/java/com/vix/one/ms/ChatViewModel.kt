package com.vix.one.ms

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AppState(
    val currentUser: User? = null,
    val chats: List<ChatPreview> = emptyList(),
    val currentChatId: String? = null,
    val currentChatTitle: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val screen: Screen = Screen.Auth
)

enum class Screen { Auth, Chats, Chat, CreateGroup, AddMember }

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("vix_chat", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        val tag = prefs.getString("tag", null)
        val id = prefs.getString("id", null)
        val name = prefs.getString("name", null)
        if (tag != null && id != null && name != null) {
            _state.update {
                it.copy(
                    currentUser = User(id, tag, name),
                    screen = Screen.Chats
                )
            }
            loadChats()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun loginOrRegister(tag: String, name: String) {
        if (tag.isBlank()) {
            _state.update { it.copy(error = "Введите тег") }
            return
        }
        val cleanTag = tag.trim().removePrefix("@").lowercase()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // try get existing
            val existing = ApiClient.getUserByTag(cleanTag).getOrNull()
            val user = if (existing != null) {
                existing
            } else {
                val created = ApiClient.createUser(cleanTag, name.ifBlank { cleanTag })
                created.getOrElse { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    return@launch
                }
            }
            prefs.edit()
                .putString("tag", user.tag)
                .putString("id", user.id)
                .putString("name", user.name)
                .apply()
            // ensure personal archive exists in chat list
            ensurePersonalArchive(user)
            _state.update {
                it.copy(currentUser = user, isLoading = false, screen = Screen.Chats)
            }
            loadChats()
        }
    }

    private suspend fun ensurePersonalArchive(user: User) {
        val archiveId = "personal_${user.id}"
        // just add to user_chats
        val path = "user_chats/${user.id}/$archiveId"
        val obj = JSONObject().apply {
            put("id", archiveId)
            put("title", "Архив")
            put("isGroup", false)
            put("added", System.currentTimeMillis())
        }
        ApiClient.dbSetObject(path, obj)
    }

    fun loadChats() {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = ApiClient.getUserChats(user.id)
            result.onSuccess { list ->
                _state.update { it.copy(chats = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun openChat(chat: ChatPreview) {
        _state.update {
            it.copy(
                currentChatId = chat.id,
                currentChatTitle = chat.title,
                screen = Screen.Chat,
                messages = emptyList()
            )
        }
        loadMessages(chat.id)
    }

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = ApiClient.getMessages(chatId)
            result.onSuccess { msgs ->
                _state.update { it.copy(messages = msgs, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun sendMessage(text: String, encrypt: Boolean = true) {
        val user = _state.value.currentUser ?: return
        val chatId = _state.value.currentChatId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = ApiClient.sendMessage(chatId, user, text.trim(), encrypt)
            result.onSuccess { msg ->
                _state.update { s ->
                    s.copy(messages = s.messages + msg)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun createGroup(title: String) {
        val user = _state.value.currentUser ?: return
        if (title.isBlank()) {
            _state.update { it.copy(error = "Введите название группы") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = ApiClient.createGroup(user, title.trim())
            result.onSuccess {
                _state.update { it.copy(isLoading = false, screen = Screen.Chats) }
                loadChats()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun addMemberByTag(tag: String) {
        val user = _state.value.currentUser ?: return
        val chatId = _state.value.currentChatId ?: return
        val clean = tag.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val target = ApiClient.getUserByTag(clean).getOrNull()
            if (target == null) {
                _state.update { it.copy(isLoading = false, error = "Пользователь @$clean не найден") }
                return@launch
            }
            val result = ApiClient.joinGroup(chatId, target)
            result.onSuccess {
                _state.update { it.copy(isLoading = false, error = null) }
                // also add chat to the target user's list is already done in joinGroup
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun goTo(screen: Screen) {
        _state.update { it.copy(screen = screen, error = null) }
        if (screen == Screen.Chats) loadChats()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _state.update { AppState() }
    }
}
