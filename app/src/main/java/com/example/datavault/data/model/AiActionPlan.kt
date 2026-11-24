package com.example.datavault.data.model

sealed class AiActionPlan {
    data class RenamePlan(val file: FileModel, val newName: String, val reason: String) : AiActionPlan()
    data class MovePlan(val file: FileModel, val destinationPath: String, val reason: String) : AiActionPlan()
    data class OrganizePlan(
        val foldersToCreate: List<String>,
        val moves: List<FileMove>,
        val needsReview: List<FileReview>,
        val summary: String
    ) : AiActionPlan()
}

data class FileMove(val fileName: String, val destination: String, val reason: String)
data class FileReview(val fileName: String, val reason: String)
