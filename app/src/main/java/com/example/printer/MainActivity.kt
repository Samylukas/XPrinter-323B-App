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

        // استخدام ScrollView لكي لا تغطي لوحة المفاتيح على الأزرار
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

        // --- 1. نافذة تجربة الملصقات ---
        val labelTitle = TextView(this).apply { text = "1. طباعة الملصقات (تأكد أن الطابعة EZD)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 20, 0, 10) }
        val labelInput = EditText(this).apply { hint = "اكتب رقم الباركود هنا (مثال: 123456)..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق واحد 🏷️"
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code) else Toast.makeText(context, "يرجى كتابة رقم الباركود", Toast.LENGTH_SHORT).show()
            }
        }

        // --- 2. نافذة تجربة الفواتير ---
        val receiptTitle = TextView(this).apply { text = "2. طباعة الفواتير (تأكد أن الطابعة ESC)"; setTextColor(Color.BLACK); textSize = 16f; setPadding(0, 40, 0, 10) }
        val receiptInput = EditText(this).apply { hint = "اكتب نص الفاتورة هنا..."; textSize = 16f; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة تجريبية 🧾"
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = receiptInput.text.toString()
                if (txt.isNotEmpty()) printReceiptESC(txt) else Toast.makeText(context, "يرجى كتابة نص الفاتورة", Toast.LENGTH_SHORT).show()
            }
        }
        
        val infoText = TextView(this).apply { 
            text = "\n⚠️ ملاحظة: لطباعة فاتورة بنجاح، يجب تغيير وضع الطابعة فيزيائياً من EZD إلى وضع الفواتير ESC، وإلا ستتجاهل الطابعة الأمر."
            setTextColor(Color.RED)
            textSize = 14f
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(labelTitle)
        layout.addView(labelInput)
        layout.addView(btnTestLabel)
        layout.addView(receiptTitle)
        layout.addView(receiptInput)
        layout.addView(btnTestReceipt)
        layout.addView(infoText)
        
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
            statusText.text = "مستقبل أمر: طباعة ملصق للباركود $barcode"
            printLabelTSPL(barcode)
        } else {
            val printPayload = data.getQueryParameter("text")
            if (!printPayload.isNullOrEmpty()) {
                statusText.text = "مستقبل أمر: طباعة فاتورة"
                printReceiptESC(printPayload)
            }
        }
    }

    // --- محرك طباعة الليبل (لغة TSPL الخام) ---
    // هذا المحرك يرسل الأوامر مباشرة بدون مكتبة ESC، مما يمنع خروج ورقة فارغة ويمنع تهنيج الطابعة
    private fun printLabelTSPL(barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    printerConnection.connect()
                    
                    // أوامر TSPL لضبط الورقة على 38 مم عرض و 25 مم طول، وطباعة باركود ورقم واحد
                    val tsplCommand = """
                        SIZE 38 mm, 25 mm
                        GAP 2 mm, 0 mm
                        DIRECTION 1
                        CLS
                        BARCODE 20,40,"128",80,1,0,2,2,"$barcode"
                        TEXT 20,140,"3",0,1,1,"$barcode"
                        PRINT 1,1
                        
                    """.trimIndent()

                    printerConnection.write(tsplCommand.toByteArray())
                    Thread.sleep(400) // تأخير بسيط لضمان التفريغ
                    printerConnection.disconnect() // إغلاق الاتصال بأمان تام
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم طباعة الملصق بنجاح", Toast.LENGTH_SHORT).show()
                        statusText.text = "🟢 جاهز لاستقبال الأوامر"
                    }
                } else {
                    runOnUiThread { statusText.text = "🔴 لا توجد طابعة مقترنة" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { statusText.text = "🔴 خطأ تقني: ${e.message}" }
            }
        }.start()
    }

    // --- محرك طباعة الفاتورة (لغة ESC/POS) ---
    private fun printReceiptESC(payloadText: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    val formattedText = "[C]El Sayeh Store\n" +
                                        "[L]\n" +
                                        "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                        "[C]--------------------------------\n"
                    printer.printFormattedText(formattedText)
                    Thread.sleep(400)
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم إرسال الفاتورة", Toast.LENGTH_SHORT).show() 
                        statusText.text = "🟢 جاهز لاستقبال الأوامر"
                    }
                } else {
                    runOnUiThread { statusText.text = "🔴 لا توجد طابعة مقترنة" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { statusText.text = "🔴 خطأ تقني: ${e.message}" }
            }
        }.start()
    }
}