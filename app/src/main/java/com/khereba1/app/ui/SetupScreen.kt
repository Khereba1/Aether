package com.khereba1.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.khereba1.app.AetherUiState
import com.khereba1.app.data.BridgeScripts
import com.khereba1.app.data.CliEngine
import com.khereba1.app.data.TermuxBridge

@Composable
fun SetupScreen(
    state: AetherUiState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onAutoStartBridge: (Boolean) -> Unit,
    onInstallCli: (CliEngine) -> Unit,
    onContinueToChat: () -> Unit,
    onCopiedNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val setup = state.setup
    val health = setup.health
    val activeCliKey = state.settings.cliEngine.binary
    val cliReady = if (activeCliKey.isEmpty()) true else (health.clis[activeCliKey] == true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تهيئة Aether",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "فحص تلقائي لـ Termux و Ubuntu والـ CLI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !setup.checking,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (setup.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (setup.statusNote.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = setup.statusNote,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Step 1: Termux Installed
            StepCard(
                stepNumber = "1",
                title = "تطبيق Termux",
                ok = setup.termuxInstalled,
                subtitle = if (setup.termuxInstalled) {
                    "موجود ومثبت على جهازك."
                } else {
                    "مش مثبت — نزّل نسخة F-Droid (مش Google Play)."
                }
            ) {
                if (!setup.termuxInstalled) {
                    Button(
                        onClick = { TermuxBridge.openFdroidTermux(context) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تحميل Termux من F-Droid")
                    }
                }
            }

            // Step 2: RUN_COMMAND Permission + allow-external-apps
            StepCard(
                stepNumber = "2",
                title = "صلاحية الربط مع Termux",
                ok = setup.permissionGranted && health.reachable,
                subtitle = "تسمح لـ Aether يشغّل سيرفر Ubuntu تلقائياً أول ما تفتح التطبيق."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!setup.permissionGranted) {
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("منح صلاحية RUN_COMMAND")
                        }
                    }
                    Text(
                        text = "مرة واحدة فقط في Termux: الصق الأمر ده لتفعيل allow-external-apps=true (واسمح لـ Termux بالظهور فوق التطبيقات):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CommandBox(
                        command = BridgeScripts.ENABLE_EXTERNAL_APPS_CMD,
                        onCopy = {
                            copyToClipboard(context, BridgeScripts.ENABLE_EXTERNAL_APPS_CMD)
                            onCopiedNotice("تم نسخ أمر التفعيل — الصقه في Termux")
                        }
                    )
                }
            }

            // Step 3: Ubuntu + Bridge Server
            StepCard(
                stepNumber = "3",
                title = "بيئة Ubuntu وسيرفر الربط",
                ok = health.reachable,
                subtitle = if (health.reachable) {
                    val envLabel = if (health.ubuntuReady) "Ubuntu جاهز" else "Python جاهز"
                    "$envLabel · Python ${health.pythonVersion} · Port ${state.settings.port}"
                } else {
                    "السيرفر المحلي مش شغال حالياً. اضغط تشغيل تلقائي عشان Aether يجهّز Ubuntu ويشغّله."
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAutoStartBridge(true) },
                        enabled = setup.termuxInstalled && !setup.checking,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تجهيز وتشغيل تلقائي")
                    }
                    OutlinedButton(
                        onClick = { onAutoStartBridge(false) },
                        enabled = setup.termuxInstalled && !setup.checking,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("شاشة Termux")
                    }
                }
            }

            // Step 4: CLI Engine (Antigravity / Gemini / Claude)
            StepCard(
                stepNumber = "4",
                title = "أداة الذكاء الاصطناعي (${state.settings.cliEngine.label})",
                ok = health.reachable && cliReady,
                subtitle = when {
                    !health.reachable -> "شغّل السيرفر في الخطوة 3 الأول عشان نفحص الـ CLI."
                    cliReady -> "أداة ${state.settings.cliEngine.binary} مثبتة وجاهزة جوه Ubuntu!"
                    else -> "أداة ${state.settings.cliEngine.binary} لسه مش مثبتة جوه Ubuntu. اضغط الزرار لتثبيتها وتسجيل الدخول بحسابك."
                }
            ) {
                if (health.reachable && !cliReady) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onInstallCli(state.settings.cliEngine) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تثبيت وتسجيل دخول ${state.settings.cliEngine.binary}")
                        }
                        CommandBox(
                            command = BridgeScripts.cliSetupCommand(state.settings.cliEngine),
                            onCopy = {
                                copyToClipboard(context, BridgeScripts.cliSetupCommand(state.settings.cliEngine))
                                onCopiedNotice("تم نسخ أمر التثبيت")
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onContinueToChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = health.reachable
            ) {
                Text("ابدأ الشغل في Aether", style = MaterialTheme.typography.titleMedium)
            }

            if (!health.reachable) {
                TextButton(
                    onClick = onContinueToChat,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("تخطي مؤقتاً واستعراض الواجهة")
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    ok: Boolean,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(
            1.dp,
            if (ok) Color(0xFF2E7D32).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$stepNumber. $title",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action()
        }
    }
}

@Composable
private fun CommandBox(command: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("نسخ")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("aether_cmd", text))
}
