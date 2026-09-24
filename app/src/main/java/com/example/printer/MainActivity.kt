package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
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
        
        // طلب صلاحيات البلوتوث والكاميرا للهواتف الحديثة
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

        // إنشاء متصفح داخلي (WebView) ليعرض الموقع كأنه تطبيق
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        
        // ربط الموقع بكود الطباعة في الأندرويد باسم "AndroidPrinter"
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrinter")
        
        setContentView(webView)
        
        // تحميل الموقع الخاص بك من المجلد الداخلي assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    // الكلاس المسؤول عن استقبال الأوامر من الجافا سكريبت في موقعك
    inner class WebAppInterface(private val mContext: Activity) {
        
        @JavascriptInterface
        fun printReceipt(payloadText: String) {
            val formattedText = "[C]<u><font size='big'>El Sayeh Store</font></u>\n" +
                                "[L]\n" +
                                "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                "[C]--------------------------------\n"
            executePrintJob(formattedText)
        }

        @JavascriptInterface
        fun printLabel(name: String, price: String, barcode: String) {
            val formattedText = "[C]<b>$name</b>\n" +
                                "[C]السعر : $price ج.م\n" +
                                "[C]<barcode type='128' width='2' height='10'>$barcode</barcode>\n"
            executePrintJob(formattedText)
        }

        // محرك الطباعة في الخلفية (لمنع التهنيج)
        private fun executePrintJob(textToPrint: String) {
            Thread {
                try {
                    val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                    if (printerConnection != null) {
                        val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                        printer.printFormattedText(textToPrint)
                        
                        // قطع الاتصال فورا ليكون جاهزا للطباعة التالية
                        printer.disconnectPrinter() 
                        
                        runOnUiThread { Toast.makeText(mContext, "تم الطباعة بنجاح", Toast.LENGTH_SHORT).show() }
                    } else {
                        runOnUiThread { Toast.makeText(mContext, "يرجى ربط الطابعة بالبلوتوث أولاً", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread { Toast.makeText(mContext, "خطأ في الطباعة، تأكد من تشغيل الطابعة", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }
}