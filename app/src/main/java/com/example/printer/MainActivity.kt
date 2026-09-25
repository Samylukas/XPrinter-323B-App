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
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.CAMERA), 1)
            }
        } else if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = true
        
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
        }
        
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrinter")
        setContentView(webView)
        
        // الرابط الصحيح والكامل لمكان الملف داخل مستودعك لتجنب خطأ 404
webView.loadUrl("https://samylukas.github.io/XPrinter-323B-App/")
    }

    inner class WebAppInterface(private val mContext: Activity) {
        
        @JavascriptInterface
        fun getPairedPrinters(): String {
            return try {
                val p = BluetoothPrintersConnections.selectFirstPaired()
                if (p != null) "متصل بـ: ${p.device.name}" else "لا توجد طابعة مقترنة"
            } catch (e: Exception) { "خطأ بلوتوث" }
        }

        @JavascriptInterface
        fun printTestPage(paperWidth: Float) {
            val text = "[C]<b>TEST PAGE - صفحة اختبار</b>\n[C]الطابعة تعمل بنجاح!\n[C]عرض الورق المبرمج: $paperWidth mm\n"
            executePrintJob(text, paperWidth)
        }

        @JavascriptInterface
        fun printReceipt(payloadText: String, paperWidth: Float) {
            val formattedText = "[C]<u><font size='big'>El Sayeh Store</font></u>\n[L]\n[L]${payloadText.replace("\n", "\n[L]")}\n[C]--------------------------------\n"
            executePrintJob(formattedText, paperWidth)
        }

        @JavascriptInterface
        fun printCustomLabel(name: String, price: String, barcode: String, paperWidth: Float, barcodeWidth: Int, barcodeHeight: Int, showName: Boolean, showPrice: Boolean) {
            var formattedText = ""
            if (showName) formattedText += "[C]<b>$name</b>\n"
            if (showPrice) formattedText += "[C]السعر: $price ج.م\n"
            
            formattedText += "[C]<barcode type='128' width='$barcodeWidth' height='$barcodeHeight'>$barcode</barcode>\n"
            
            executePrintJob(formattedText, paperWidth)
        }

        private fun executePrintJob(textToPrint: String, paperWidthMM: Float) {
            Thread {
                try {
                    val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                    if (printerConnection != null) {
                        val printer = EscPosPrinter(printerConnection, 203, paperWidthMM, 48)
                        printer.printFormattedText(textToPrint)
                        Thread.sleep(400)
                        printer.disconnectPrinter() 
                        runOnUiThread { Toast.makeText(mContext, "الطباعة تمت", Toast.LENGTH_SHORT).show() }
                    } else {
                        runOnUiThread { Toast.makeText(mContext, "الطابعة غير مقترنة", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val err = e.message ?: "مجهول"
                    runOnUiThread { Toast.makeText(mContext, "خطأ: $err", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }
}