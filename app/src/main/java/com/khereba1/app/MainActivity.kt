package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.khereba1.app.data.DownloadedArtifact
import com.khereba1.app.data.TermuxBridge
import com.khereba1.app.ui.AetherTheme
import com.khereba1.app.ui.ChatScreen
import com.khereba1.app.ui.SettingsScreen
import com.khereba1.app.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val vm: AetherViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLog.log("PERM", "RUN_COMMAND granted=$granted")
        vm.refreshEnvironment(autoWake = granted)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) handlePickedFile(uri)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.ui.collectAsState()
            val snackbarHost = remember { SnackbarHostState() }

            LaunchedEffect(state.snackbarMessage) {
                val msg = state.snackbarMessage
                if (!msg.isNullOrBlank()) {
                    snackbarHost.showSnackbar(msg)
                    vm.clearSnackbar()
                }
            }

            LaunchedEffect(state.lastExportedArtifact) {
                val artifact = state.lastExportedArtifact
                if (artifact != null && artifact.file.exists()) {
                    shareArtifact(artifact)
                    vm.clearLastExportedArtifact()
                }
            }

            AetherTheme(themeMode = state.settings.themeMode, language = state.settings.language) {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHost) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (state.screen) {
                            AppScreen.SETUP -> SetupScreen(
                                state = state,
                                onRequestPermission = {
                                    permLauncher.launch(TermuxBridge.PERMISSION_RUN_COMMAND)
                                },
                                onRefresh = { vm.refreshEnvironment(autoWake = false) },
                                onAutoStartBridge = { bg -> vm.triggerAutoBootstrap(background = bg) },
                                onInstallCli = { engine -> vm.launchCliSetupInTermux(engine) },
                                onContinueToChat = { vm.navigateTo(AppScreen.CHAT) },
                                onCopiedNotice = { vm.showNotice(it) }
                            )

                            AppScreen.CHAT -> ChatScreen(
                                state = state,
                                onSendPrompt = { vm.sendPrompt(it) },
                                onSelectProject = { vm.selectProject(it) },
                                onCreateProject = { n, t, r -> vm.createProject(n, t, r) },
                                onDeleteProject = { vm.deleteProject(it) },
                                onClearChat = { vm.clearActiveChat() },
                                onLoadRules = { vm.loadActiveRules() },
                                onSaveRules = { vm.saveActiveRules(it) },
                                onLoadFiles = { vm.loadActiveFiles() },
                                onPickUploadFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                                onExportSingleFile = { path -> vm.exportSingleFile(path) },
                                onExportZip = { vm.exportActiveProjectZip() },
                                onOpenSettings = { vm.navigateTo(AppScreen.SETTINGS) },
                                onOpenSetup = { vm.navigateTo(AppScreen.SETUP) },
                                onRefreshConnection = { vm.refreshEnvironment(autoWake = true) },
                                onNotice = { vm.showNotice(it) }
                            )

                            AppScreen.SETTINGS -> SettingsScreen(
                                settings = state.settings,
                                onBack = { vm.navigateTo(AppScreen.CHAT) },
                                onSave = { updated, langChanged ->
                                    vm.updateSettings(updated)
                                    if (langChanged) recreate()
                                },
                                onOpenSetup = { vm.navigateTo(AppScreen.SETUP) },
                                onNotice = { vm.showNotice(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handlePickedFile(uri: Uri) {
        try {
            var name = "uploaded_file"
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    name = c.getString(idx) ?: name
                }
            }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                vm.uploadFileToActiveProject(name, bytes)
            }
        } catch (e: Exception) {
            AppLog.log("PICKER", "Failed to read picked file", e)
            vm.showNotice("Could not read selected file")
        }
    }

    private fun shareArtifact(artifact: DownloadedArtifact) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", artifact.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = artifact.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open or share ${artifact.file.name}"))
        } catch (e: Exception) {
            AppLog.log("SHARE", "Failed to open share sheet", e)
        }
    }
}
