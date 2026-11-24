package com.example.datavault.data.security

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(private val context: Context) {

    private val keyFile = File(context.filesDir, "vault/.key")

    private fun getOrCreateKey(): SecretKey {
        if (!keyFile.exists()) {
            keyFile.parentFile?.mkdirs()
            val keyBytes = ByteArray(32) // AES-256
            SecureRandom().nextBytes(keyBytes)
            keyFile.writeBytes(keyBytes)
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    fun encryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val key = getOrCreateKey()
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        }

        outputStream.write(iv)

        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int

        inputStream.use { input ->
            outputStream.use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val encrypted = cipher.update(buffer, 0, bytesRead)
                    if (encrypted != null) output.write(encrypted)
                }
                val final = cipher.doFinal()
                if (final != null) output.write(final)
            }
        }
    }

    fun decryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val key = getOrCreateKey()

        inputStream.use { input ->
            outputStream.use { output ->
                val iv = ByteArray(16)
                input.read(iv)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                }

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val decrypted = cipher.update(buffer, 0, bytesRead)
                    if (decrypted != null) output.write(decrypted)
                }
                val final = cipher.doFinal()
                if (final != null) output.write(final)
            }
        }
    }
}
