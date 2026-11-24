package com.example.datavault.data.model

import java.util.UUID

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFile: FileModel? = null,
    val actionPlan: AiActionPlan? = null
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
