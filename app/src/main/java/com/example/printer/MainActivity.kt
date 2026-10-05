package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
            }
        }

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#f0f4f8"))
        }

        val titleText = TextView(this).apply {
            text = "🖨️ Print Bridge"
            textSize = 24f
            setTextColor(Color.parseColor("#1e3c72"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "🟢 جاهز لاستقبال الأوامر من الموقع"
            textSize = 16f
            setTextColor(Color.parseColor("#27ae60"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val labelTitle = TextView(this).apply { text = "1. اختبار الملصقات"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
        val labelInput = EditText(this).apply { hint = "اكتب رقم الباركود هنا..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق واحد 🏷️"
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code) else Toast.makeText(context, "اكتب الباركود", Toast.LENGTH_SHORT).show()
            }
        }

        val receiptTitle = TextView(this).apply { text = "2. اختبار الفواتير"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
        val receiptInput = EditText(this).apply { hint = "اكتب فاتورة عربي/انجليزي..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة 🧾"
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = receiptInput.text.toString()
                printReceiptAsImage(if (txt.isNotEmpty()) txt else "اختبار الفاتورة\nبنجاح!")
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(labelTitle)
        layout.addView(labelInput)
        layout.addView(btnTestLabel)
        layout.addView(receiptTitle)
        layout.addView(receiptInput)
        layout.addView(btnTestReceipt)
        
        scrollView.addView(layout)
        setContentView(scrollView)
        handlePrintIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    private fun handlePrintIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW || intent.data == null) return
        val data: Uri = intent.data!!
        val type = data.getQueryParameter("type")
        
        if (type == "label") {
            val barcode = data.getQueryParameter("barcode") ?: "0000"
            printLabelTSPL(barcode)
        } else {
            val text = data.getQueryParameter("text") ?: "فاتورة من الموقع"
            printReceiptAsImage(text)
        }
    }

    // محرك الملصقات (يعمل بشكل مثالي ومستقر)
    private fun printLabelTSPL(barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    Thread.sleep(500) 
                    val command = """
                        SIZE 38 mm,25 mm
                        GAP 2 mm,0 mm
                        DIRECTION 1
                        CLS
                        BARCODE 60,40,"128",80,1,0,2,2,"$barcode"
                        TEXT 100,140,"3",0,1,1,"$barcode"
                        PRINT 1,1
                        
                    """.trimIndent().replace("\n", "\r\n")
                    
                    printerConnection.write(command.toByteArray())
                    printerConnection.send() 
                    Thread.sleep(2000)
                    printerConnection.disconnect()
                    runOnUiThread { Toast.makeText(this, "تمت الطباعة", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // محرك الفواتير الجديد: يحول الكلام لصورة عشان يطبع عربي/إنجليزي غصب عن الطابعة
    private fun printReceiptAsImage(payloadText: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    
                    // 1. إعداد الخط العربي الجميل وتنسيقه
                    val textPaint = android.text.TextPaint(android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
                    textPaint.color = android.graphics.Color.BLACK
                    textPaint.textSize = 36f
                    textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    
                    val formattedText = "=== EL SAYEH STORE ===\n\n$payloadText\n\nشكراً لزيارتكم\n\n\n"
                    
                    val staticLayout = android.text.StaticLayout(
                        formattedText, textPaint, 576, android.text.Layout.Alignment.ALIGN_CENTER, 1.2f, 0f, false
                    )
                    
                    // 2. تحويل الكلام إلى صورة (Bitmap)
                    val bitmap = android.graphics.Bitmap.createBitmap(576, staticLayout.height + 40, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.translate(0f, 20f)
                    staticLayout.draw(canvas)
                    
                    // 3. إرسال الصورة للطابعة (نفس فكرة 4print)
                    val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                    val hexImage = PrinterTextParserImg.bitmapToHexadecimalString(printer, drawable)
                    
                    printer.printFormattedTextAndCut("[C]