package com.example.printer

import android.Manifest
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
            text = "🟢 جاهز لاستقبال الأوامر"
            textSize = 16f
            setTextColor(Color.parseColor("#27ae60"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val labelTitle = TextView(this).apply { text = "1. طباعة الملصقات (يجب ضبط الطابعة على EZD)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
        val labelInput = EditText(this).apply { hint = "اكتب رقم الباركود هنا..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق واحد 🏷️"
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code) else Toast.makeText(context, "اكتب رقم الباركود", Toast.LENGTH_SHORT).show()
            }
        }

        val receiptTitle = TextView(this).apply { text = "2. طباعة الفواتير (يجب ضبط الطابعة على ESC)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
        val receiptInput = EditText(this).apply { hint = "اكتب نص الفاتورة هنا..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة تجريبية 🧾"
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = receiptInput.text.toString()
                if (txt.isNotEmpty()) printReceiptESC(txt) else Toast.makeText(context, "اكتب نص الفاتورة", Toast.LENGTH_SHORT).show()
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
            statusText.text = "مستقبل أمر: طباعة ملصق $barcode"
            printLabelTSPL(barcode)
        } else {
            val printPayload = data.getQueryParameter("text")
            if (!printPayload.isNullOrEmpty()) {
                statusText.text = "مستقبل أمر: طباعة فاتورة"
                printReceiptESC(printPayload)
            }
        }
    }

    private fun printLabelTSPL(barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    
                    val tsplCommand = "SIZE 38 mm,25 mm\r\n" +
                                      "GAP 2 mm,0 mm\r\n" +
                                      "DIRECTION 1\r\n" +
                                      "CLS\r\n" +
                                      "BARCODE 20,40,\"128\",80,1,0,2,2,\"$barcode\"\r\n" +
                                      "TEXT 20,140,\"3\",0,1,1,\"$barcode\"\r\n" +
                                      "PRINT 1,1\r\n"

                    // السر كله هنا: write بتجهز البيانات، و send بتدفعها فعلياً عبر البلوتوث للطابعة!
                    printerConnection.write(tsplCommand.toByteArray())
                    printerConnection.send() 
                    
                    Thread.sleep(1000)
                    printerConnection.disconnect()
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم إرسال الملصق بنجاح", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun printReceiptESC(payloadText: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    val formattedText = "[C]**TEST RECEIPT**\n[L]\n[L]$payloadText\n[C]----------------\n"
                    
                    // دالة printFormattedText بداخلها أمر send تلقائي
                    printer.printFormattedText(formattedText)
                    
                    Thread.sleep(1000)
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم إرسال الفاتورة", Toast.LENGTH_SHORT).show() 
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}