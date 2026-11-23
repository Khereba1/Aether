package com.khereba1.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khereba1.app.AetherUiState
import com.khereba1.app.data.ChatMessage
import com.khereba1.app.data.ProjectInfo
import com.khereba1.app.data.ProjectTemplate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AetherUiState,
    onSendPrompt: (String) -> Unit,
    onSelectProject: (ProjectInfo) -> Unit,
    onCreateProject: (String, ProjectTemplate, String) -> Unit,
    onDeleteProject: (ProjectInfo) -> Unit,
    onClearChat: () -> Unit,
    onLoadRules: () -> Unit,
    onSaveRules: (String) -> Unit,
    onLoadFiles: () -> Unit,
    onPickUploadFile: () -> Unit,
    onExportZip: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onRefreshConnection: () -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFilesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Aether", style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showNewProjectDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشروع معزول جديد")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "المشاريع (${state.projects.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.projects, key = { it.slug }) { project ->
                            val selected = project.slug == state.activeProject?.slug
                            NavigationDrawerItem(
                                selected = selected,
                                onClick = {
                                    onSelectProject(project)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                label = {
                                    Column {
                                        Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${project.fileCount} ملف" + if (project.hasRules) " · GEMINI.md" else "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                badge = {
                                    IconButton(onClick = { onDeleteProject(project) }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSetup()
                        },
                        icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                        label = {
                            Text(if (state.setup.health.reachable) "البيئة (متصل)" else "تهيئة البيئة (غير متصل)")
                        }
                    )
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSettings()
                        },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("الإعدادات والسجل") }
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = state.activeProject?.name ?: "Aether",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.setup.health.reachable) Color(0xFF2E7D32)
                                            else MaterialTheme.colorScheme.error
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (state.setup.health.reachable) {
                                        state.settings.cliEngine.binary.ifBlank { "CLI" }
                                    } else {
                                        "غير متصل — اضغط للتشغيل"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        if (!state.setup.health.reachable) onRefreshConnection()
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.activeProject != null) {
                            IconButton(onClick = {
                                onLoadRules()
                                showRulesDialog = true
                            }) {
                                Icon(Icons.Rounded.Description, contentDescription = "Rules")
                            }
                            IconButton(onClick = {
                                onLoadFiles()
                                showFilesDialog = true
                            }) {
                                Icon(Icons.Rounded.FolderZip, contentDescription = "Files and ZIP")
                            }
                            IconButton(onClick = onClearChat) {
                                Icon(Icons.Rounded.Add, contentDescription = "New chat")
                            }
                        } else {
                            IconButton(onClick = onRefreshConnection) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                if (state.messages.isEmpty()) {
                    WelcomeEmptyState(
                        modifier = Modifier.weight(1f),
                        projectName = state.activeProject?.name,
                        engineLabel = state.settings.cliEngine.label,
                        onQuickPrompt = { prompt -> onSendPrompt(prompt) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageItem(
                                message = msg,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("aether_msg", msg.text))
                                    onNotice("تم نسخ الرد")
                                },
                                onExportZip = onExportZip
                            )
                        }
                        if (state.isStreaming && state.activeToolStatus.isNotBlank()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "شغال على أداة: ${state.activeToolStatus}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Claude-inspired floating rounded composer
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = onPickUploadFile,
                            enabled = state.activeProject != null && !state.isStreaming
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = "Upload file",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("اكتب لـ Aether…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Box(
                            modifier = Modifier
                                .padding(bottom = 6.dp, end = 4.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !state.isStreaming)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                                .clickable(enabled = inputText.isNotBlank() && !state.isStreaming) {
                                    val toSend = inputText
                                    inputText = ""
                                    onSendPrompt(toSend)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isStreaming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.rounded.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, template, rules ->
                showNewProjectDialog = false
                onCreateProject(name, template, rules)
            }
        )
    }

    if (showRulesDialog && state.activeProject != null) {
        RulesEditorDialog(
            projectName = state.activeProject.name,
            initialRules = state.currentRules,
            onDismiss = { showRulesDialog = false },
            onSave = { updated ->
                showRulesDialog = false
                onSaveRules(updated)
            }
        )
    }

    if (showFilesDialog && state.activeProject != null) {
        ProjectFilesDialog(
            projectName = state.activeProject.name,
            files = state.projectFiles,
            onDismiss = { showFilesDialog = false },
            onUpload = {
                showFilesDialog = false
                onPickUploadFile()
            },
            onExportZip = {
                showFilesDialog = false
                onExportZip()
            }
        )
    }
}

@Composable
private fun WelcomeEmptyState(
    modifier: Modifier,
    projectName: String?,
    engineLabel: String,
    onQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "تحب نشتغل على إيه النهارده؟",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = (projectName?.let { "مشروع: $it · " } ?: "") + engineLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickChip("استعرض الملفات الموجودة في المشروع ده ولخّصلي هيكلها") { onQuickPrompt(it) }
            QuickChip("اعملي ملخص منظم حسب قواعد GEMINI.md للملفات المرفوعة") { onQuickPrompt(it) }
            QuickChip("جهّزلي ملفات المشروع النهائية عشان أعملها تصدير ZIP") { onQuickPrompt(it) }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(text) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    onCopy: () -> Unit,
    onExportZip: () -> Unit
) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Aether",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (message.text.isNotBlank()) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (message.toolEvents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.toolEvents.take(4).forEach { tool ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = tool,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = message.text.ifBlank { "…" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )

            if (message.changedFiles.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تم تعديل/إنشاء ${message.changedFiles.size} ملف",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = message.changedFiles.take(3).joinToString("، "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(onClick = onExportZip, shape = RoundedCornerShape(10.dp)) {
                            Text("تحميل ZIP")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, ProjectTemplate, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ProjectTemplate.CODING) }
    var rules by remember { mutableStateOf(ProjectTemplate.CODING.defaultRules) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشروع معزول جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المشروع (مثلاً: ملخصات التمريض)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectTemplate.entries.forEach { t ->
                        FilterChip(
                            selected = template == t,
                            onClick = {
                                template = t
                                rules = t.defaultRules
                            },
                            label = { Text(t.labelAr) }
                        )
                    }
                }
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("قواعد المشروع (GEMINI.md)") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.ifBlank { "مشروع جديد" }, template, rules) }
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun RulesEditorDialog(
    projectName: String,
    initialRules: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialRules) { mutableStateOf(initialRules) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قواعد وذاكرة: $projectName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("GEMINI.md") },
                minLines = 8,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun ProjectFilesDialog(
    projectName: String,
    files: List<com.khereba1.app.data.WorkspaceFile>,
    onDismiss: () -> Unit,
    onUpload: () -> Unit,
    onExportZip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملفات مشروع: $projectName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (files.isEmpty()) {
                    Text("الفولدر فاضي حالياً.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    files.take(15).forEach { f ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = (if (f.isDir) "📁 " else "📄 ") + f.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!f.isDir) {
                                Text(
                                    text = "${(f.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUpload, modifier = Modifier.weight(1f)) {
                        Text("رفع ملف")
                    }
                    Button(onClick = onExportZip, modifier = Modifier.weight(1f)) {
                        Text("تحميل ZIP")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تم") }
        }
    )
}
