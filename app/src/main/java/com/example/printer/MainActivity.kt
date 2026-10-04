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

        val labelTitle = TextView(this).apply { text = "1. طباعة الملصقات (EZD)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
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

        val receiptTitle = TextView(this).apply { text = "2. طباعة الفواتير (ESC)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
        val receiptInput = EditText(this).apply { hint = "اختبار الفاتورة..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة تجريبية 🧾"
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                printReceiptESC()
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
            printReceiptESC()
        }
    }

    private fun printLabelTSPL(barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    Thread.sleep(500) 
                    val tsplCommand = "SIZE 38 mm,25 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\nBARCODE 60,40,\"128\",80,1,0,2,2,\"\(barcode\"\r\nTEXT 100,140,\"3\",0,1,1,\"\)barcode\"\r\nPRINT 1,1\r\n"
                    printerConnection.write(tsplCommand.toByteArray())
                    printerConnection.send() 
                    Thread.sleep(2000)
                    printerConnection.disconnect()
                    runOnUiThread { Toast.makeText(this, "تم أمر الملصق", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // دالة الفواتير الخام: إنجليزي فقط لإثبات استجابة الطابعة الميكانيكية
    private fun printReceiptESC() {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    Thread.sleep(500)
                    
                    // 1. أمر تهيئة الطابعة (ESC @)
                    printerConnection.write(byteArrayOf(0x1B, 0x40))
                    
                    // 2. إرسال نص إنجليزي بحت (US_ASCII) لتجنب انهيار الطابعة بسبب الترميز
                    val text = "SUCCESS! RECEIPT MODE IS WORKING.\r\nTESTING HARDWARE RESPONSE.\r\n-----------------------\r\n\r\n\r\n\r\n\r\n"
                    printerConnection.write(text.toByteArray(Charsets.US_ASCII))
                    
                    // 3. الدفع الفعلي للأوامر
                    printerConnection.send()
                    
                    Thread.sleep(3000)
                    printerConnection.disconnect() 
                    
                    runOnUiThread { Toast.makeText(this, "تم أمر الفاتورة", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}