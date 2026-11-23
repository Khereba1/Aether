package com.khereba1.app.data

import android.content.Context
import com.khereba1.app.AppLog
import com.khereba1.app.PREFS
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LocalStore {

    fun loadSettings(context: Context): AppSettings {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            port = sp.getInt("port", BridgeScripts.DEFAULT_PORT),
            cliEngine = CliEngine.fromId(sp.getString("cli_engine", CliEngine.ANTIGRAVITY.id)),
            customBinary = sp.getString("custom_bin", "") ?: "",
            modelOverride = sp.getString("model_override", "") ?: "",
            themeMode = sp.getString("theme_mode", "system") ?: "system",
            language = sp.getString("lang", "system") ?: "system"
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("port", settings.port)
            .putString("cli_engine", settings.cliEngine.id)
            .putString("custom_bin", settings.customBinary)
            .putString("model_override", settings.modelOverride)
            .putString("theme_mode", settings.themeMode)
            .putString("lang", settings.language)
            .apply()
    }

    fun loadLastProject(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_project", null)

    fun saveLastProject(context: Context, slug: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_project", slug)
            .apply()
    }

    private fun chatFile(context: Context, slug: String): File {
        val dir = File(context.filesDir, "chats").apply { mkdirs() }
        val safe = slug.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return File(dir, "$safe.json")
    }

    fun loadMessages(context: Context, slug: String): List<ChatMessage> {
        val f = chatFile(context, slug)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            val out = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tools = mutableListOf<String>()
                val tArr = o.optJSONArray("tools")
                if (tArr != null) for (j in 0 until tArr.length()) tools.add(tArr.optString(j))
                val files = mutableListOf<String>()
                val fArr = o.optJSONArray("files")
                if (fArr != null) for (j in 0 until fArr.length()) files.add(fArr.optString(j))
                out.add(
                    ChatMessage(
                        id = o.optString("id"),
                        role = o.optString("role"),
                        text = o.optString("text"),
                        timestamp = o.optLong("ts", 0L),
                        toolEvents = tools,
                        changedFiles = files,
                        isError = o.optBoolean("err", false)
                    )
                )
            }
            out
        } catch (e: Exception) {
            AppLog.log("STORE", "Failed to read chats for $slug", e)
            emptyList()
        }
    }

    fun saveMessages(context: Context, slug: String, messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            for (m in messages) {
                val o = JSONObject()
                    .put("id", m.id)
                    .put("role", m.role)
                    .put("text", m.text)
                    .put("ts", m.timestamp)
                    .put("err", m.isError)
                    .put("tools", JSONArray(m.toolEvents))
                    .put("files", JSONArray(m.changedFiles))
                arr.put(o)
            }
            chatFile(context, slug).writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLog.log("STORE", "Failed to save chats for $slug", e)
        }
    }

    fun clearMessages(context: Context, slug: String) {
        try {
            chatFile(context, slug).delete()
        } catch (_: Exception) {
        }
    }
}
