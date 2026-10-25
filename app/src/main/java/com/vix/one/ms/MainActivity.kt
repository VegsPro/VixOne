package com.vix.one.ms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VixTheme {
                val state by vm.state.collectAsState()
                when (state.screen) {
                    Screen.Auth -> AuthScreen(
                        isLoading = state.isLoading,
                        error = state.error,
                        onLogin = { tag, name -> vm.loginOrRegister(tag, name) },
                        onClearError = { vm.clearError() }
                    )
                    Screen.Chats -> ChatsScreen(
                        state = state,
                        onOpenChat = { vm.openChat(it) },
                        onCreateGroup = { vm.goTo(Screen.CreateGroup) },
                        onRefresh = { vm.loadChats() },
                        onLogout = { vm.logout() }
                    )
                    Screen.Chat -> ChatScreen(
                        state = state,
                        onBack = { vm.goTo(Screen.Chats) },
                        onSend = { text, encrypt -> vm.sendMessage(text, encrypt) },
                        onAddMember = { vm.goTo(Screen.AddMember) },
                        onRefresh = { state.currentChatId?.let { vm.loadMessages(it) } }
                    )
                    Screen.CreateGroup -> CreateGroupScreen(
                        isLoading = state.isLoading,
                        error = state.error,
                        onCreate = { vm.createGroup(it) },
                        onBack = { vm.goTo(Screen.Chats) }
                    )
                    Screen.AddMember -> AddMemberScreen(
                        isLoading = state.isLoading,
                        error = state.error,
                        onAdd = { vm.addMemberByTag(it) },
                        onBack = { vm.goTo(Screen.Chat) }
                    )
                }
            }
        }
    }
}

@Composable
fun VixTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF2AABEE),
        secondary = Color(0xFF34A853),
        background = Color(0xFF0E1621),
        surface = Color(0xFF17212B),
        onPrimary = Color.White,
        onBackground = Color(0xFFE4E6EB),
        onSurface = Color(0xFFE4E6EB),
        surfaceVariant = Color(0xFF242F3D)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

// ==================== AUTH ====================

@Composable
fun AuthScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VIX", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2AABEE))
            Text("Чат с шифрованием", color = Color(0xFF8B98A5), fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("Тег (например: alex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("@", color = Color(0xFF2AABEE), fontSize = 18.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLogin(tag, name) })
            )

            if (error != null) {
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            Button(
                onClick = { onLogin(tag, name) },
                enabled = !isLoading && tag.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE))
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                else Text("Войти / Создать", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==================== CHATS LIST ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    state: AppState,
    onOpenChat: (ChatPreview) -> Unit,
    onCreateGroup: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VIX", fontWeight = FontWeight.Bold, color = Color(0xFF2AABEE))
                        state.currentUser?.let {
                            Text("@${it.tag}", fontSize = 12.sp, color = Color(0xFF8B98A5))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17212B)),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFF8B98A5))
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, null, tint = Color(0xFF8B98A5))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroup,
                containerColor = Color(0xFF2AABEE)
            ) {
                Icon(Icons.Default.GroupAdd, null)
            }
        },
        containerColor = Color(0xFF0E1621)
    ) { padding ->
        if (state.chats.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет чатов.\nСоздайте группу или откройте Архив.", color = Color(0xFF8B98A5), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.chats, key = { it.id }) { chat ->
                    ChatItem(chat = chat, onClick = { onOpenChat(chat) })
                }
            }
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2AABEE))
            }
        }
    }
}

@Composable
fun ChatItem(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (chat.isGroup) Color(0xFF2AABEE) else Color(0xFF34A853)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (chat.isGroup) Icons.Default.Group else Icons.Default.Person,
                null,
                tint = Color.White
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (chat.isGroup) "Группа" else "Личный архив",
                fontSize = 13.sp,
                color = Color(0xFF8B98A5)
            )
        }
    }
}

// ==================== CHAT ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppState,
    onBack: () -> Unit,
    onSend: (String, Boolean) -> Unit,
    onAddMember: () -> Unit,
    onRefresh: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var encrypt by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.currentChatTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (encrypt) Text("🔒 Шифрование включено", fontSize = 11.sp, color = Color(0xFF34A853))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFF8B98A5))
                    }
                    if (state.currentChatId?.startsWith("g_") == true) {
                        IconButton(onClick = onAddMember) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF2AABEE))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17212B))
            )
        },
        bottomBar = {
            Column(Modifier.background(Color(0xFF17212B))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = encrypt,
                        onCheckedChange = { encrypt = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34A853))
                    )
                    Text("Шифровать", fontSize = 13.sp, color = Color(0xFF8B98A5))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Сообщение...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2AABEE),
                            unfocusedBorderColor = Color(0xFF2A3A4A)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text, encrypt)
                                text = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2AABEE))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color(0xFF0E1621)
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    isMine = msg.senderId == state.currentUser?.id
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isMine: Boolean) {
    val time = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text("@${message.senderTag}", fontSize = 11.sp, color = Color(0xFF2AABEE), modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMine) Color(0xFF2B5278) else Color(0xFF182533))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 300.dp)
        ) {
            Column {
                Text(message.text, color = Color(0xFFE4E6EB), fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.encrypted) {
                        Text("🔒 ", fontSize = 10.sp)
                    }
                    Text(time, fontSize = 11.sp, color = Color(0xFF8B98A5))
                }
            }
        }
    }
}

// ==================== CREATE GROUP ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    isLoading: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая группа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17212B))
            )
        },
        containerColor = Color(0xFF0E1621)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название группы") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error, color = Color(0xFFFF6B6B))
            Button(
                onClick = { onCreate(title) },
                enabled = !isLoading && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE))
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                else Text("Создать группу")
            }
        }
    }
}

// ==================== ADD MEMBER ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberScreen(
    isLoading: Boolean,
    error: String?,
    onAdd: (String) -> Unit,
    onBack: () -> Unit
) {
    var tag by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить участника") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17212B))
            )
        },
        containerColor = Color(0xFF0E1621)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Введите тег пользователя", color = Color(0xFF8B98A5))
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("Тег") },
                singleLine = true,
                leadingIcon = { Text("@", color = Color(0xFF2AABEE)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error, color = Color(0xFFFF6B6B))
            Button(
                onClick = { onAdd(tag) },
                enabled = !isLoading && tag.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE))
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                else Text("Добавить")
            }
        }
    }
}
