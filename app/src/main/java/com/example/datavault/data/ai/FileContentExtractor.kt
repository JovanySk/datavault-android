package com.example.datavault.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.datavault.data.model.FileModel
import java.io.ByteArrayOutputStream
import java.io.File
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.graphics.Movie
import android.media.MediaMetadataRetriever

enum class ContentType { TEXT, IMAGE, IMAGE_PDF, METADATA_ONLY }

data class ExtractedContent(
    val type: ContentType,
    val text: String? = null,
    val base64: String? = null,
    val note: String? = null
)

class FileContentExtractor {

    fun extract(file: FileModel, context: Context): ExtractedContent {
        val realFile = File(file.absolutePath)
        if (!realFile.exists() || file.isDirectory) {
            return ExtractedContent(ContentType.METADATA_ONLY, note = "Archivo no encontrado o es directorio")
        }

        return when {
            file.mimeType == "application/pdf" || file.extension.lowercase() == "pdf" -> {
                extractPdf(realFile)
            }
            file.mimeType.startsWith("text/") || file.mimeType == "application/json" -> {
                extractText(realFile)
            }
            file.mimeType.startsWith("image/gif") || file.extension.lowercase() == "gif" -> {
                extractImage(realFile)
            }
            file.mimeType.startsWith("image/") -> {
                extractImage(realFile)
            }
            file.mimeType.startsWith("video/") -> {
                extractVideo(realFile)
            }
            file.extension.lowercase() == "docx" -> {
                ExtractedContent(ContentType.METADATA_ONLY, note = "Contenido no extraíble, análisis por metadatos")
            }
            else -> {
                ExtractedContent(ContentType.METADATA_ONLY, note = "Tipo de archivo no soportado para extracción de contenido")
            }
        }
    }

    private fun extractPdf(file: File): ExtractedContent {
        return try {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(3, document.numberOfPages)
                val text = stripper.getText(document).trim()
                
                if (text.isBlank()) {
                    ExtractedContent(
                        type = ContentType.METADATA_ONLY,
                        note = "PDF sin texto extraíble (posiblemente escaneado o imagen)"
                    )
                } else {
                    val truncated = if (text.length > 3000) {
                        text.take(1500) + "\n...[texto truncado]...\n" + text.takeLast(500)
                    } else text
                    ExtractedContent(type = ContentType.TEXT, text = truncated)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Extractor", "Error leyendo PDF: ${e.message}")
            ExtractedContent(
                type = ContentType.METADATA_ONLY,
                note = "Error al leer el PDF: ${e.message}"
            )
        }
    }

    private fun extractText(file: File): ExtractedContent {
        return try {
            val fullText = file.inputStream().bufferedReader().use { it.readText() }
            if (fullText.length > 3000) {
                val start = fullText.take(1500)
                val end = fullText.takeLast(500)
                ExtractedContent(
                    type = ContentType.TEXT,
                    text = "$start\n\n...[truncado]...\n\n$end"
                )
            } else {
                ExtractedContent(ContentType.TEXT, text = fullText)
            }
        } catch (e: Exception) {
            ExtractedContent(ContentType.METADATA_ONLY, note = "No se pudo leer el archivo de texto")
        }
    }

    private fun extractImage(file: File): ExtractedContent {
        return try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (originalBitmap == null) {
                return ExtractedContent(type = ContentType.METADATA_ONLY, 
                    note = "No se pudo leer la imagen")
            }
            // Redimensionar si es muy grande
            val bitmap = if (originalBitmap.width > 1024) {
                val ratio = 1024f / originalBitmap.width
                val newHeight = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, 1024, newHeight, true)
            } else originalBitmap
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            ExtractedContent(type = ContentType.IMAGE, base64 = base64, 
                note = "image/jpeg")
        } catch (e: Exception) {
            ExtractedContent(ContentType.METADATA_ONLY, note = "Error al leer imagen: ${e.message}")
        }
    }

    private fun extractVideo(file: File): ExtractedContent {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val originalBitmap = retriever.getFrameAtTime(1000000) // 1000ms en microsegundos
            retriever.release()
            
            if (originalBitmap == null) {
                return ExtractedContent(ContentType.METADATA_ONLY, note = "No se pudo extraer frame del video")
            }
            
            val bitmap = if (originalBitmap.width > 1024) {
                val ratio = 1024f / originalBitmap.width
                val newHeight = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, 1024, newHeight, true)
            } else originalBitmap
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            ExtractedContent(type = ContentType.IMAGE, base64 = base64, 
                note = "video frame extraído")
        } catch (e: Exception) {
            ExtractedContent(ContentType.METADATA_ONLY, note = "Error al procesar video: ${e.message}")
        }
    }
}
