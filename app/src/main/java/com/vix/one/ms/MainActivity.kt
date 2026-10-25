package com.vix.one.ms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VixTheme {
                val state by vm.state.collectAsState()
                MainScreen(
                    state = state,
                    onConnect = { vm.checkConnection() },
                    onSelectFolder = { /* handled by launcher */ },
                    onSync = { vm.startSync() },
                    onClearLog = { vm.clearLog() },
                    folderPicker = { uri ->
                        val doc = DocumentFile.fromTreeUri(this, uri)
                        val name = doc?.name ?: "Выбранная папка"
                        // Persist permission
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        vm.setFolder(uri, name)
                    }
                )
            }
        }
    }
}

@Composable
fun VixTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF1A73E8),
        secondary = Color(0xFF34A853),
        background = Color(0xFF0D0D0D),
        surface = Color(0xFF1A1A1A),
        onPrimary = Color.White,
        onBackground = Color(0xFFE8EAED),
        onSurface = Color(0xFFE8EAED)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onConnect: () -> Unit,
    onSelectFolder: () -> Unit,
    onSync: () -> Unit,
    onClearLog: () -> Unit,
    folderPicker: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { folderPicker(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "VIX One",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A73E8)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D)
                ),
                actions = {
                    IconButton(onClick = onConnect) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = Color(0xFF1A73E8))
                    }
                }
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier = Modifier.padding(16.dp)) {
                    Text("Аккаунт / Устройство", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(Modifier = Modifier.height(4.dp))
                    Text(
                        state.accountStatus,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE8EAED)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConnect,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(Modifier = Modifier.width(8.dp))
                        Text(if (state.isConnected) "Обновить подключение" else "Подключить")
                    }
                }
            }

            // Folder card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Папка для синхронизации", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.folderName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE8EAED)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { launcher.launch(null) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A73E8))
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier = Modifier.width(8.dp))
                        Text("Выбрать папку")
                    }
                }
            }

            // Sync status
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Статус синхронизации", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.syncStatus,
                        fontSize = 16.sp,
                        color = Color(0xFFE8EAED)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Последняя: ${state.lastSync}",
                        fontSize = 13.sp,
                        color = Color(0xFF9AA0A6)
                    )
                }
            }

            // Sync button
            Button(
                onClick = onSync,
                enabled = !state.isBusy && state.folderUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Синхронизировать сейчас", fontWeight = FontWeight.Bold)
                }
            }

            // Log
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Лог", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                        TextButton(onClick = onClearLog) {
                            Text("Очистить", fontSize = 12.sp, color = Color(0xFF1A73E8))
                        }
                    }
                    Text(
                        state.log,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9AA0A6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                    )
                }
            }
        }
    }
}
