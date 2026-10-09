package com.example.upivoicealert

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var konfettiView: KonfettiView? = null
    private var cardPayment: LinearLayout? = null
    private var tvAmount: TextView? = null
    private var tvUpiApp: TextView? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        konfettiView = findViewById(R.id.konfettiView)
        cardPayment = findViewById(R.id.cardPayment)
        tvAmount = findViewById(R.id.tvAmount)
        tvUpiApp = findViewById(R.id.tvUpiApp)

        findViewById<Button>(R.id.btnEnableNotification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnEnableOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "ఈ డివైస్‌కు అవసరం లేదు", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnTestAlert).setOnClickListener {
            triggerAlert("100", "PhonePe (Test)")
        }

        findViewById<Button>(R.id.btnExportPdf).setOnClickListener {
            exportToPdf()
        }

        handleIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val amount = intent?.getStringExtra("AMOUNT")
        val app = intent?.getStringExtra("APP")
        if (amount != null && app != null) {
            triggerAlert(amount, app)
        }
    }

    private fun triggerAlert(amount: String, appName: String) {
        tvAmount?.text = "₹ $amount"
        tvUpiApp?.text = "$appName ద్వారా అందింది"
        cardPayment?.visibility = View.VISIBLE

        speakTelugu("$appName ద్వారా $amount రూపాయలు అందాయి")

        val party = Party(
            speed = 10f,
            maxSpeed = 35f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0x22c55e, 0x3b82f6),
            emitter = Emitter(duration = 250, TimeUnit.MILLISECONDS).max(200),
            position = Position.Relative(0.5, 0.4)
        )
        konfettiView?.start(party)
    }

    private fun speakTelugu(msg: String) {
        if (isTtsReady) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "TEST_VOICE")
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToPdf() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = AppDatabase.getInstance(this@MainActivity).getAllPayments()
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint()

            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText("UPI Payment Transactions Statement", 50f, 60f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            var yPos = 110f
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

            for (record in list) {
                val row = "${sdf.format(Date(record.timestamp))}  |  ${record.upiApp}  |  ₹${record.amount}"
                canvas.drawText(row, 50f, yPos, paint)
                yPos += 30f
                if (yPos > 800f) break
            }

            pdfDoc.finishPage(page)

            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UPI_Statement_${System.currentTimeMillis()}.pdf")
            pdfDoc.writeTo(FileOutputStream(file))
            pdfDoc.close()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "PDF సేవ్ చేయబడింది: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
