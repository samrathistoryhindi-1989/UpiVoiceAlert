package com.example.upivoicealert

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
    private var lastAmount = ""
    private var lastTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
            val langCode = prefs.getString("voice_lang", "te") ?: "te"
            val loc = when (langCode) {
                "hi" -> Locale("hi", "IN")
                "en" -> Locale("en", "IN")
                else -> Locale("te", "IN")
            }
            tts?.setLanguage(loc)
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            isTtsReady = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""
        val fullText = "$title $text $bigText $subText".trim()

        if (fullText.isEmpty()) return

        val pkg = (sbn.packageName ?: "").lowercase(Locale.ROOT)
        var upiApp = ""

        when {
            pkg.contains("paisa") || pkg.contains("nbu") -> upiApp = "Google Pay"
            pkg.contains("phonepe") -> upiApp = "PhonePe"
            pkg.contains("paytm") -> upiApp = "Paytm"
            pkg.contains("bhim") || pkg.contains("npci") -> upiApp = "BHIM"
            pkg.contains("cred") -> upiApp = "CRED"
            pkg.contains("amazon") -> upiApp = "Amazon Pay"
            pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("sms") -> upiApp = "Bank SMS"
            else -> {
                if (fullText.contains("UPI", true) || fullText.contains("bank", true) || fullText.contains("a/c", true)) {
                    upiApp = "UPI Payment"
                }
            }
        }

        if (upiApp.isEmpty()) return

        val lower = fullText.lowercase(Locale.ROOT)
        val isCredit = lower.contains("received") ||
                lower.contains("credited") ||
                lower.contains("deposited") ||
                lower.contains("sent you") ||
                lower.contains("paid you") ||
                lower.contains("added") ||
                lower.contains("జమ") ||
                lower.contains("వచ్చింది") ||
                lower.contains("అందాయి") ||
                lower.contains("प्राप्त")

        if (!isCredit || lower.contains("debited") || lower.contains("sent to") || lower.contains("paid to")) {
            return
        }

        val amount = extractAmount(fullText)
        if (!amount.isNullOrEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (amount == lastAmount && (currentTime - lastTime) < 5000) {
                return
            }
            lastAmount = amount
            lastTime = currentTime

            val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
            val langCode = prefs.getString("voice_lang", "te") ?: "te"
            val defaultTemplate = when (langCode) {
                "hi" -> "{app} par {amount} rupaye prapt hue"
                "en" -> "Received {amount} rupees on {app}"
                else -> "{app} ద్వారా {amount} రూపాయలు అందాయి"
            }
            val template = prefs.getString("voice_template", defaultTemplate) ?: defaultTemplate
            val voiceMsg = template.replace("{app}", upiApp).replace("{amount}", amount)

            speakCustomLoudly(voiceMsg, langCode)

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

    private fun extractAmount(text: String): String? {
        val patterns = listOf(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹|రూపాయలు|rupees)",
            "received\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "credited\\s*(?:by|with)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "paid\\s+you\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
        )

        for (p in patterns) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(text)
            if (matcher.find()) {
                val clean = matcher.group(1)?.replace(",", "")?.trim()
                if (!clean.isNullOrEmpty() && clean.toDoubleOrNull() != null && clean.toDouble() > 0) {
                    return clean
                }
            }
        }
        return null
    }

    private fun speakCustomLoudly(message: String, langCode: String) {
        val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
        val volPercent = prefs.getInt("voice_volume", 100)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((volPercent / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)

        val loc = when (langCode) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale("en", "IN")
            else -> Locale("te", "IN")
        }
        tts?.setLanguage(loc)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        if (isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "LOUD_UPI_ALERT")
        } else {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = loc
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "LOUD_UPI_ALERT")
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
