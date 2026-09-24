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

        val printBtn = Button(this)
        printBtn.text = "طباعة فاتورة تجريبية"
        printBtn.textSize = 24f
        layout.addView(printBtn)
        setContentView(layout)

        val intent = intent
        val action = intent.action
        val data: Uri? = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            val type = data.getQueryParameter("type")
            if (type == "label") {
                val name = data.getQueryParameter("name") ?: ""
                val price = data.getQueryParameter("price") ?: ""
                val barcode = data.getQueryParameter("barcode") ?: ""
                printLabelFromWeb(name, price, barcode)
            } else {
                val printPayload = data.getQueryParameter("text")
                if (!printPayload.isNullOrEmpty()) {
                    printReceiptFromWeb(printPayload)
                }
            }
        }

        printBtn.setOnClickListener {
            printReceiptFromWeb("فاتورة تجريبية يدوية")
        }
    }

    private fun printReceiptFromWeb(payloadText: String) {
        // تشغيل الطباعة في الخلفية لمنع تجميد الشاشة
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
                    
                    // الحل السحري: إغلاق الاتصال بعد الطباعة ليعمل مجدداً
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { Toast.makeText(this, "تم الطباعة بنجاح", Toast.LENGTH_SHORT).show() }
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
                    
                    // إغلاق الاتصال بعد الملصق
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { Toast.makeText(this, "تم طباعة الباركود", Toast.LENGTH_SHORT).show() }
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