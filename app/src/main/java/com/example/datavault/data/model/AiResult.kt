package com.example.datavault.data.model

sealed class AiResult<out T> {
    data class Success<T>(val data: T) : AiResult<T>()
    data class Error(val message: String, val cause: Exception? = null) : AiResult<Nothing>()
    object Loading : AiResult<Nothing>()
}
