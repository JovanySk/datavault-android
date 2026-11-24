package com.example.datavault.data.ai

import android.content.Context
import com.example.datavault.BuildConfig
import com.example.datavault.data.model.AiActionPlan
import com.example.datavault.data.model.AiMessage
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import com.example.datavault.data.model.ChatMessage
import com.example.datavault.data.model.ChatRequest
import com.example.datavault.data.model.MessageRole
import com.example.datavault.data.network.AiApiService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiFileAssistant(
    private val apiService: AiApiService,
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val extractor: FileContentExtractor
) {

    private val gson = Gson()

    companion object {
        val SYSTEM_PROMPT = """
Eres un asistente inteligente integrado en un gestor de archivos Android.
Tienes acceso completo a la información de archivos y PUEDES ejecutar acciones reales.
SIEMPRE pide confirmación antes de renombrar, mover o reorganizar archivos.
Personalidad: directo, útil, conciso. Sin relleno innecesario.
Idioma: español siempre.
REGLAS:
- Nunca inventes contenido que no esté en el archivo.
- Antes de cualquier acción: muestra el plan y espera confirmación.
- Archivos sensibles (médicos, financieros, contraseñas): advierte antes de sugerir moverlos.
- Nombres válidos: máx 50 chars, sin / \ : * ? " < > |
""".trimIndent()

        val ANALYZE_PROMPT = """
ARCHIVO: {fileName} | {mimeType} | {fileSize} | modificado: {modifiedDate}
CONTENIDO: {fileContent}

Identifica de qué trata este archivo en máximo 2 líneas.
Luego lista las acciones disponibles así (exactamente este formato):
ACCIONES_DISPONIBLES: resumir | parafrasear | sugerir_nombre | mover_a_carpeta | chat_libre
""".trimIndent()

        val ORGANIZE_PROMPT = """
Analiza estos archivos y crea un plan de organización.
ARCHIVOS: {fileList}

Responde ÚNICAMENTE con este JSON (sin markdown, sin texto extra):
{
  "foldersToCreate": ["ruta/carpeta"],
  "moves": [{"fileName": "nombre.ext", "destination": "ruta/destino", "reason": "razón corta"}],
  "needsReview": [{"fileName": "nombre.ext", "reason": "por qué"}],
  "summary": "resumen del plan en 2 líneas"
}
""".trimIndent()

        val RENAME_PROMPT = """
Sugiere 3 nombres para este archivo. Responde ÚNICAMENTE con este JSON:
{"suggestions": ["nombre1.ext", "nombre2.ext", "nombre3.ext"], "reasons": ["razón1", "razón2", "razón3"]}
Nombre actual: {fileName}
Contenido/contexto: {fileContent}
Reglas: máx 50 chars, sin / \ : * ? " < > |, mantén extensión, descriptivo.
""".trimIndent()

        val SUMMARIZE_PROMPT = """
Tarea: {task}
Nombre del archivo: {fileName}

Texto del documento:
---
{fileContent}
---

Instrucciones según la tarea:
- Si task es "resumir": Escribe un resumen claro en bullet points, máximo 150 palabras
- Si task es "parafrasear": Reescribe el contenido con otras palabras manteniendo el significado
- Si task es "puntos_clave": Lista los 10 puntos más importantes numerados
- Si task es "explicar": Explica el contenido de forma simple y clara

Responde directamente con el resultado, en español, sin preámbulos.
""".trimIndent()
    }

    private fun cleanJson(json: String): String {
        var clean = json.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json")
        }
        if (clean.startsWith("```")) {
            clean = clean.substringAfter("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.substringBeforeLast("```")
        }
        return clean.trim()
    }

    private suspend fun callOpenRouter(
        prompt: String,
        extractedContent: ExtractedContent? = null,
        history: List<ChatMessage>? = null,
        fallbackImagePrompt: String? = null
    ): AiResult<String> = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage(role = "system", content = SYSTEM_PROMPT))

            if (history != null) {
                messages.addAll(history)
            }

            var finalPrompt = prompt
            if (extractedContent?.type == ContentType.IMAGE_PDF) {
                finalPrompt += "\n[NOTA DEL SISTEMA: El archivo es un PDF escaneado o imagen, analiza solo por nombre y metadatos]."
            }

            var request: ChatRequest
            if (extractedContent != null && extractedContent.type == ContentType.IMAGE && extractedContent.base64 != null) {
                val imageMessage = listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,${extractedContent.base64}"
                        )
                    ),
                    mapOf(
                        "type" to "text", 
                        "text" to finalPrompt
                    )
                )
                messages.add(ChatMessage(role = "user", content = imageMessage))
                request = ChatRequest(model = ChatRequest.MODEL_VISION, messages = messages)
            } else {
                messages.add(ChatMessage(role = "user", content = finalPrompt))
                request = ChatRequest(model = ChatRequest.MODEL, messages = messages)
            }

            val authHeader = "Bearer $apiKey"
            timber.log.Timber.d("OpenRouter request - key length: \${apiKey.length}, url: chat/completions, model: \${request.model}")
            
            var response = apiService.chatCompletions(authorization = authHeader, request = request)

            if (!response.isSuccessful && response.code() == 404) {
                timber.log.Timber.d("OpenRouter request failed with 404. Retrying with fallback model.")
                if (extractedContent?.type == ContentType.IMAGE && fallbackImagePrompt != null) {
                    val fallbackMessages = mutableListOf<ChatMessage>()
                    fallbackMessages.add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
                    if (history != null) {
                        fallbackMessages.addAll(history)
                    }
                    fallbackMessages.add(ChatMessage(role = "user", content = fallbackImagePrompt))
                    request = ChatRequest(model = ChatRequest.MODEL, messages = fallbackMessages)
                    response = apiService.chatCompletions(authorization = authHeader, request = request)
                } else {
                    request = ChatRequest(model = ChatRequest.MODEL_FALLBACK, messages = messages)
                    response = apiService.chatCompletions(authorization = authHeader, request = request)
                }
            }

            if (response.isSuccessful) {
                val body = response.body()
                val text = body?.choices?.firstOrNull()?.message?.content
                if (text != null) {
                    AiResult.Success(text.toString())
                } else {
                    AiResult.Error("Respuesta vacía o formato inesperado de OpenRouter")
                }
            } else {
                val errBody = response.errorBody()?.string() ?: response.message()
                AiResult.Error("Error HTTP ${response.code()}: $errBody")
            }
        } catch (e: Exception) {
            AiResult.Error("Error de red: ${e.message}", e)
        }
    }

    suspend fun analyzeFile(file: FileModel, context: Context): AiResult<String> = withContext(Dispatchers.IO) {
        val extracted = extractor.extract(file, context)
        val contentStr = extracted.text ?: extracted.note ?: "Sin contenido extraíble"
        
        var prompt = ANALYZE_PROMPT
            .replace("{fileName}", file.name)
            .replace("{mimeType}", file.mimeType)
            .replace("{fileSize}", file.readableSize())
            .replace("{modifiedDate}", file.readableDate())
            .replace("{fileContent}", contentStr)

        var fallbackImagePrompt: String? = null
        if (extracted.type == ContentType.IMAGE) {
            prompt = "Analiza esta imagen y describe: qué contiene, qué texto visible hay, y qué tipo de archivo parece ser (foto personal, documento escaneado, captura de pantalla, etc). Responde en español."
            fallbackImagePrompt = "El archivo es una imagen llamada ${file.name} de ${file.readableSize()}.\n" +
                "Basándote SOLO en el nombre del archivo, infiere:\n" +
                "1. Qué podría contener esta imagen\n" +
                "2. De dónde podría venir (WhatsApp, cámara, captura, etc)\n" +
                "3. Sugerencia de carpeta donde organizarla\n" +
                "Responde en español."
        }

        callOpenRouter(prompt, extracted, null, fallbackImagePrompt)
    }

    suspend fun organizeFiles(files: List<FileModel>, context: Context): AiResult<AiActionPlan.OrganizePlan> = withContext(Dispatchers.IO) {
        val fileListStr = files.joinToString("\n") { "${it.name} (${it.mimeType}, ${it.readableSize()})" }
        val prompt = ORGANIZE_PROMPT.replace("{fileList}", fileListStr)

        when (val res = callOpenRouter(prompt)) {
            is AiResult.Success -> {
                try {
                    val jsonStr = cleanJson(res.data)
                    val plan = gson.fromJson(jsonStr, AiActionPlan.OrganizePlan::class.java)
                    AiResult.Success(plan)
                } catch (e: Exception) {
                    AiResult.Error("Fallo al interpretar el JSON de organización: ${e.message}")
                }
            }
            is AiResult.Error -> res
            AiResult.Loading -> AiResult.Loading
        }
    }

    suspend fun getRenamesuggestions(file: FileModel, context: Context): AiResult<List<String>> = withContext(Dispatchers.IO) {
        val extracted = extractor.extract(file, context)
        val contentStr = extracted.text ?: extracted.note ?: ""
        val prompt = RENAME_PROMPT
            .replace("{fileName}", file.name)
            .replace("{fileContent}", contentStr)

        var fallbackImagePrompt: String? = null
        if (extracted.type == ContentType.IMAGE) {
            fallbackImagePrompt = "El archivo es una imagen llamada ${file.name} de ${file.readableSize()}.\n" +
                "Basándote SOLO en el nombre del archivo, infiere:\n" +
                "1. Qué podría contener esta imagen\n" +
                "2. De dónde podría venir (WhatsApp, cámara, captura, etc)\n" +
                "3. Sugerencia de carpeta donde organizarla\n" +
                "Responde en español."
        }

        when (val res = callOpenRouter(prompt, extracted, null, fallbackImagePrompt)) {
            is AiResult.Success -> {
                try {
                    val jsonStr = cleanJson(res.data)
                    val type = object : TypeToken<Map<String, List<String>>>() {}.type
                    val map: Map<String, List<String>> = gson.fromJson(jsonStr, type)
                    val suggestions = map["suggestions"] ?: emptyList()
                    AiResult.Success(suggestions)
                } catch (e: Exception) {
                    AiResult.Error("Fallo al leer sugerencias de nombre: ${e.message}")
                }
            }
            is AiResult.Error -> res
            AiResult.Loading -> AiResult.Loading
        }
    }

    suspend fun summarize(file: FileModel, task: String, context: Context): AiResult<String> = withContext(Dispatchers.IO) {
        val extracted = extractor.extract(file, context)
        val contentStr = extracted.text ?: extracted.note ?: "Sin contenido extraíble"
        
        var prompt = SUMMARIZE_PROMPT
            .replace("{task}", task)
            .replace("{fileName}", file.name)
            .replace("{fileContent}", contentStr)

        var fallbackImagePrompt: String? = null
        if (extracted.type == ContentType.IMAGE) {
            prompt = "Analiza esta imagen y describe: qué contiene, qué texto visible hay, y qué tipo de archivo parece ser (foto personal, documento escaneado, captura de pantalla, etc). Responde en español."
            fallbackImagePrompt = "El archivo es una imagen llamada ${file.name} de ${file.readableSize()}.\n" +
                "Basándote SOLO en el nombre del archivo, infiere:\n" +
                "1. Qué podría contener esta imagen\n" +
                "2. De dónde podría venir (WhatsApp, cámara, captura, etc)\n" +
                "3. Sugerencia de carpeta donde organizarla\n" +
                "Responde en español."
        }

        callOpenRouter(prompt, extracted, null, fallbackImagePrompt)
    }

    suspend fun chat(userMessage: String, fileContext: FileModel?, history: List<AiMessage>, context: Context): AiResult<String> = withContext(Dispatchers.IO) {
        val chatHistory = mutableListOf<ChatMessage>()

        for (msg in history) {
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            if (msg.role != MessageRole.SYSTEM) {
                chatHistory.add(ChatMessage(role = role, content = msg.content))
            }
        }

        var finalPrompt = userMessage
        var extractedContentForChat: ExtractedContent? = null
        var fallbackImagePrompt: String? = null

        if (fileContext != null) {
            val extracted = extractor.extract(fileContext, context)
            val contentStr = extracted.text ?: extracted.note ?: ""
            finalPrompt = "CONTEXTO DEL ARCHIVO: ${fileContext.name}\n$contentStr\n\n$userMessage"
            
            extractedContentForChat = extracted
            if (extracted.type == ContentType.IMAGE) {
                fallbackImagePrompt = "El archivo es una imagen llamada ${fileContext.name} de ${fileContext.readableSize()}.\n" +
                    "Basándote SOLO en el nombre del archivo, infiere:\n" +
                    "1. Qué podría contener esta imagen\n" +
                    "2. De dónde podría venir (WhatsApp, cámara, captura, etc)\n" +
                    "3. Sugerencia de carpeta donde organizarla\n" +
                    "Responde en español.\n\n$userMessage"
            }
        }

        callOpenRouter(finalPrompt, extractedContentForChat, chatHistory, fallbackImagePrompt)
    }
}
