package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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

        requestBluetoothPermissions()

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#f0f4f8"))
        }

        val titleText = TextView(this).apply {
            text = "🖨️ Print Bridge (Arabic TSPL Supported)"
            textSize = 22f
            setTextColor(Color.parseColor("#1e3c72"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "🟢 جاهز للطباعة بالعربي والإنجليزي"
            textSize = 16f
            setTextColor(Color.parseColor("#27ae60"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val labelTitle = TextView(this).apply { text = "اختبار ملصق عربي"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
        val labelInput = EditText(this).apply { hint = "اكتب رقم الباركود..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق عربي تجريبي 🏷️"
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code, "أنجم جرين بنادول سوبر", "150.00 ج.م", false) 
                else Toast.makeText(context, "اكتب الباركود أولاً", Toast.LENGTH_SHORT).show()
            }
        }

        val receiptTitle = TextView(this).apply { text = "اختبار الفواتير"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
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
        
        handlePrintIntent(intent)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 1)
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
            moveTaskToBack(true)
            
            val type = data.getQueryParameter("type")
            if (type == "label") {
                val barcode = data.getQueryParameter("barcode") ?: "0000"
                val name = data.getQueryParameter("name") ?: ""
                val price = data.getQueryParameter("price") ?: ""
                printLabelTSPL(barcode, name, price, true) 
            } else {
                val text = data.getQueryParameter("text") ?: "TEST RECEIPT"
                printReceiptDirect(text, true) 
            }
        }
    }

    // 🔥 المحرك السحري لطباعة العربي على طابعات الباركود TSPL عبر تحويل الرسوم
    private fun printLabelTSPL(barcode: String, prodName: String, prodPrice: String, autoClose: Boolean) {
        Toast.makeText(this, "جاري طباعة الملصق العربي...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    Thread.sleep(300) 

                    // 1. رسم الملصق بالكامل باللغة العربية كـ Bitmap في الذاكرة
                    val labelWidthPx = 304  // يعادل 38mm بدقة 203dpi
                    val labelHeightPx = 200 // يعادل 25mm بدقة 203dpi
                    val bitmap = Bitmap.createBitmap(labelWidthPx, labelHeightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    // رسم الاسم العربي
                    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 24f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val nameLayout = StaticLayout.Builder.obtain(prodName, 0, prodName.length, namePaint, labelWidthPx - 10)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(5f, 5f)
                    nameLayout.draw(canvas)
                    canvas.restore()

                    // رسم السعر العربي
                    val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 26f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val priceLayout = StaticLayout.Builder.obtain(prodPrice, 0, prodPrice.length, pricePaint, labelWidthPx - 10)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(5f, (labelHeightPx - priceLayout.height - 5).toFloat())
                    priceLayout.draw(canvas)
                    canvas.restore()

                    // 2. تجهيز أوامر TSPL
                    val headerCommand = "SIZE 38 mm,25 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\n"
                    printerConnection.write(headerCommand.toByteArray())

                    // إرسال صورة النص السفلية والعلوية
                    val bmpBytes = bitmapToTsplBitmapCommand(bitmap, 0, 0)
                    printerConnection.write(bmpBytes)

                    // طباعة الباركود النصي في المنتصف
                    val barcodeCommand = "BARCODE 30,55,\"128\",60,0,0,2,2,\"$barcode\"\r\nTEXT 100,120,\"2\",0,1,1,\"$barcode\"\r\nPRINT 1,1\r\n"
                    printerConnection.write(barcodeCommand.toByteArray())

                    printerConnection.send() 
                    Thread.sleep(1500)
                    printerConnection.disconnect()
                    runOnUiThread { Toast.makeText(this@MainActivity, "تمت طباعة الملصق بنجاح!", Toast.LENGTH_SHORT).show() }
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

    // دالة تحويل الصورة إلى أمر BITMAP المفهوم لطابعات TSPL
    private fun bitmapToTsplBitmapCommand(bitmap: Bitmap, x: Int, y: Int): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val stream = java.io.ByteArrayOutputStream()

        val commandHead = "BITMAP $x,$y,$widthBytes,$height,0,"
        stream.write(commandHead.toByteArray())

        val imgData = ByteArray(widthBytes * height)
        var byteIndex = 0

        for (h in 0 until height) {
            for (w in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val pxX = w * 8 + bit
                    if (pxX < width) {
                        val color = bitmap.getPixel(pxX, h)
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val blue = Color.blue(color)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * blue).toInt()
                        if (luminance < 128) {
                            b = b or (1 shl (7 - bit))
                        }
                    }
                }
                imgData[byteIndex++] = b.toByte()
            }
        }
        stream.write(imgData)
        stream.write("\r\n".toByteArray())
        return stream.toByteArray()
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
                    
                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 32f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    
                    val formattedText = "=== Anjum Green Pharmacy ===\n\n$payloadText\n\nشكراً لزيارتكم\n"
                    val printWidth = 576 
                    
                    val staticLayout = StaticLayout.Builder.obtain(formattedText, 0, formattedText.length, textPaint, printWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()
                        
                    val bitmap = Bitmap.createBitmap(printWidth, staticLayout.height + 40, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
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
                                        val r = Color.red(color)
                                        val g = Color.green(color)
                                        val bColor = Color.blue(color)
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
                    Thread.sleep(2000) 
                    socket.close()
                    runOnUiThread { Toast.makeText(this@MainActivity, "تم طباعة الفاتورة بنجاح", Toast.LENGTH_SHORT).show() }
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