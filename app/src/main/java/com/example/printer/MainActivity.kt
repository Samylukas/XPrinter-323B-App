package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
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
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. طلب صلاحيات البلوتوث فور فتح التطبيق
        requestBluetoothPermissions()

        // 2. بناء الواجهة لتكون متاحة عند فتح التطبيق يدوياً للاختبار
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
            text = "🟢 جاهز للاختبار أو استقبال الأوامر"
            textSize = 16f
            setTextColor(Color.parseColor("#27ae60"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val labelTitle = TextView(this).apply { text = "1. طباعة الملصقات (EZD)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
        val labelInput = EditText(this).apply { hint = "اكتب رقم الباركود هنا..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق واحد 🏷️"
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code, false) else Toast.makeText(context, "اكتب الباركود", Toast.LENGTH_SHORT).show()
            }
        }

        val receiptTitle = TextView(this).apply { text = "2. طباعة الفواتير (ESC)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
        val receiptInput = EditText(this).apply { hint = "اختبار الفاتورة..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة تجريبية 🧾"
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = receiptInput.text.toString()
                printReceiptDirect(if (txt.isNotEmpty()) txt else "اختبار الفاتورة\nشغال ممتاز!", false)
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
        
        // 3. التحقق مما إذا كان التطبيق فُتح عن طريق أمر من برنامج الصيدلية
        handlePrintIntent(intent)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                }
            } else {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH)
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), 1)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    private fun handlePrintIntent(intent: Intent?) {
        if (intent == null || intent.data == null) return
        val data: Uri = intent.data!!
        
        if (data.scheme == "printbridge") {
            // الخدعة السحرية: إرسال هذا التطبيق للخلفية فوراً ليعود المستخدم لتطبيق الصيدلية
            moveTaskToBack(true)
            
            val type = data.getQueryParameter("type")
            if (type == "label") {
                val barcode = data.getQueryParameter("barcode") ?: "0000"
                printLabelTSPL(barcode, true) 
            } else {
                val text = data.getQueryParameter("text") ?: "TEST RECEIPT"
                printReceiptDirect(text, true) 
            }
        }
    }

    // محرك الملصقات
    private fun printLabelTSPL(barcode: String, autoClose: Boolean) {
        Toast.makeText(this, "جاري إرسال الملصق للطابعة...", Toast.LENGTH_SHORT).show()
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
                        BARCODE 60,40,"128",80,0,0,2,2,"$barcode"
                        TEXT 90,130,"3",0,1,1,"$barcode"
                        PRINT 1,1
                        
                    """.trimIndent().replace("\n", "\r\n")
                    
                    printerConnection.write(command.toByteArray())
                    printerConnection.send() 
                    Thread.sleep(2000)
                    printerConnection.disconnect()
                    runOnUiThread { Toast.makeText(this@MainActivity, "تم أمر الملصق", Toast.LENGTH_SHORT).show() }
                } else {
                    runOnUiThread { Toast.makeText(this@MainActivity, "لم يتم العثور على طابعة مقترنة!", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                if (autoClose) {
                    runOnUiThread { finish() }
                }
            }
        }.start()
    }

    // محرك الفواتير
    @SuppressLint("MissingPermission")
    private fun printReceiptDirect(payloadText: String, autoClose: Boolean) {
        Toast.makeText(this, "جاري إرسال الفاتورة للطابعة...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter?.bondedDevices?.firstOrNull() 
                
                if (device != null) {
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    val socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                    val out = socket.outputStream
                    
                    val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    textPaint.color = android.graphics.Color.BLACK
                    textPaint.textSize = 32f
                    textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    
                    val formattedText = "=== Anjum Green Pharmacy ===\n\n$payloadText\n\nشكراً لزيارتكم\n"
                    val printWidth = 576 
                    
                    val staticLayout = android.text.StaticLayout.Builder.obtain(formattedText, 0, formattedText.length, textPaint, printWidth)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()
                        
                    val bitmap = android.graphics.Bitmap.createBitmap(printWidth, staticLayout.height + 40, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.translate(0f, 20f)
                    staticLayout.draw(canvas)

                    out.write(byteArrayOf(0x1B, 0x40)) 
                    
                    val bmpWidth = bitmap.width
                    val bmpHeight = bitmap.height
                    var offset = 0
                    
                    while (offset < bmpHeight) {
                        val chunkHeight = if (bmpHeight - offset > 255) 255 else bmpHeight - offset
                        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
                        val xL = (bmpWidth / 8) % 256
                        val xH = (bmpWidth / 8) / 256
                        out.write(byteArrayOf(xL.toByte(), xH.toByte()))
                        val yL = chunkHeight % 256
                        val yH = chunkHeight / 256
                        out.write(byteArrayOf(yL.toByte(), yH.toByte()))
                        
                        val rowBytes = ByteArray((bmpWidth / 8) * chunkHeight)
                        var index = 0
                        for (y in 0 until chunkHeight) {
                            for (x in 0 until bmpWidth step 8) {
                                var b = 0
                                for (k in 0..7) {
                                    if (x + k < bmpWidth) {
                                        val color = bitmap.getPixel(x + k, offset + y)
                                        val r = android.graphics.Color.red(color)
                                        val g = android.graphics.Color.green(color)
                                        val bColor = android.graphics.Color.blue(color)
                                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bColor).toInt()
                                        if (luminance < 128) {
                                            b = b or (1 shl (7 - k))
                                        }
                                    }
                                }
                                rowBytes[index++] = b.toByte()
                            }
                        }
                        out.write(rowBytes)
                        offset += chunkHeight
                    }
                    
                    out.write(byteArrayOf(0x1B, 0x64, 0x05))
                    out.flush()
                    Thread.sleep(3000) 
                    socket.close()
                    runOnUiThread { Toast.makeText(this@MainActivity, "تم طباعة الفاتورة", Toast.LENGTH_SHORT).show() }
                } else {
                    runOnUiThread { Toast.makeText(this@MainActivity, "لم يتم العثور على طابعة مقترنة!", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                if (autoClose) {
                    runOnUiThread { finish() }
                }
            }
        }.start()
    }
}