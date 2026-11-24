package com.example.datavault.data.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository {

    companion object {
        private val API_KEY = com.example.datavault.BuildConfig.GEMINI_API_KEY
        private const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
        private const val MAX_TEXT_BYTES = 12_000
    }

    // ─── Core send function ───────────────────────────────────────────────
    private suspend fun sendPrompt(prompt: String, imagePath: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = URL("$API_URL?key=$API_KEY")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val parts = JSONArray()

            // Attach image if provided and file is small enough
            if (imagePath != null) {
                val imgFile = File(imagePath)
                if (imgFile.exists() && imgFile.length() < 4 * 1024 * 1024) {
                    val mimeType = getMimeByExtension(imgFile.extension)
                    if (mimeType.startsWith("image/")) {
                        val bytes = imgFile.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        parts.put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", b64)
                            })
                        })
                    }
                }
            }

            // Text part
            parts.put(JSONObject().apply {
                put("text", prompt)
            })

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", parts)
                    })
                })
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val raw = if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "Error $code"
            }

            if (code != 200) {
                return@withContext try {
                    val errJson = JSONObject(raw).getJSONObject("error")
                    val message = errJson.getString("message")
                    if (code == 429) {
                        "Límite de cuota excedido para la API Key. Por favor intenta más tarde o usa otra cuenta."
                    } else {
                        "Error API ($code): $message"
                    }
                } catch (e: Exception) {
                    "Error de conexión ($code). Por favor intenta más tarde."
                }
            }

            val json = JSONObject(raw)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }

    // ─── Public API methods ───────────────────────────────────────────────

    /** Ask any question about a specific file */
    suspend fun analyzeFile(
        fileName: String,
        filePath: String,
        mimeType: String,
        fileSize: Long,
        modifiedDate: String,
        userQuestion: String
    ): String {
        val fileContent = extractTextContent(filePath, mimeType)
        val isImage = mimeType.startsWith("image/")

        val prompt = """
Eres una IA integrada en un gestor de archivos para Android.
Tu tarea es ayudar al usuario a analizar, resumir, buscar, renombrar y organizar archivos.

Información del archivo:
- Nombre: $fileName
- Ruta: $filePath
- Tipo/MIME: $mimeType
- Tamaño: ${formatSize(fileSize)}
- Fecha de modificación: $modifiedDate
- Contenido extraído del archivo:
${if (fileContent.isNotEmpty()) fileContent else "(Archivo binario o sin contenido de texto extraíble)"}

Instrucción del usuario:
$userQuestion

Reglas:
1. Responde de forma clara, breve y útil.
2. Si el usuario pide un resumen, resume el contenido del archivo.
3. Si pide organizar archivos, sugiere una carpeta adecuada.
4. Si pide renombrar, propone un nombre corto, descriptivo y sin caracteres inválidos.
5. Si el contenido no es suficiente, dilo claramente.
6. No inventes información que no aparezca en el archivo.
7. Si el archivo parece sensible o privado, advierte al usuario antes de sugerir compartirlo.

Responde en español.
        """.trimIndent()

        return sendPrompt(prompt, if (isImage) filePath else null)
    }

    /** Suggest how to organize a list of files into folders */
    suspend fun suggestOrganization(fileList: List<String>): String {
        val prompt = """
Eres una IA integrada en un gestor de archivos para Android.

Analiza esta lista de archivos y sugiere cómo organizarlos en carpetas.

Archivos:
${fileList.joinToString("\n") { "- $it" }}

Devuelve el resultado en este formato para cada archivo:

Archivo: nombre.ext
Carpeta sugerida: carpeta/subcarpeta
Razón: explicación breve

---

Responde en español.
        """.trimIndent()

        return sendPrompt(prompt)
    }

    /** Suggest a better name for a file */
    suspend fun suggestRename(fileName: String, filePath: String, mimeType: String): String {
        val fileContent = extractTextContent(filePath, mimeType)
        val prompt = """
Sugiere un nuevo nombre para este archivo.

Nombre actual: $fileName
Contenido: ${if (fileContent.isNotEmpty()) fileContent.take(2000) else "(sin contenido de texto)"}

Reglas:
- Máximo 50 caracteres.
- Mantén la extensión original (${fileName.substringAfterLast('.', "")}).
- No uses / \\ : * ? " < > |
- Que sea claro y descriptivo.
- Solo responde con el nombre sugerido, sin explicaciones adicionales.

Responde en español.
        """.trimIndent()

        return sendPrompt(prompt)
    }

    /** General chat — user can ask anything about their files */
    suspend fun chat(message: String, context: String = ""): String {
        val prompt = """
Eres una IA integrada en un gestor de archivos Android llamado DataVault.
Ayudas al usuario a gestionar, analizar y organizar sus archivos.
${if (context.isNotEmpty()) "Contexto actual: $context" else ""}

Pregunta del usuario: $message

Responde de forma concisa y útil en español.
        """.trimIndent()

        return sendPrompt(prompt)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun extractTextContent(filePath: String, mimeType: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return ""
            if (file.length() > 50 * 1024 * 1024) return "(archivo muy grande para leer)"

            val textMimes = listOf("text/", "application/json", "application/xml",
                "application/javascript", "application/x-sh")
            val textExtensions = setOf("txt", "md", "csv", "json", "xml", "html", "htm",
                "kt", "java", "py", "js", "ts", "css", "sql", "log", "yaml", "yml",
                "ini", "cfg", "conf", "sh", "bat", "gradle", "properties")

            val ext = file.extension.lowercase()
            val isText = textMimes.any { mimeType.startsWith(it) } || ext in textExtensions

            if (isText) {
                val bytes = file.readBytes()
                val text = bytes.take(MAX_TEXT_BYTES).toByteArray().toString(Charsets.UTF_8)
                if (bytes.size > MAX_TEXT_BYTES) "$text\n...(contenido recortado)" else text
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMimeByExtension(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "application/octet-stream"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
