package com.example.datavault.data.files

import com.example.datavault.data.model.AiActionPlan
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.ExecutionReport
import com.example.datavault.data.model.FailedOperation
import com.example.datavault.data.model.FileModel
import com.example.datavault.data.model.UndoOp
import com.example.datavault.data.model.UndoSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class FileOperationsManager {

    suspend fun executeOrganizePlan(
        plan: AiActionPlan.OrganizePlan,
        baseDir: File
    ): AiResult<ExecutionReport> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Iniciando executeOrganizePlan")
            val foldersCreated = mutableListOf<String>()
            
            // Paso 1: Crear carpetas
            for (folderPath in plan.foldersToCreate) {
                val folder = File(baseDir, folderPath)
                if (!folder.exists()) {
                    if (folder.mkdirs()) {
                        foldersCreated.add(folderPath)
                        Timber.d("Carpeta creada: %s", folderPath)
                    } else {
                        Timber.w("No se pudo crear carpeta: %s", folderPath)
                    }
                }
            }

            val successfulMoves = mutableListOf<com.example.datavault.data.model.FileMove>()
            val failedMoves = mutableListOf<FailedOperation>()
            val undoOps = mutableListOf<UndoOp>()

            // Paso 2: Mover archivos
            for (move in plan.moves) {
                val sourceFile = findFile(move.fileName, baseDir)
                if (sourceFile == null) {
                    failedMoves.add(FailedOperation(move, "Archivo no encontrado"))
                    Timber.w("Archivo no encontrado para mover: %s", move.fileName)
                    continue
                }

                val destinationDir = File(baseDir, move.destination)
                val destinationFile = getUniqueFile(File(destinationDir, sourceFile.name))

                undoOps.add(UndoOp(currentPath = destinationFile.absolutePath, originalPath = sourceFile.absolutePath))

                val success = moveFile(sourceFile, destinationFile)
                if (success) {
                    successfulMoves.add(move)
                    Timber.d("Movido exitosamente: %s -> %s", sourceFile.name, destinationFile.absolutePath)
                } else {
                    undoOps.removeLast() // No se movió
                    failedMoves.add(FailedOperation(move, "Fallo al mover el archivo"))
                    Timber.e("Fallo al mover: %s", sourceFile.name)
                }
            }

            val report = ExecutionReport(
                successful = successfulMoves,
                failed = failedMoves,
                foldersCreated = foldersCreated,
                undoSnapshot = UndoSnapshot(undoOps)
            )

            AiResult.Success(report)
        } catch (e: Exception) {
            Timber.e(e, "Error al ejecutar OrganizePlan")
            AiResult.Error("Error al organizar archivos: ${e.message}", e)
        }
    }

    suspend fun renameFile(file: FileModel, newName: String): AiResult<FileModel> = withContext(Dispatchers.IO) {
        try {
            val invalidChars = arrayOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|")
            for (c in invalidChars) {
                if (newName.contains(c)) {
                    return@withContext AiResult.Error("El nombre contiene caracteres inválidos")
                }
            }

            if (newName.length > 50) {
                return@withContext AiResult.Error("El nombre no debe exceder 50 caracteres")
            }

            val originalFile = File(file.absolutePath)
            if (!originalFile.exists()) {
                return@withContext AiResult.Error("El archivo original no existe")
            }

            val parentDir = originalFile.parentFile ?: return@withContext AiResult.Error("No se pudo determinar el directorio padre")
            
            // Determinar extensión original si el newName no la tiene o manejarla
            val finalName = if (newName.contains(".")) newName else "$newName.${file.extension}"
            var destinationFile = File(parentDir, finalName)

            destinationFile = getUniqueFile(destinationFile)

            if (originalFile.renameTo(destinationFile)) {
                Timber.d("Archivo renombrado exitosamente: %s", destinationFile.absolutePath)
                val updatedModel = file.copy(
                    name = destinationFile.name,
                    path = destinationFile.path,
                    absolutePath = destinationFile.absolutePath,
                    size = destinationFile.length(),
                    lastModified = destinationFile.lastModified(),
                    extension = destinationFile.extension
                )
                AiResult.Success(updatedModel)
            } else {
                AiResult.Error("No se pudo renombrar el archivo por el sistema")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error en renameFile")
            AiResult.Error("Error al renombrar: ${e.message}", e)
        }
    }

    suspend fun moveFile(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = destination.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val finalDest = getUniqueFile(destination)

            if (source.renameTo(finalDest)) {
                return@withContext true
            }

            // Cross partition
            source.copyTo(finalDest, overwrite = false)
            if (source.delete()) {
                return@withContext true
            } else {
                // Mover falló a medias (copió pero no borró). Revertimos la copia.
                finalDest.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al mover el archivo")
            false
        }
    }

    fun findFile(name: String, searchRoot: File): File? {
        val ignoreList = listOf("Android", ".thumbnails", ".cache", "cache", ".trash")
        
        val queue = ArrayDeque<File>()
        queue.add(searchRoot)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isDirectory) {
                if (current.name in ignoreList) continue
                
                val children = current.listFiles()
                if (children != null) {
                    for (child in children) {
                        if (child.name.equals(name, ignoreCase = true)) {
                            return child
                        }
                        if (child.isDirectory) {
                            queue.add(child)
                        }
                    }
                }
            }
        }
        return null
    }

    suspend fun undo(snapshot: UndoSnapshot): AiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            var failedCount = 0
            for (op in snapshot.operations.reversed()) {
                val currentFile = File(op.currentPath)
                val originalFile = File(op.originalPath)

                if (currentFile.exists()) {
                    val originalDir = originalFile.parentFile
                    if (originalDir != null && !originalDir.exists()) {
                        originalDir.mkdirs()
                    }
                    val moved = moveFile(currentFile, originalFile)
                    if (!moved) failedCount++
                } else {
                    failedCount++
                }
            }
            if (failedCount > 0) {
                AiResult.Error("Deshacer completado con $failedCount errores")
            } else {
                AiResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al deshacer")
            AiResult.Error("Fallo general al deshacer: ${e.message}", e)
        }
    }

    suspend fun saveTextAsFile(
        content: String,
        originalFile: FileModel,
        suffix: String = "_resumen"
    ): AiResult<FileModel> = withContext(Dispatchers.IO) {
        try {
            val nameWithoutExt = originalFile.name.substringBeforeLast(".")
            var newFileName = "$nameWithoutExt$suffix.txt"
            val parentDir = File(originalFile.absolutePath).parentFile ?: return@withContext AiResult.Error("Directorio padre no encontrado")
            
            var destFile = File(parentDir, newFileName)
            if (destFile.exists()) {
                newFileName = "${nameWithoutExt}${suffix}_${System.currentTimeMillis()}.txt"
                destFile = File(parentDir, newFileName)
            }

            destFile.writeText(content, Charsets.UTF_8)
            Timber.d("Archivo de texto guardado en: %s", destFile.absolutePath)

            val updatedModel = FileModel(
                name = destFile.name,
                path = destFile.path,
                absolutePath = destFile.absolutePath,
                mimeType = "text/plain",
                size = destFile.length(),
                lastModified = destFile.lastModified(),
                isDirectory = false,
                extension = "txt"
            )

            AiResult.Success(updatedModel)
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar texto como archivo")
            AiResult.Error("No se pudo guardar el archivo: ${e.message}", e)
        }
    }

    private fun getUniqueFile(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile
        val name = file.nameWithoutExtension
        val ext = file.extension
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
        
        var counter = 1
        var newFile = File(parent, "$name($counter)$extSuffix")
        while (newFile.exists()) {
            counter++
            newFile = File(parent, "$name($counter)$extSuffix")
        }
        return newFile
    }
}
