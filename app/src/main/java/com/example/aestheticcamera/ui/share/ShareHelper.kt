package com.example.aestheticcamera.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    fun getShareableUri(context: Context, uri: Uri): Uri {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else {
                uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri
        }
    }

    fun shareMedia(context: Context, uri: Uri, mimeType: String = "image/*") {
        try {
            val contentUri = getShareableUri(context, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar mídia", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareToWhatsApp(context: Context, uri: Uri, mimeType: String = "image/*") {
        try {
            val contentUri = getShareableUri(context, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback para compartilhador geral se o WhatsApp não estiver instalado
            shareMedia(context, uri, mimeType)
        }
    }

    fun shareToInstagram(context: Context, uri: Uri, mimeType: String = "image/*") {
        try {
            val contentUri = getShareableUri(context, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                setPackage("com.instagram.android")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback para compartilhador geral se o Instagram não estiver instalado
            shareMedia(context, uri, mimeType)
        }
    }
}
