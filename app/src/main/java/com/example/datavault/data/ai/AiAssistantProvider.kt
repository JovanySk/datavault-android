package com.example.datavault.data.ai

import com.example.datavault.data.files.FileOperationsManager
import com.example.datavault.data.network.AiClient

object AiAssistantProvider {
    val apiService by lazy { AiClient.apiService }
    val extractor by lazy { FileContentExtractor() }
    val assistant by lazy { AiFileAssistant(apiService, extractor = extractor) }
    val fileOperationsManager by lazy { FileOperationsManager() }
}
