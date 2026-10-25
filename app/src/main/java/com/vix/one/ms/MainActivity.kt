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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onSync = { vm.startSync() },
                    onClearLog = { vm.clearLog() },
                    folderPicker = { uri ->
                        val doc = DocumentFile.fromTreeUri(this, uri)
                        val name = doc?.name ?: "Выбранная папка"
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
                        text = "VIX One",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A73E8)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D)
                ),
                actions = {
                    IconButton(onClick = onConnect) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = Color(0xFF1A73E8)
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Аккаунт / Устройство", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.accountStatus, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8EAED))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConnect,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isConnected) "Обновить подключение" else "Подключить")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Папка для синхронизации", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.folderName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE8EAED))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { launcher.launch(null) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A73E8))
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выбрать папку")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Статус синхронизации", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.syncStatus, fontSize = 16.sp, color = Color(0xFFE8EAED))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Последняя: ${state.lastSync}", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                }
            }

            Button(
                onClick = onSync,
                enabled = !state.isBusy && state.folderUri != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Синхронизировать сейчас", fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Лог", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                        TextButton(onClick = onClearLog) {
                            Text(text = "Очистить", fontSize = 12.sp, color = Color(0xFF1A73E8))
                        }
                    }
                    Text(
                        text = state.log,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9AA0A6),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    )
                }
            }
        }
    }
}
