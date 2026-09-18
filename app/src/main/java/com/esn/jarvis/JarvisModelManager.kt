package com.esn.jarvis

import android.content.Context
import android.net.Uri
import java.io.FileOutputStream

object JarvisModelManager {
    data class ImportResult(val ok: Boolean, val message: String)

    fun importGguf(context: Context, uri: Uri): ImportResult {
        val name = queryName(context, uri)
        if (!name.endsWith(".gguf", ignoreCase = true)) return ImportResult(false, "Select a GGUF model file.")
        val target = JarvisModelRuntime.modelFile(context)
        val temp = target.resolveSibling(target.name + ".part")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, 1024 * 1024)
                    output.fd.sync()
                }
            } ?: return ImportResult(false, "I could not open that model file.")
            if (temp.length() <= 1_000_000L) {
                temp.delete()
                return ImportResult(false, "That GGUF file is too small to be a usable model.")
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                return ImportResult(false, "I could not replace the existing local model.")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                return ImportResult(false, "I could not finish installing the local model.")
            }
            ImportResult(true, "Local GGUF installed (" + formatSize(target.length()) + ").")
        } catch (t: Throwable) {
            temp.delete()
            ImportResult(false, "Model import failed: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    fun delete(context: Context): Boolean {
        val file = JarvisModelRuntime.modelFile(context)
        return !file.exists() || file.delete()
    }

    fun installedSize(context: Context): String {
        val file = JarvisModelRuntime.modelFile(context)
        return if (file.exists()) formatSize(file.length()) else "NONE"
    }

    private fun queryName(context: Context, uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index).orEmpty()
            }
        }
        return name.ifBlank { uri.lastPathSegment.orEmpty() }
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(java.util.Locale.US, "%.2f GB", gb)
        else String.format(java.util.Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
    }
}
