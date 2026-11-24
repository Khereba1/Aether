package com.khereba1.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import com.khereba1.app.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DownloadedArtifact(
    val file: File,
    val mimeType: String,
    val openDirectly: Boolean = false
)

data class ModelsResponse(
    val models: List<ModelOption>,
    val currentModel: String
)

object BridgeClient {

    private fun baseUrl(port: Int) = "http://127.0.0.1:$port"

    private fun enc(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    suspend fun checkHealth(port: Int): BridgeHealth = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${baseUrl(port)}/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 3000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: "{}"
            conn.disconnect()
            if (code !in 200..299) return@withContext BridgeHealth(reachable = false)
            val json = JSONObject(body)
            val cliObj = json.optJSONObject("clis") ?: JSONObject()
            val clis = mutableMapOf<String, Boolean>()
            val keys = cliObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                clis[k] = cliObj.optBoolean(k, false)
            }
            BridgeHealth(
                reachable = json.optBoolean("ok", false),
                ubuntuReady = json.optBoolean("ubuntu", false),
                pythonVersion = json.optString("python", ""),
                bridgeVersion = json.optString("bridge_version", ""),
                clis = clis,
                workspacesDir = json.optString("workspaces_dir", "")
            )
        } catch (e: Exception) {
            BridgeHealth(reachable = false)
        }
    }

    suspend fun listModels(port: Int): ModelsResponse = withContext(Dispatchers.IO) {
        try {
            val text = requestJson(port, "GET", "/models", null)
            val json = JSONObject(text)
            val arr = json.optJSONArray("models")
            val out = mutableListOf<ModelOption>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val label = o.optString("label", id)
                    if (id.isNotBlank()) out.add(ModelOption(id = id, label = label))
                }
            }
            ModelsResponse(out, json.optString("current", ""))
        } catch (e: Exception) {
            AppLog.log("MODELS", "Failed to fetch models: ${e.message}")
            ModelsResponse(emptyList(), "")
        }
    }

    suspend fun listProjects(port: Int): List<ProjectInfo> = withContext(Dispatchers.IO) {
        val text = requestJson(port, "GET", "/projects", null)
        val arr = JSONObject(text).optJSONArray("projects") ?: return@withContext emptyList()
        val out = mutableListOf<ProjectInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ProjectInfo(
                    slug = o.optString("slug"),
                    name = o.optString("name"),
                    fileCount = o.optInt("file_count", 0),
                    hasRules = o.optBoolean("has_rules", false),
                    hasSession = o.optBoolean("has_session", false),
                    updatedAt = o.optLong("updated_at", 0L)
                )
            )
        }
        out
    }

    suspend fun createProject(port: Int, name: String, rules: String): ProjectInfo = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("name", name).put("rules", rules)
        val o = JSONObject(requestJson(port, "POST", "/projects", payload))
        ProjectInfo(
            slug = o.optString("slug"),
            name = o.optString("name", name),
            fileCount = o.optInt("file_count", 0),
            hasRules = o.optBoolean("has_rules", false),
            hasSession = o.optBoolean("has_session", false),
            updatedAt = o.optLong("updated_at", System.currentTimeMillis())
        )
    }

    suspend fun deleteProject(port: Int, slug: String): Boolean = withContext(Dispatchers.IO) {
        val text = requestJson(port, "DELETE", "/projects/${enc(slug)}", null)
        JSONObject(text).optBoolean("ok", false)
    }

    suspend fun resetSession(port: Int, slug: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = requestJson(port, "POST", "/projects/${enc(slug)}/reset", JSONObject())
            JSONObject(text).optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun cancelChat(port: Int, slug: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("project", slug)
            val text = requestJson(port, "POST", "/chat/cancel", payload)
            JSONObject(text).optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getRules(port: Int, slug: String): String = withContext(Dispatchers.IO) {
        val text = requestJson(port, "GET", "/projects/${enc(slug)}/rules", null)
        JSONObject(text).optString("rules", "")
    }

    suspend fun saveRules(port: Int, slug: String, rules: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("rules", rules)
        val text = requestJson(port, "POST", "/projects/${enc(slug)}/rules", payload)
        JSONObject(text).optBoolean("ok", false)
    }

    suspend fun listFiles(port: Int, slug: String): List<WorkspaceFile> = withContext(Dispatchers.IO) {
        val text = requestJson(port, "GET", "/projects/${enc(slug)}/files", null)
        val arr = JSONObject(text).optJSONArray("files") ?: return@withContext emptyList()
        val out = mutableListOf<WorkspaceFile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                WorkspaceFile(
                    name = o.optString("name"),
                    sizeBytes = o.optLong("size", 0L),
                    isDir = o.optBoolean("is_dir", false)
                )
            )
        }
        out
    }

    suspend fun uploadFile(port: Int, slug: String, fileName: String, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("filename", fileName)
                .put("data_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            val text = requestJson(port, "POST", "/projects/${enc(slug)}/upload", payload)
            JSONObject(text).optBoolean("ok", false)
        }

    suspend fun downloadSingleFile(
        context: Context,
        port: Int,
        slug: String,
        relPath: String,
        openDirectly: Boolean = true
    ): DownloadedArtifact = withContext(Dispatchers.IO) {
        val conn = (URL("${baseUrl(port)}/projects/${enc(slug)}/file?path=${enc(relPath)}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 60000
            requestMethod = "GET"
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            conn.disconnect()
            throw RuntimeException("File download failed: $err")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        val serverMime = conn.contentType?.substringBefore(";")?.trim()
        conn.disconnect()

        val fileName = File(relPath).name.ifBlank { "file.bin" }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = serverMime?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cacheFile = File(exportDir, fileName)
        cacheFile.writeBytes(bytes)
        saveToDownloads(context, fileName, mime, bytes)
        DownloadedArtifact(cacheFile, mime, openDirectly = openDirectly)
    }

    suspend fun downloadProjectZip(context: Context, port: Int, slug: String): DownloadedArtifact =
        withContext(Dispatchers.IO) {
            val conn = (URL("${baseUrl(port)}/projects/${enc(slug)}/zip").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 60000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                conn.disconnect()
                throw RuntimeException("ZIP download failed: $err")
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val zipName = "${slug}.zip"
            val cacheFile = File(exportDir, zipName)
            cacheFile.writeBytes(bytes)
            saveToDownloads(context, zipName, "application/zip", bytes)
            DownloadedArtifact(cacheFile, "application/zip", openDirectly = false)
        }

    private fun saveToDownloads(context: Context, fileName: String, mime: String, bytes: ByteArray) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Aether")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        } catch (e: Exception) {
            AppLog.log("EXPORT", "MediaStore copy skipped: ${e.message}")
        }
    }

    suspend fun streamChat(
        port: Int,
        slug: String,
        prompt: String,
        settings: AppSettings,
        onDelta: (String) -> Unit,
        onTool: (String) -> Unit,
        onDone: (List<String>, Boolean) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val payload = JSONObject()
                .put("project", slug)
                .put("prompt", prompt)
                .put("engine", settings.cliEngine.id)
                .put("custom_bin", settings.customBinary)
                .put("model", settings.modelOverride)
                .put("effort", settings.effort)
                .put("continue_session", true)

            conn = (URL("${baseUrl(port)}/chat").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 300_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                val msg = try {
                    JSONObject(errBody).optString("error", errBody)
                } catch (_: Exception) {
                    errBody
                }
                onError(msg)
                return@withContext
            }

            var finished = false
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isEmpty()) continue
                    try {
                        val ev = JSONObject(trimmed)
                        when (ev.optString("type")) {
                            "delta" -> onDelta(ev.optString("text"))
                            "tool" -> onTool(ev.optString("name"))
                            "error" -> onError(ev.optString("text"))
                            "done" -> {
                                finished = true
                                val arr = ev.optJSONArray("changed_files")
                                val changed = mutableListOf<String>()
                                if (arr != null) {
                                    for (i in 0 until arr.length()) changed.add(arr.optString(i))
                                }
                                onDone(changed, ev.optBoolean("has_session", false))
                            }
                        }
                    } catch (e: Exception) {
                        onDelta(trimmed + "\n")
                    }
                }
            }
            if (!finished) onDone(emptyList(), false)
        } catch (e: Exception) {
            AppLog.log("CHAT", "Stream failed", e)
            onError(e.message ?: "Connection lost")
        } finally {
            conn?.disconnect()
        }
    }

    private fun requestJson(port: Int, method: String, path: String, body: JSONObject?): String {
        val conn = (URL("${baseUrl(port)}$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 15000
            requestMethod = method
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (code !in 200..299) {
                throw RuntimeException(
                    try {
                        JSONObject(text).optString("error", "HTTP $code")
                    } catch (_: Exception) {
                        "HTTP $code: $text"
                    }
                )
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
