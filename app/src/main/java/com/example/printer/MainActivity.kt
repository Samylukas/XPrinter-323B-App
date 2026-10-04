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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // طلب صلاحيات البلوتوث
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
            }
        }

        // --- بناء واجهة التطبيق (الكوبري والاختبار) ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#f0f4f8"))
        }

        val titleText = TextView(this).apply {
            text = "🖨️ El Sayeh Printer Bridge"
            textSize = 24f
            setTextColor(Color.parseColor("#1e3c72"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "🟢 التطبيق يعمل في الخلفية كوبري لاستقبال أوامر الطباعة من متصفح كروم."
            textSize = 16f
            setTextColor(Color.parseColor("#27ae60"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        // الزر الأول: اختبار الملصقات (EZD / 1x1.5 inch)
        val btnTestLabel = Button(this).apply {
            text = "تجربة ملصق (1x1.5 إنش - وضع EZD)"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#8e44ad"))
            setTextColor(Color.WHITE)
            setPadding(20, 30, 20, 30)
            setOnClickListener {
                statusText.text = "جاري طباعة الملصق..."
                // نمرر 38 ملم كعرض للورقة (يعادل 1.5 إنش تقريباً)
                printLabel("تيل فرامل أمامي", "250", "2006325")
            }
        }

        // مسافة بين الزرين
        val space = TextView(this).apply { textSize = 10f }

        // الزر الثاني: اختبار الفواتير (ESC / 80mm)
        val btnTestReceipt = Button(this).apply {
            text = "تجربة فاتورة (80 مم - وضع ESC)"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#3498db"))
            setTextColor(Color.WHITE)
            setPadding(20, 30, 20, 30)
            setOnClickListener {
                statusText.text = "جاري طباعة الفاتورة..."
                // نمرر 72 ملم مساحة طباعة فعلية لورقة 80 مم
                printReceipt("فاتورة تجريبية\n----------------\nالإجمالي: 150 ج.م")
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(btnTestLabel)
        layout.addView(space)
        layout.addView(btnTestReceipt)
        setContentView(layout)

        // معالجة الأمر لو التطبيق بيفتح لأول مرة من المتصفح
        handlePrintIntent(intent)
    }

    // معالجة الأمر لو التطبيق مفتوح بالفعل في الخلفية (singleTask)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    // استقبال الأوامر من رابط الموقع (الكوبري)
    private fun handlePrintIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW || intent.data == null) return
        
        val data: Uri = intent.data!!
        val type = data.getQueryParameter("type")
        
        if (type == "label") {
            val name = data.getQueryParameter("name") ?: ""
            val price = data.getQueryParameter("price") ?: ""
            val barcode = data.getQueryParameter("barcode") ?: ""
            statusText.text = "مستقبل أمر من الموقع: طباعة ملصق"
            printLabel(name, price, barcode)
        } else {
            val printPayload = data.getQueryParameter("text")
            if (!printPayload.isNullOrEmpty()) {
                statusText.text = "مستقبل أمر من الموقع: طباعة فاتورة"
                printReceipt(printPayload)
            }
        }
    }

    // --- محركات الطباعة الفعلية ---

    private fun printLabel(name: String, price: String, barcode: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    // إعداد الطابعة للملصق: 38 ملم عرض، 24 حرف
                    val printer = EscPosPrinter(printerConnection, 203, 38f, 24)
                    
                    // تصغير الباركود (width='1' height='8') ليتناسب مع 1x1.5 إنش
                    val formattedText = "[C]**$name**\n" +
                                        "[C]السعر : $price ج.م\n" +
                                        "[C]$barcode\n"
                    
                    printer.printFormattedText(formattedText)
                    Thread.sleep(400) // تأخير لضمان خروج الورقة بالكامل
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم طباعة الملصق بنجاح", Toast.LENGTH_SHORT).show()
                        statusText.text = "🟢 جاهز لاستقبال أوامر جديدة"
                    }
                } else {
                    runOnUiThread { statusText.text = "🔴 لا توجد طابعة بلوتوث مقترنة!" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { statusText.text = "🔴 خطأ تقني: ${e.message}" }
            }
        }.start()
    }

    private fun printReceipt(payloadText: String) {
        Thread {
            try {
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection != null) {
                    // إعداد الطابعة للفاتورة الكبيرة: 72 ملم مساحة طباعة، 48 حرف
                    val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                    
                    val formattedText = "[C]El Sayeh Store\n" +
                                        "[L]\n" +
                                        "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                        "[C]--------------------------------\n"
                    
                    printer.printFormattedText(formattedText)
                    Thread.sleep(400)
                    printer.disconnectPrinter() 
                    
                    runOnUiThread { 
                        Toast.makeText(this, "تم طباعة الفاتورة بنجاح", Toast.LENGTH_SHORT).show() 
                        statusText.text = "🟢 جاهز لاستقبال أوامر جديدة"
                    }
                } else {
                    runOnUiThread { statusText.text = "🔴 لا توجد طابعة بلوتوث مقترنة!" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { statusText.text = "🔴 خطأ تقني: ${e.message}" }
            }
        }.start()
    }
}