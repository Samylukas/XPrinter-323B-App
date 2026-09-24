package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class MainActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. طلب الصلاحيات الأساسية (بلوتوث وكاميرا)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT, 
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.CAMERA
                ), 1)
            }
        } else if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }

        // 2. إعداد المتصفح الداخلي (WebView)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        
        // السماح للكاميرا بالعمل داخل المتصفح الداخلي بدون مشاكل
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
        
        // 3. ربط أوامر الجافا سكريبت بالأندرويد
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrinter")
        
        setContentView(webView)
        
        // 4. تحميل ملف الموقع
        webView.loadUrl("file:///android_asset/index.html")
    }

    // الكلاس المسؤول عن استقبال أوامر الطباعة من الموقع
    inner class WebAppInterface(private val mContext: Activity) {
        
        @JavascriptInterface
        fun printReceipt(payloadText: String) {
            val formattedText = "[C]<u><font size='big'>El Sayeh Store</font></u>\n" +
                                "[L]\n" +
                                "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                "[C]--------------------------------\n"
            
            // طباعة الفواتير: نفترض عرض البكرة 80 ملم (مساحة الطباعة 72 ملم) و 48 حرف في السطر
            executePrintJob(formattedText, 72f, 48)
        }

        @JavascriptInterface
        fun printLabel(name: String, price: String, barcode: String) {
            // تصغير الباركود (width='1' و height='8') ليتناسب مع الليبل
            val formattedText = "[C]<b>$name</b>\n" +
                                "[C]السعر : $price ج.م\n" +
                                "[C]<barcode type='128' width='1' height='8'>$barcode</barcode>\n"
            
            // طباعة الليبل: مقاس 1.5 إنش يعادل 38 ملم تقريباً، ونضبط عدد الحروف على 24 ليكون الخط واضحاً
            executePrintJob(formattedText, 38f, 24)
        }

        // محرك الطباعة الشامل (يستقبل النص، عرض الورقة، وعدد الحروف)
        private fun executePrintJob(textToPrint: String, paperWidthMM: Float, charsPerLine: Int) {
            Thread {
                try {
                    val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                    
                    if (printerConnection != null) {
                        // هنا يتم تحديد حجم الورق بناءً على الأمر (فاتورة أو ليبل)
                        val printer = EscPosPrinter(printerConnection, 203, paperWidthMM, charsPerLine)
                        printer.printFormattedText(textToPrint)
                        
                        // تأخير بسيط لضمان تفريغ ذاكرة الطابعة قبل قطع الاتصال
                        Thread.sleep(500)
                        printer.disconnectPrinter() 
                        
                        runOnUiThread { Toast.makeText(mContext, "تم الطباعة بنجاح", Toast.LENGTH_SHORT).show() }
                    } else {
                        runOnUiThread { Toast.makeText(mContext, "لم يتم العثور على أي طابعة مقترنة!", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMessage = e.message ?: "خطأ غير معروف"
                    runOnUiThread { Toast.makeText(mContext, "خطأ تقني: $errorMessage", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }
}