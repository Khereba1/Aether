package com.khereba1.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.khereba1.app.AppLog
import com.khereba1.app.data.AppSettings
import com.khereba1.app.data.CliEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onOpenSetup: () -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    var engine by remember(settings) { mutableStateOf(settings.cliEngine) }
    var customBin by remember(settings) { mutableStateOf(settings.customBinary) }
    var modelOverride by remember(settings) { mutableStateOf(settings.modelOverride) }
    var effort by remember(settings) { mutableStateOf(settings.effort) }
    var portText by remember(settings) { mutableStateOf(settings.port.toString()) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var logText by remember { mutableStateOf(AppLog.read()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("CLI Engine", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CliEngine.entries.forEach { item ->
                    FilterChip(
                        selected = engine == item,
                        onClick = { engine = item },
                        label = { Text(item.label) }
                    )
                }
            }

            if (engine == CliEngine.CUSTOM) {
                OutlinedTextField(
                    value = customBin,
                    onValueChange = { customBin = it },
                    label = { Text("Custom CLI binary name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = modelOverride,
                onValueChange = { modelOverride = it },
                label = { Text("Model slug (leave blank for CLI default)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Reasoning Effort (agy)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Auto", "low" to "Low", "medium" to "Medium", "high" to "High").forEach { (k, label) ->
                    FilterChip(
                        selected = effort == k,
                        onClick = { effort = k },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Local bridge port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, label) ->
                    FilterChip(
                        selected = themeMode == k,
                        onClick = { themeMode = k },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
                        onSave(
                            AppSettings(
                                port = parsedPort,
                                cliEngine = engine,
                                customBinary = customBin.trim(),
                                modelOverride = modelOverride.trim(),
                                effort = effort,
                                themeMode = themeMode,
                                language = "en"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Settings")
                }
                OutlinedButton(
                    onClick = onOpenSetup,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Setup Check")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("AppLog", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = logText.ifBlank { "Log is empty." }.takeLast(2500),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("aether_log", AppLog.read()))
                        onNotice("Log copied")
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Copy Log")
                }
                OutlinedButton(
                    onClick = {
                        AppLog.clear()
                        logText = ""
                        onNotice("Log cleared")
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Log")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
