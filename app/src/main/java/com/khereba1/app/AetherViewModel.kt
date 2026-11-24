package com.khereba1.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khereba1.app.data.AppSettings
import com.khereba1.app.data.BridgeClient
import com.khereba1.app.data.BridgeScripts
import com.khereba1.app.data.ChatMessage
import com.khereba1.app.data.CliEngine
import com.khereba1.app.data.DownloadedArtifact
import com.khereba1.app.data.LocalStore
import com.khereba1.app.data.ModelOption
import com.khereba1.app.data.ProjectInfo
import com.khereba1.app.data.ProjectTemplate
import com.khereba1.app.data.SetupStatus
import com.khereba1.app.data.TermuxBridge
import com.khereba1.app.data.WorkspaceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen { SETUP, CHAT, SETTINGS }

data class AetherUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val settings: AppSettings = AppSettings(),
    val setup: SetupStatus = SetupStatus(),
    val projects: List<ProjectInfo> = emptyList(),
    val activeProject: ProjectInfo? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val activeToolStatus: String = "",
    val projectFiles: List<WorkspaceFile> = emptyList(),
    val currentRules: String = "",
    val availableModels: List<ModelOption> = emptyList(),
    val defaultCliModel: String = "",
    val loadingModels: Boolean = false,
    val snackbarMessage: String? = null,
    val lastExportedArtifact: DownloadedArtifact? = null
)

class AetherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    private val _ui = MutableStateFlow(AetherUiState(settings = LocalStore.loadSettings(ctx)))
    val ui: StateFlow<AetherUiState> = _ui.asStateFlow()

    private var streamJob: Job? = null

    init {
        refreshEnvironment(autoWake = true, switchToChatIfReady = true)
    }

    fun navigateTo(screen: AppScreen) {
        _ui.update { it.copy(screen = screen) }
    }

    fun clearSnackbar() {
        _ui.update { it.copy(snackbarMessage = null) }
    }

    fun clearLastExportedArtifact() {
        _ui.update { it.copy(lastExportedArtifact = null) }
    }

    fun showNotice(msg: String) {
        _ui.update { it.copy(snackbarMessage = msg) }
    }

    fun refreshEnvironment(autoWake: Boolean = false, switchToChatIfReady: Boolean = false) {
        viewModelScope.launch {
            val port = _ui.value.settings.port
            val termuxOk = TermuxBridge.isTermuxInstalled(ctx)
            val permOk = TermuxBridge.hasRunCommandPermission(ctx)
            _ui.update {
                it.copy(
                    setup = it.setup.copy(
                        termuxInstalled = termuxOk,
                        permissionGranted = permOk,
                        checking = true,
                        statusNote = ""
                    )
                )
            }

            var health = BridgeClient.checkHealth(port)
            if (!health.reachable && autoWake && termuxOk && permOk) {
                _ui.update { it.copy(setup = it.setup.copy(statusNote = "Waking Ubuntu bridge in Termux...")) }
                val fastRes = TermuxBridge.runCommand(ctx, BridgeScripts.termuxFastWakeCommand(port), background = true)
                if (fastRes.isSuccess) {
                    for (attempt in 0 until 5) {
                        delay(1000)
                        health = BridgeClient.checkHealth(port)
                        if (health.reachable) break
                    }
                }
                if (!health.reachable) {
                    _ui.update { it.copy(setup = it.setup.copy(statusNote = "Installing bridge inside Ubuntu...")) }
                    val fullRes = TermuxBridge.runCommand(ctx, BridgeScripts.termuxStartBridgeCommand(port), background = true)
                    if (fullRes.isSuccess) {
                        for (attempt in 0 until 6) {
                            delay(1200)
                            health = BridgeClient.checkHealth(port)
                            if (health.reachable) break
                        }
                    }
                }
            }

            _ui.update {
                it.copy(
                    setup = SetupStatus(
                        termuxInstalled = termuxOk,
                        permissionGranted = permOk,
                        health = health,
                        checking = false,
                        statusNote = if (health.reachable) "" else it.setup.statusNote
                    ),
                    screen = if (switchToChatIfReady && health.reachable) AppScreen.CHAT else it.screen
                )
            }

            if (health.reachable) {
                loadProjects()
                loadModels()
            }
        }
    }

    fun triggerAutoBootstrap(background: Boolean = true) {
        viewModelScope.launch {
            val port = _ui.value.settings.port
            _ui.update { it.copy(setup = it.setup.copy(checking = true, statusNote = "Sent bootstrap command to Termux...")) }
            val result = TermuxBridge.runCommand(ctx, BridgeScripts.termuxStartBridgeCommand(port), background = background)
            if (result.isFailure) {
                _ui.update {
                    it.copy(
                        setup = it.setup.copy(
                            checking = false,
                            statusNote = "Could not launch Termux automatically. Make sure allow-external-apps=true and Display Over Other Apps are enabled."
                        )
                    )
                }
                return@launch
            }
            var health = BridgeClient.checkHealth(port)
            for (i in 0 until 8) {
                if (health.reachable && health.bridgeVersion == BridgeScripts.BRIDGE_VERSION) break
                delay(1500)
                health = BridgeClient.checkHealth(port)
            }
            _ui.update {
                it.copy(
                    setup = it.setup.copy(
                        health = health,
                        checking = false,
                        statusNote = if (health.reachable) "Connected successfully!" else "Still starting — open Termux or tap Copy Manual."
                    )
                )
            }
            if (health.reachable) {
                loadProjects()
                loadModels()
            }
        }
    }

    fun launchCliSetupInTermux(engine: CliEngine) {
        val cmd = BridgeScripts.cliSetupCommand(engine)
        val res = TermuxBridge.runCommand(ctx, cmd, background = false)
        if (res.isFailure) {
            showNotice("Open Termux and paste the CLI install command.")
        } else {
            showNotice("Opened Termux to install & sign in to ${engine.label}")
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            _ui.update { it.copy(loadingModels = true) }
            val resp = BridgeClient.listModels(_ui.value.settings.port)
            _ui.update {
                it.copy(
                    availableModels = resp.models,
                    defaultCliModel = resp.currentModel,
                    loadingModels = false
                )
            }
        }
    }

    fun selectModelAndEffort(modelId: String, effort: String) {
        val updated = _ui.value.settings.copy(
            modelOverride = modelId.trim(),
            effort = effort.trim()
        )
        LocalStore.saveSettings(ctx, updated)
        val label = modelId.ifBlank { "Default (agy)" } + if (effort.isNotBlank()) " · $effort" else ""
        _ui.update { it.copy(settings = updated, snackbarMessage = "Model: $label") }
    }

    fun loadProjects() {
        viewModelScope.launch {
            try {
                val port = _ui.value.settings.port
                val list = BridgeClient.listProjects(port)
                val lastSlug = _ui.value.activeProject?.slug ?: LocalStore.loadLastProject(ctx)
                val selected = list.firstOrNull { it.slug == lastSlug } ?: list.firstOrNull()
                val msgs = if (selected != null) LocalStore.loadMessages(ctx, selected.slug) else emptyList()
                _ui.update {
                    it.copy(
                        projects = list,
                        activeProject = selected,
                        messages = msgs
                    )
                }
                if (selected != null) {
                    LocalStore.saveLastProject(ctx, selected.slug)
                }
            } catch (e: Exception) {
                AppLog.log("PROJECTS", "Failed to load projects", e)
            }
        }
    }

    fun selectProject(project: ProjectInfo) {
        LocalStore.saveLastProject(ctx, project.slug)
        val msgs = LocalStore.loadMessages(ctx, project.slug)
        _ui.update {
            it.copy(
                activeProject = project,
                messages = msgs,
                projectFiles = emptyList(),
                currentRules = ""
            )
        }
    }

    fun createProject(name: String, template: ProjectTemplate, customRules: String) {
        viewModelScope.launch {
            try {
                val port = _ui.value.settings.port
                val rules = customRules.ifBlank { template.defaultRules }
                val created = BridgeClient.createProject(port, name.trim(), rules)
                val updated = listOf(created) + _ui.value.projects.filterNot { it.slug == created.slug }
                LocalStore.saveLastProject(ctx, created.slug)
                _ui.update {
                    it.copy(
                        projects = updated,
                        activeProject = created,
                        messages = emptyList(),
                        snackbarMessage = "Created project: ${created.name}"
                    )
                }
            } catch (e: Exception) {
                AppLog.log("PROJECTS", "Create failed", e)
                showNotice("Failed to create project: ${e.message}")
            }
        }
    }

    fun deleteProject(project: ProjectInfo) {
        viewModelScope.launch {
            try {
                val port = _ui.value.settings.port
                BridgeClient.deleteProject(port, project.slug)
                LocalStore.clearMessages(ctx, project.slug)
                val remaining = _ui.value.projects.filterNot { it.slug == project.slug }
                val next = if (_ui.value.activeProject?.slug == project.slug) remaining.firstOrNull() else _ui.value.activeProject
                val msgs = if (next != null) LocalStore.loadMessages(ctx, next.slug) else emptyList()
                LocalStore.saveLastProject(ctx, next?.slug)
                _ui.update {
                    it.copy(
                        projects = remaining,
                        activeProject = next,
                        messages = msgs,
                        snackbarMessage = "Project deleted"
                    )
                }
            } catch (e: Exception) {
                AppLog.log("PROJECTS", "Delete failed", e)
                showNotice("Failed to delete project: ${e.message}")
            }
        }
    }

    fun clearActiveChat() {
        val p = _ui.value.activeProject ?: return
        LocalStore.clearMessages(ctx, p.slug)
        _ui.update {
            it.copy(
                messages = emptyList(),
                activeProject = p.copy(hasSession = false),
                snackbarMessage = "Started a fresh session"
            )
        }
        viewModelScope.launch {
            BridgeClient.resetSession(_ui.value.settings.port, p.slug)
        }
    }

    fun loadActiveRules() {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                val txt = BridgeClient.getRules(_ui.value.settings.port, p.slug)
                _ui.update { it.copy(currentRules = txt) }
            } catch (e: Exception) {
                showNotice("Failed to load GEMINI.md: ${e.message}")
            }
        }
    }

    fun saveActiveRules(rulesText: String) {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                BridgeClient.saveRules(_ui.value.settings.port, p.slug, rulesText)
                _ui.update { it.copy(currentRules = rulesText, snackbarMessage = "Saved project rules (GEMINI.md)") }
            } catch (e: Exception) {
                showNotice("Failed to save rules: ${e.message}")
            }
        }
    }

    fun loadActiveFiles() {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                val files = BridgeClient.listFiles(_ui.value.settings.port, p.slug)
                _ui.update { it.copy(projectFiles = files) }
            } catch (e: Exception) {
                showNotice("Failed to list files: ${e.message}")
            }
        }
    }

    fun uploadFileToActiveProject(fileName: String, bytes: ByteArray) {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                BridgeClient.uploadFile(_ui.value.settings.port, p.slug, fileName, bytes)
                showNotice("Uploaded $fileName to project")
                loadActiveFiles()
                loadProjects()
            } catch (e: Exception) {
                AppLog.log("UPLOAD", "Failed", e)
                showNotice("Upload failed: ${e.message}")
            }
        }
    }

    fun exportSingleFile(relPath: String, openDirectly: Boolean = true) {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                val artifact = BridgeClient.downloadSingleFile(
                    ctx,
                    _ui.value.settings.port,
                    p.slug,
                    relPath,
                    openDirectly = openDirectly
                )
                _ui.update {
                    it.copy(
                        lastExportedArtifact = artifact,
                        snackbarMessage = "Saved ${artifact.file.name} to Downloads/Aether"
                    )
                }
            } catch (e: Exception) {
                AppLog.log("EXPORT", "Single file export failed", e)
                showNotice("Download failed: ${e.message}")
            }
        }
    }

    fun exportActiveProjectZip() {
        val p = _ui.value.activeProject ?: return
        viewModelScope.launch {
            try {
                val artifact = BridgeClient.downloadProjectZip(ctx, _ui.value.settings.port, p.slug)
                _ui.update {
                    it.copy(
                        lastExportedArtifact = artifact,
                        snackbarMessage = "Saved ${artifact.file.name} to Downloads/Aether"
                    )
                }
            } catch (e: Exception) {
                AppLog.log("EXPORT", "ZIP export failed", e)
                showNotice("ZIP export failed: ${e.message}")
            }
        }
    }

    fun stopStreaming() {
        val slug = _ui.value.activeProject?.slug
        streamJob?.cancel()
        streamJob = null
        _ui.update {
            it.copy(
                isStreaming = false,
                activeToolStatus = "",
                snackbarMessage = "Stopped generation"
            )
        }
        if (slug != null) {
            viewModelScope.launch {
                BridgeClient.cancelChat(_ui.value.settings.port, slug)
            }
        }
    }

    fun sendPrompt(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty() || _ui.value.isStreaming) return

        streamJob = viewModelScope.launch {
            val port = _ui.value.settings.port
            var project = _ui.value.activeProject
            if (project == null) {
                try {
                    project = BridgeClient.createProject(port, "General", ProjectTemplate.CODING.defaultRules)
                    _ui.update {
                        it.copy(
                            projects = listOf(project) + it.projects,
                            activeProject = project
                        )
                    }
                    LocalStore.saveLastProject(ctx, project.slug)
                } catch (e: Exception) {
                    showNotice("Bridge offline. Open Environment Setup first.")
                    return@launch
                }
            }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                text = clean
            )
            val assistantId = UUID.randomUUID().toString()
            val assistantPlaceholder = ChatMessage(
                id = assistantId,
                role = "assistant",
                text = ""
            )

            val initialList = _ui.value.messages + userMsg + assistantPlaceholder
            _ui.update {
                it.copy(
                    messages = initialList,
                    isStreaming = true,
                    activeToolStatus = ""
                )
            }
            LocalStore.saveMessages(ctx, project.slug, initialList)

            val buf = StringBuilder()
            val tools = mutableListOf<String>()
            var hadError = false

            try {
                BridgeClient.streamChat(
                    port = port,
                    slug = project.slug,
                    prompt = clean,
                    settings = _ui.value.settings,
                    onDelta = { chunk ->
                        buf.append(chunk)
                        val currentText = buf.toString()
                        _ui.update { state ->
                            state.copy(
                                messages = state.messages.map { m ->
                                    if (m.id == assistantId) m.copy(text = currentText) else m
                                }
                            )
                        }
                    },
                    onTool = { toolName ->
                        if (toolName.isNotBlank() && toolName !in tools) tools.add(toolName)
                        val snapshotTools = tools.toList()
                        _ui.update { state ->
                            state.copy(
                                activeToolStatus = toolName,
                                messages = state.messages.map { m ->
                                    if (m.id == assistantId) m.copy(toolEvents = snapshotTools) else m
                                }
                            )
                        }
                    },
                    onDone = { changedFiles, hasSession ->
                        val finalText = buf.toString().ifBlank {
                            if (hadError) "" else "Done."
                        }
                        _ui.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == assistantId) {
                                    m.copy(
                                        text = if (m.text.isBlank()) finalText else m.text,
                                        changedFiles = changedFiles,
                                        toolEvents = tools.toList()
                                    )
                                } else m
                            }
                            LocalStore.saveMessages(ctx, project.slug, updated)
                            val updatedProject = state.activeProject?.let {
                                if (it.slug == project.slug) it.copy(hasSession = hasSession) else it
                            }
                            state.copy(
                                messages = updated,
                                activeProject = updatedProject,
                                isStreaming = false,
                                activeToolStatus = ""
                            )
                        }
                        if (changedFiles.isNotEmpty()) {
                            loadProjects()
                        }
                    },
                    onError = { errText ->
                        hadError = true
                        buf.append(if (buf.isNotEmpty()) "\n\n$errText" else errText)
                        val errFinal = buf.toString()
                        _ui.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == assistantId) m.copy(text = errFinal, isError = true) else m
                            }
                            LocalStore.saveMessages(ctx, project.slug, updated)
                            state.copy(
                                messages = updated,
                                isStreaming = false,
                                activeToolStatus = ""
                            )
                        }
                    }
                )
            } catch (_: CancellationException) {
                val partial = buf.toString().ifBlank { "(Stopped)" }
                _ui.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == assistantId) m.copy(text = partial) else m
                    }
                    LocalStore.saveMessages(ctx, project.slug, updated)
                    state.copy(messages = updated, isStreaming = false, activeToolStatus = "")
                }
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        LocalStore.saveSettings(ctx, newSettings)
        _ui.update { it.copy(settings = newSettings, snackbarMessage = "Settings saved") }
    }
}
