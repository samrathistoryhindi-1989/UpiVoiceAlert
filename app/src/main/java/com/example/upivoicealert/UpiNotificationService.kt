package com.example.upivoicealert

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class UpiNotificationService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("te", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("en", "IN"))
            }
            isTtsReady = true
        } else {
            tts = TextToSpeech(applicationContext) { s ->
                if (s == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    isTtsReady = true
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title $text $bigText".trim()

        if (fullText.isEmpty()) return

        val pkg = sbn.packageName.lowercase(Locale.ROOT)
        var upiApp = "UPI"

        when {
            pkg.contains("paisa") || pkg.contains("nbu") -> upiApp = "Google Pay"
            pkg.contains("phonepe") -> upiApp = "PhonePe"
            pkg.contains("paytm") -> upiApp = "Paytm"
            pkg.contains("bhim") || pkg.contains("npci") -> upiApp = "BHIM"
            pkg.contains("cred") -> upiApp = "CRED"
            pkg.contains("amazon") -> upiApp = "Amazon Pay"
            else -> {
                if (fullText.contains("UPI", true) || fullText.contains("credited", true) || fullText.contains("received", true)) {
                    upiApp = "Bank UPI"
                } else {
                    return
                }
            }
        }

        // క్రెడిట్/రిసీవ్డ్ పదాలు ఉన్నాయో లేదో తనిఖీ
        val lowerText = fullText.lowercase(Locale.ROOT)
        val isCredit = lowerText.contains("received") ||
                lowerText.contains("credited") ||
                lowerText.contains("deposited") ||
                lowerText.contains("sent you") ||
                lowerText.contains("జమ") ||
                lowerText.contains("అందాయి")

        if (isCredit) {
            val amount = extractAmount(fullText)
            if (amount != null && amount.isNotEmpty()) {
                val voiceMsg = "$upiApp ద్వారా $amount రూపాయలు అందాయి"
                speakOut(voiceMsg)

                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getInstance(applicationContext).insertPayment(amount, upiApp)
                }

                val popupIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("AMOUNT", amount)
                    putExtra("APP", upiApp)
                }
                startActivity(popupIntent)
            }
        }
    }

    private fun extractAmount(text: String): String? {
        val regexPatterns = listOf(
            "(?:Rs\\.?|INR|₹|INR\\s*Rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹|రూపాయలు)",
            "received\\s+([0-9,]+(?:\\.[0-9]{1,2})?)",
            "credited\\s+with\\s+([0-9,]+(?:\\.[0-9]{1,2})?)"
        )

        for (p in regexPatterns) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(text)
            if (matcher.find()) {
                val found = matcher.group(1)?.replace(",", "")?.trim()
                if (!found.isNullOrEmpty() && found.toDoubleOrNull() != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun speakOut(message: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        if (isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "UPI_LIVE_ALERT")
        } else {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("te", "IN")
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "UPI_LIVE_ALERT")
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
