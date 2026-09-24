package com.example.printer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class MainActivity : Activity() {
    
    private lateinit var printBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
            }
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 200, 50, 50)

        printBtn = Button(this)
        printBtn.text = "مستعد للطباعة من النظام"
        printBtn.textSize = 24f
        layout.addView(printBtn)
        setContentView(layout)

        printBtn.setOnClickListener {
            printReceiptFromWeb("فاتورة تجريبية يدوية")
        }

        // استقبال الطلب لو التطبيق لسة بيفتح لأول مرة
        handlePrintIntent(intent)
    }

    // السر هنا: استقبال الطلب لو التطبيق مفتوح بالفعل في الخلفية (لمنع تجاهل الطابعة)
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
            val name = data.getQueryParameter("name") ?: ""
            val price = data.getQueryParameter("price") ?: ""
            val barcode = data.getQueryParameter("barcode") ?: ""
            printBtn.text = "جاري طباعة ملصق الباركود..."
            printLabelFromWeb(name, price, barcode)
        } else {
            val printPayload = data.getQueryParameter("text")
            if (!printPayload.isNullOrEmpty()) {
                printBtn.text = "جاري طباعة الفاتورة..."
                printReceiptFromWeb(printPayload)
            }
        }
    }

    private fun printReceiptFromWeb(payloadText: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    val formattedText = "[C]<u><font size='big'>El Sayeh Store</font></u>\n" +
                                        "[L]\n" +
                                        "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                        "[C]--------------------------------\n"
                    printer.printFormattedText(formattedText)
                    
                    // تحرير الطابعة فوراً عشان تشتغل المرة الجاية بدون ريستارت
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم الطباعة بنجاح", Toast.LENGTH_SHORT).show() 
                        printBtn.text = "مستعد للطباعة من النظام"
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "يرجى ربط الطابعة بالبلوتوث", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "حدث خطأ أثناء الطباعة", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun printLabelFromWeb(name: String, price: String, barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    val formattedText = "[C]<b>$name</b>\n" +
                                        "[C]السعر : $price ج.م\n" +
                                        "[C]<barcode type='128' width='2' height='10'>$barcode</barcode>\n"
                    
                    printer.printFormattedText(formattedText)
                    
                    // تحرير الطابعة
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم طباعة الباركود", Toast.LENGTH_SHORT).show() 
                        printBtn.text = "مستعد للطباعة من النظام"
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "يرجى ربط الطابعة بالبلوتوث", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "حدث خطأ أثناء طباعة الباركود", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}