package com.example.upivoicealert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var konfettiView: KonfettiView? = null
    private var cardPayment: LinearLayout? = null
    private var tvAmount: TextView? = null
    private var tvUpiApp: TextView? = null
    private var tvTodayTotal: TextView? = null
    private var tvTodayCount: TextView? = null
    private var layoutHistoryList: LinearLayout? = null
    private var etCustomVoice: EditText? = null
    private var tvVolumeLevel: TextView? = null
    private var seekVolume: SeekBar? = null
    private var spinnerLanguage: Spinner? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val languages = listOf("తెలుగు (Telugu)", "English", "हिन्दी (Hindi)")
    private val langCodes = listOf("te", "en", "hi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // స్క్రీన్ ఆఫ్ లో ఉన్నా లాక్ స్క్రీన్ పై వెలిగి కనిపించేలా చేయడం
        turnScreenOnAndShowWhenLocked()

        setContentView(R.layout.activity_main)

        tts = TextToSpeech(applicationContext, this, "com.google.android.tts")

        konfettiView = findViewById(R.id.konfettiView)
        cardPayment = findViewById(R.id.cardPayment)
        tvAmount = findViewById(R.id.tvAmount)
        tvUpiApp = findViewById(R.id.tvUpiApp)
        tvTodayTotal = findViewById(R.id.tvTodayTotal)
        tvTodayCount = findViewById(R.id.tvTodayCount)
        layoutHistoryList = findViewById(R.id.layoutHistoryList)
        etCustomVoice = findViewById(R.id.etCustomVoice)
        tvVolumeLevel = findViewById(R.id.tvVolumeLevel)
        seekVolume = findViewById(R.id.seekVolume)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)

        val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)

        // లాంగ్వేజ్ స్పిన్నర్
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerLanguage?.adapter = adapter

        val savedLangCode = prefs.getString("voice_lang", "te") ?: "te"
        val langIndex = langCodes.indexOf(savedLangCode).let { if (it >= 0) it else 0 }
        spinnerLanguage?.setSelection(langIndex)

        spinnerLanguage?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = langCodes[position]
                val currentCode = prefs.getString("voice_lang", "te")
                if (selectedCode != currentCode) {
                    prefs.edit().putString("voice_lang", selectedCode).apply()
                    updateTtsLanguage(selectedCode)

                    val defaultMsg = when (selectedCode) {
                        "hi" -> "{app} par {amount} rupaye prapt hue"
                        "en" -> "Received {amount} rupees on {app}"
                        else -> "{app} ద్వారా {amount} రూపాయలు అందాయి"
                    }
                    etCustomVoice?.setText(defaultMsg)
                    prefs.edit().putString("voice_template", defaultMsg).apply()
                    Toast.makeText(this@MainActivity, "${languages[position]} భాష సెట్ చేయబడింది", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val savedVoice = prefs.getString("voice_template", "{app} ద్వారా {amount} రూపాయలు అందాయి")
        etCustomVoice?.setText(savedVoice)

        findViewById<Button>(R.id.btnSaveCustomVoice).setOnClickListener {
            val text = etCustomVoice?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                prefs.edit().putString("voice_template", text).apply()
                Toast.makeText(this, "వాయిస్ మెసేజ్ సేవ్ చేయబడింది!", Toast.LENGTH_SHORT).show()
            }
        }

        val savedVolume = prefs.getInt("voice_volume", 100)
        seekVolume?.progress = savedVolume
        tvVolumeLevel?.text = "🔊 వాల్యూమ్ స్థాయి: $savedVolume%"

        seekVolume?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolumeLevel?.text = "🔊 వాల్యూమ్ స్థాయి: $progress%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val p = sb?.progress ?: 100
                prefs.edit().putInt("voice_volume", p).apply()
                setDeviceVolume(p)
                Toast.makeText(this@MainActivity, "వాల్యూమ్ $p% కి సెట్ చేయబడింది", Toast.LENGTH_SHORT).show()
            }
        })

        findViewById<Button>(R.id.btnTestAlert).setOnClickListener {
            triggerAlert("500", "PhonePe")
        }

        findViewById<Button>(R.id.btnExportPdf).setOnClickListener {
            exportToPdf()
        }

        checkPermissions()
        refreshHistoryAndSummary()
        handleIntent(intent)
    }

    private fun turnScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun updateTtsLanguage(code: String) {
        if (!isTtsReady) return
        val loc = when (code) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale("en", "IN")
            else -> Locale("te", "IN")
        }
        val res = tts?.setLanguage(loc)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.ENGLISH)
        }
    }

    private fun checkPermissions() {
        try {
            val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            if (enabledListeners == null || !enabledListeners.contains(packageName)) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
            val savedLangCode = prefs.getString("voice_lang", "te") ?: "te"
            updateTtsLanguage(savedLangCode)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnAndShowWhenLocked()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val amount = intent?.getStringExtra("AMOUNT")
        val app = intent?.getStringExtra("APP")
        if (amount != null && app != null) {
            triggerAlert(amount, app)
            refreshHistoryAndSummary()
        }
    }

    private fun triggerAlert(amount: String, appName: String) {
        turnScreenOnAndShowWhenLocked()

        tvAmount?.text = "₹ $amount"
        tvUpiApp?.text = "$appName ద్వారా అందింది"
        cardPayment?.visibility = View.VISIBLE

        val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
        val template = prefs.getString("voice_template", "{app} ద్వారా {amount} రూపాయలు అందాయి") ?: "{app} ద్వారా {amount} రూపాయలు అందాయి"
        val message = template.replace("{app}", appName).replace("{amount}", amount)

        speakLoudly(message)

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

    private fun setDeviceVolume(percent: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun speakLoudly(msg: String) {
        val prefs = getSharedPreferences("UpiVoicePrefs", Context.MODE_PRIVATE)
        val volPercent = prefs.getInt("voice_volume", 100)
        setDeviceVolume(volPercent)

        val langCode = prefs.getString("voice_lang", "te") ?: "te"
        updateTtsLanguage(langCode)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        if (isTtsReady) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, params, "ALERT_VOICE")
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshHistoryAndSummary() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = AppDatabase.getInstance(this@MainActivity).getAllPayments()

            var todaySum = 0.0
            var todayCount = 0

            val calToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            for (r in list) {
                if (r.timestamp >= calToday) {
                    todaySum += (r.amount.toDoubleOrNull() ?: 0.0)
                    todayCount++
                }
            }

            withContext(Dispatchers.Main) {
                tvTodayTotal?.text = "₹ " + String.format(Locale.US, "%.2f", todaySum)
                tvTodayCount?.text = "$todayCount"

                layoutHistoryList?.removeAllViews()
                val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())

                if (list.isEmpty()) {
                    val tvEmpty = TextView(this@MainActivity).apply {
                        text = "ఇంకా ఎలాంటి లావాదేవీలు లేవు"
                        textSize = 14f
                        setTextColor(Color.parseColor("#9CA3AF"))
                        gravity = Gravity.CENTER
                        setPadding(0, 30, 0, 30)
                    }
                    layoutHistoryList?.addView(tvEmpty)
                } else {
                    for (record in list.take(30)) {
                        val rowCard = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setBackgroundColor(Color.WHITE)
                            setPadding(24, 20, 24, 20)
                            elevation = 2f
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 16) }
                            layoutParams = params
                        }

                        val leftInfo = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val tvApp = TextView(this@MainActivity).apply {
                            text = record.upiApp
                            textSize = 15f
                            setTextColor(Color.parseColor("#1F2937"))
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }

                        val tvDate = TextView(this@MainActivity).apply {
                            text = sdf.format(Date(record.timestamp))
                            textSize = 12f
                            setTextColor(Color.parseColor("#6B7280"))
                            setPadding(0, 4, 0, 0)
                        }

                        leftInfo.addView(tvApp)
                        leftInfo.addView(tvDate)

                        val tvAmt = TextView(this@MainActivity).apply {
                            text = "+ ₹" + record.amount
                            textSize = 16f
                            setTextColor(Color.parseColor("#16A34A"))
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        }

                        rowCard.addView(leftInfo)
                        rowCard.addView(tvAmt)

                        layoutHistoryList?.addView(rowCard)
                    }
                }
            }
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

            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("UPI Payment Transactions Statement", 40f, 50f, paint)

            paint.textSize = 11f
            paint.isFakeBoldText = false
            var yPos = 90f
            val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())

            for (record in list) {
                val row = "${sdf.format(Date(record.timestamp))}   |   ${record.upiApp}   |   ₹${record.amount}"
                canvas.drawText(row, 40f, yPos, paint)
                yPos += 26f
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
