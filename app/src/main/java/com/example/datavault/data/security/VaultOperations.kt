package com.example.datavault.data.security

import android.util.Log

import android.content.Context
import com.example.datavault.data.local.AppDatabase
import com.example.datavault.data.local.entity.VaultFile
import com.example.datavault.data.storage.FileOperations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class VaultOperations(private val context: Context) {
    private val cryptoManager = CryptoManager(context)
    private val vaultDao = AppDatabase.getDatabase(context).vaultFileDao()
    private val fileOps = FileOperations()

    private val vaultDir = File(context.filesDir, "vault").apply {
        if (!exists()) mkdirs()
    }

    suspend fun moveToVault(originalPath: String): Boolean {
        return try {
            val originalFile = File(originalPath)
            Log.d("VAULT", "Intentando mover: $originalPath")
            Log.d("VAULT", "Archivo existe: ${originalFile.exists()}, isFile: ${originalFile.isFile}")

            if (!originalFile.exists() || !originalFile.isFile) {
                Log.e("VAULT", "Archivo no encontrado o es directorio")
                return false
            }

            val encryptedFileName = UUID.randomUUID().toString() + ".enc"
            val encryptedFile = File(vaultDir, encryptedFileName)
            Log.d("VAULT", "Destino encriptado: ${encryptedFile.absolutePath}")

            FileInputStream(originalFile).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    cryptoManager.encryptStream(input, output)
                }
            }
            Log.d("VAULT", "Encriptación OK, tamaño: ${encryptedFile.length()}")

            val vaultFile = VaultFile(
                originalPath = originalPath,
                encryptedFileName = encryptedFileName,
                originalFileName = originalFile.name,
                mimeType = fileOps.getMimeType(originalPath) ?: "application/octet-stream",
                fileSize = originalFile.length()
            )
            vaultDao.insert(vaultFile)
            Log.d("VAULT", "Insertado en Room OK")

            val deleted = originalFile.delete()
            Log.d("VAULT", "Original eliminado: $deleted")
            true
        } catch (e: Exception) {
            Log.e("VAULT", "Error en moveToVault: ${e.message}", e)
            false
        }
    }

    suspend fun restoreFromVault(vaultFile: VaultFile): Boolean {
        return try {
            val encryptedFile = File(vaultDir, vaultFile.encryptedFileName)
            Log.d("VAULT", "Restaurando: ${vaultFile.originalFileName}, id=${vaultFile.id}")
            Log.d("VAULT", "Archivo enc existe: ${encryptedFile.exists()}, path: ${encryptedFile.absolutePath}")

            if (!encryptedFile.exists()) {
                Log.e("VAULT", "Archivo encriptado no encontrado")
                return false
            }

            val restoredFile = File(vaultFile.originalPath)
            restoredFile.parentFile?.mkdirs()
            Log.d("VAULT", "Destino restauración: ${restoredFile.absolutePath}")

            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(restoredFile).use { output ->
                    cryptoManager.decryptStream(input, output)
                }
            }
            Log.d("VAULT", "Descifrado OK, tamaño restaurado: ${restoredFile.length()}")

            vaultDao.delete(vaultFile)
            Log.d("VAULT", "Room delete OK")

            val deleted = encryptedFile.delete()
            Log.d("VAULT", "Enc eliminado: $deleted")
            true
        } catch (e: Exception) {
            Log.e("VAULT", "Error restore: ${e.message}", e)
            false
        }
    }
    
    suspend fun decryptToTemp(vaultFile: VaultFile): File? {
        return try {
            val encryptedFile = File(vaultDir, vaultFile.encryptedFileName)
            if (!encryptedFile.exists()) return null

            val tempDir = File(context.cacheDir, "vault_temp").apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File(tempDir, vaultFile.originalFileName)

            val inputStream = FileInputStream(encryptedFile)
            val outputStream = FileOutputStream(tempFile)
            cryptoManager.decryptStream(inputStream, outputStream)
            
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun clearTempFiles() {
        val tempDir = File(context.cacheDir, "vault_temp")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}
