package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import java.util.UUID

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // لا حاجة لـ setContentView لأن التطبيق سيكون شفافاً تماماً وبدون واجهة
        handlePrintIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    private fun handlePrintIntent(intent: Intent?) {
        // إذا تم فتح التطبيق يدوياً بالغلط بدون أمر طباعة، نقفله فوراً
        if (intent == null || intent.data == null) {
            finish()
            return
        }
        
        val data: Uri = intent.data!!
        if (data.scheme == "printbridge") {
            val type = data.getQueryParameter("type")
            
            if (type == "label") {
                val barcode = data.getQueryParameter("barcode") ?: "0000"
                printLabelTSPL(barcode)
            } else {
                val text = data.getQueryParameter("text") ?: "TEST RECEIPT"
                printReceiptDirect(text)
            }
        } else {
            finish()
        }
    }

    // محرك الملصقات
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
                        BARCODE 60,40,"128",80,0,0,2,2,"$barcode"
                        TEXT 90,130,"3",0,1,1,"$barcode"
                        PRINT 1,1
                        
                    """.trimIndent().replace("\n", "\r\n")
                    
                    printerConnection.write(command.toByteArray())
                    printerConnection.send() 
                    Thread.sleep(2000)
                    printerConnection.disconnect()
                    runOnUiThread { Toast.makeText(this@MainActivity, "✅ تم إرسال الملصق للطابعة", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "❌ خطأ في الاتصال بالطابعة", Toast.LENGTH_SHORT).show() }
            } finally {
                // السطر السحري: إغلاق التطبيق الشفاف والعودة للصيدلية فوراً بعد انتهاء مهمة الطباعة
                finish()
            }
        }.start()
    }

    // محرك الفواتير
    @SuppressLint("MissingPermission")
    private fun printReceiptDirect(payloadText: String) {
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
                    
                    // تم مسح اسم المحل القديم تماماً واستخدام النص القادم من الصيدلية مباشرة
                    val formattedText = "$payloadText\n"
                    val printWidth = 576 // مقاس الطابعة الـ 80 مللي
                    
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
                    
                    runOnUiThread { Toast.makeText(this@MainActivity, "✅ تم طباعة الفاتورة!", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "❌ خطأ في الاتصال بالطابعة", Toast.LENGTH_SHORT).show() }
            } finally {
                // إغلاق التطبيق الشفاف والعودة للصيدلية
                finish()
            }
        }.start()
    }
}