package com.example.upivoicealert

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

class MainActivity : AppCompatActivity() {

    private lateinit var konfettiView: KonfettiView
    private lateinit var cardPayment: LinearLayout
    private lateinit var tvAmount: TextView
    private lateinit var tvUpiApp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        konfettiView = findViewById(R.id.konfettiView)
        cardPayment = findViewById(R.id.cardPayment)
        tvAmount = findViewById(R.id.tvAmount)
        tvUpiApp = findViewById(R.id.tvUpiApp)

        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            Toast.makeText(this, "నోటిఫికేషన్ యాక్సెస్ పర్మిషన్ ఆన్ చేయండి", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "పాపప్ కోసం Overlay పర్మిషన్ ఇవ్వండి", Toast.LENGTH_LONG).show()
            val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(overlayIntent)
        }

        findViewById<Button>(R.id.btnExportPdf).setOnClickListener {
            exportToPdf()
        }

        handleIntent(intent)
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
            showCrackersAnimation(amount, app)
        }
    }

    private fun showCrackersAnimation(amount: String, upiApp: String) {
        tvAmount.text = "₹ $amount"
        tvUpiApp.text = "$upiApp ద్వారా వచ్చింది"
        cardPayment.visibility = View.VISIBLE

        val party = Party(
            speed = 10f,
            maxSpeed = 35f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0x22c55e, 0x3b82f6),
            emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(150),
            position = Position.Relative(0.5, 0.4)
        )
        konfettiView.start(party)
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
}
