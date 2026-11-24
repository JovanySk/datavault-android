package com.example.datavault.data.network

import com.example.datavault.data.model.ChatRequest
import com.example.datavault.data.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AiApiService {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "com.example.datavault",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}
