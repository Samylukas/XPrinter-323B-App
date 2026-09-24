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

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        
        // إعطاء صلاحية الكاميرا مباشرة للويب (لحل مشكلة NotAllowedError)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
        
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrinter")
        
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

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

        private fun executePrintJob(textToPrint: String) {
            Thread {
                try {
                    val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                    if (printerConnection != null) {
                        val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                        printer.printFormattedText(textToPrint)
                        
                        // تأخير بسيط لضمان تفريغ الذاكرة المؤقتة للطابعة قبل قطع الاتصال
                        Thread.sleep(300)
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