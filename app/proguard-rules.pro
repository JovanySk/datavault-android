# Reglas de Proguard para evitar la ofuscación de modelos de datos, red, corrutinas e IA

# Mantener atributos esenciales para reflexión, genéricos y depuración
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation, *Annotation*, SourceFile, LineNumberTable

# Mantener clases de modelos de datos y base de datos (usadas por Gson y Room)
-keep class com.example.datavault.data.model.** { *; }
-keep class com.example.datavault.data.local.entity.** { *; }
-keep class com.example.datavault.data.backup.** { *; }

# Mantener clases de red e interfaces de API (Retrofit) para evitar que R8 elimine las firmas genéricas
-keep class com.example.datavault.data.network.** { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Mantener clases de Kotlin, Metadatos y Corrutinas (vital para funciones suspend de Retrofit)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Metadata
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.DebugMetadata { *; }
-dontwarn kotlin.coroutines.**

# Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Evitar advertencias y ofuscación de PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Evitar advertencias y ofuscación del cliente API de Google y Drive
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
