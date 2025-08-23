package com.example.huka

import android.content.Context
import java.io.File
import java.io.FileOutputStream

fun saveToFile(context: Context, fileName: String, text: String): Boolean {
    return try {
        val file = File(context.filesDir, fileName)
        FileOutputStream(file, true).use { fos ->
            fos.write((text + "\n").toByteArray())
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
