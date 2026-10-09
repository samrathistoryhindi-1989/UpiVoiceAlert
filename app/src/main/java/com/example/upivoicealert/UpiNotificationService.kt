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
                tts?.language = Locale("en", "IN")
            }
            isTtsReady = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val content = "$title $text"

        val upiApp = when (pkg) {
            "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
            "com.phonepe.app" -> "PhonePe"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM"
            else -> null
        }

        if (upiApp != null && (content.contains("received", true) || content.contains("credited", true))) {
            val pattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(content)

            if (matcher.find()) {
                val amount = matcher.group(1)?.replace(",", "") ?: "0"

                // 1. వాయిస్ అలర్ట్
                if (isTtsReady) {
                    val msg = "$upiApp ద్వారా $amount రూపాయలు అందాయి"
                    tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "UPI_VOICE")
                }

                // 2. డేటాబేస్ లో సేవ్
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getInstance(applicationContext).paymentDao().insertPayment(
                        PaymentRecord(amount = amount, upiApp = upiApp)
                    )
                }

                // 3. హోమ్ స్క్రీన్ పై డైరెక్ట్‌గా పాపప్ ఓపెన్ చేయడం
                val popupIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("AMOUNT", amount)
                    putExtra("APP", upiApp)
                    putExtra("IS_POPUP", true)
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
