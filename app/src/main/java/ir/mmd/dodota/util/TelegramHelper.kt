package ir.mmd.dodota.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TelegramHelper {

    const val TELEGRAM_USERNAME = "momo1f"

    fun openTelegramSupport(context: Context) {
        openTelegramProfile(context, TELEGRAM_USERNAME)
    }

    fun openTelegramProfile(context: Context, username: String) {
        val targetUsername = username.removePrefix("@")
        try {
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$targetUsername"))
            telegramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(telegramIntent)
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$targetUsername"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "برنامه تلگرام یا مرورگر یافت نشد: @$targetUsername", Toast.LENGTH_LONG).show()
            }
        }
    }
}
