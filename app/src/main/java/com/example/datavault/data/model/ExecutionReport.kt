package com.example.datavault.data.model

data class ExecutionReport(
    val successful: List<FileMove>,
    val failed: List<FailedOperation>,
    val foldersCreated: List<String>,
    val undoSnapshot: UndoSnapshot
)

data class FailedOperation(val move: FileMove, val reason: String)
data class UndoSnapshot(val operations: List<UndoOp>)
data class UndoOp(val currentPath: String, val originalPath: String)
