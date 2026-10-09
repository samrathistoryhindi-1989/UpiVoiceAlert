package com.example.upivoicealert

import android.content.Intent
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
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("te", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.ENGLISH)
            }
            isTtsReady = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullText = "$title $text"

        var upiApp = ""
        when {
            pkg.contains("google.android.apps.nbu.paisa.user") -> upiApp = "Google Pay"
            pkg.contains("com.phonepe.app") -> upiApp = "PhonePe"
            pkg.contains("net.one97.paytm") -> upiApp = "Paytm"
            pkg.contains("in.org.npci.upiapp") -> upiApp = "BHIM"
        }

        if (upiApp.isNotEmpty() && (fullText.contains("received", true) || fullText.contains("credited", true))) {
            val pattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(fullText)
            if (matcher.find()) {
                val amount = matcher.group(1)?.replace(",", "") ?: "0"

                if (isTtsReady) {
                    val msg = "$upiApp ద్వారా $amount రూపాయలు అందాయి"
                    tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "UPI_VOICE")
                }

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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
