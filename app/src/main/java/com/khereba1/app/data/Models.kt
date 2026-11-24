package com.khereba1.app.data

enum class CliEngine(val id: String, val binary: String, val label: String) {
    ANTIGRAVITY("antigravity", "agy", "Antigravity CLI (agy)"),
    GEMINI("gemini", "gemini", "Gemini CLI (API Key)"),
    CLAUDE("claude", "claude", "Claude Code"),
    CUSTOM("custom", "", "Custom CLI");

    companion object {
        fun fromId(id: String?): CliEngine = entries.firstOrNull { it.id == id } ?: ANTIGRAVITY
    }
}

enum class ProjectTemplate(val id: String, val labelAr: String, val labelEn: String, val defaultRules: String) {
    CODING(
        id = "coding",
        labelAr = "برمجة وتطوير",
        labelEn = "Coding & Dev",
        defaultRules = """# Project Rules
- Explain everything concisely in Egyptian Arabic, keeping technical terms in English.
- Only read and edit files inside this project workspace folder.
- Always verify syntax before finishing and list any files created or modified.
"""
    ),
    SUMMARY(
        id = "summary",
        labelAr = "ملخصات ودراسة",
        labelEn = "Summaries & Study",
        defaultRules = """# Summary Style Rules
- Write clear, structured summaries in Arabic (keep medical/scientific/technical terms in English).
- Always organize every summary into:
  1. الفكرة الرئيسية (Core Idea)
  2. أهم النقاط (Key Points)
  3. أسئلة مراجعة سريعة (Quick Review Questions)
- Keep paragraphs short and easy to study.
"""
    ),
    BLANK(
        id = "blank",
        labelAr = "مشروع فاضي",
        labelEn = "Blank Workspace",
        defaultRules = """# Workspace Instructions
- Work strictly inside this folder.
- Respond concisely in Egyptian Arabic with English technical terms.
"""
    )
}

data class ModelOption(
    val id: String,
    val label: String
)

data class ProjectInfo(
    val slug: String,
    val name: String,
    val fileCount: Int = 0,
    val hasRules: Boolean = false,
    val hasSession: Boolean = false,
    val updatedAt: Long = 0L
)

data class WorkspaceFile(
    val name: String,
    val sizeBytes: Long,
    val isDir: Boolean
)

data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolEvents: List<String> = emptyList(),
    val changedFiles: List<String> = emptyList(),
    val isError: Boolean = false
)

data class BridgeHealth(
    val reachable: Boolean = false,
    val ubuntuReady: Boolean = false,
    val pythonVersion: String = "",
    val bridgeVersion: String = "",
    val clis: Map<String, Boolean> = emptyMap(),
    val workspacesDir: String = ""
)

data class SetupStatus(
    val termuxInstalled: Boolean = false,
    val permissionGranted: Boolean = false,
    val health: BridgeHealth = BridgeHealth(),
    val checking: Boolean = false,
    val statusNote: String = ""
) {
    val isReadyForChat: Boolean
        get() = termuxInstalled && health.reachable
}

data class AppSettings(
    val port: Int = 8765,
    val cliEngine: CliEngine = CliEngine.ANTIGRAVITY,
    val customBinary: String = "",
    val modelOverride: String = "",
    val effort: String = "", // "", "low", "medium", "high"
    val themeMode: String = "system", // "system", "light", "dark"
    val language: String = "en"
)
