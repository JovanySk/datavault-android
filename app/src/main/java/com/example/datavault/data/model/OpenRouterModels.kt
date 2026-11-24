package com.example.datavault.data.model

data class ChatRequest(
    val model: String = MODEL,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 2048,
    val temperature: Float = 0.3f
) {
    companion object {
        const val MODEL = "meta-llama/llama-3.1-8b-instruct"
        const val MODEL_FALLBACK = "meta-llama/llama-3.1-8b-instruct"
        const val MODEL_VISION = "google/gemini-2.0-flash-exp:free"
    }
}

data class ChatMessage(val role: String, val content: Any)

data class ChatResponse(val choices: List<Choice>)

data class Choice(val message: ChatMessage)
