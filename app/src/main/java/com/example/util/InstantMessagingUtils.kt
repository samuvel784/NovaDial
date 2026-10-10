package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object InstantMessagingUtils {

    fun openWhatsApp(context: Context, rawNumber: String, initialText: String? = null) {
        val cleanDigits = rawNumber.replace(Regex("[^0-9+]"), "")
        if (cleanDigits.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val encodedText = initialText?.let { Uri.encode(it) } ?: ""
        val url = "https://api.whatsapp.com/send?phone=$cleanDigits${if (encodedText.isNotEmpty()) "&text=$encodedText" else ""}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp is not installed or url cannot be opened.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openTelegram(context: Context, rawNumber: String) {
        val cleanDigits = rawNumber.replace(Regex("[^0-9+]"), "")
        if (cleanDigits.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        // Telegram link by phone number
        val url = "https://t.me/+$cleanDigits"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Telegram app not available", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSignal(context: Context, rawNumber: String) {
        val cleanDigits = rawNumber.replace(Regex("[^0-9+]"), "")
        if (cleanDigits.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "https://signal.me/#p/+$cleanDigits"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Signal app not available", Toast.LENGTH_SHORT).show()
        }
    }
}
