package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var printerSpinner: Spinner
    private lateinit var printerCardLayout: LinearLayout
    private var pairedDevicesList = listOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissions()

        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 50)
            setBackgroundColor(Color.parseColor("#F4F6F9"))
        }

        // Header Title
        val titleText = TextView(this).apply {
            text = "🖨️ Print Bridge Pro"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E3C72"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // Status Card
        printerCardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            background = createCardDrawable("#FFFFFF", "#E2E8F0")
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "🔍 جاري فحص حالة البلوتوث والطابعة..."
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4A5568"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        printerSpinner = Spinner(this).apply {
            setPadding(10, 15, 10, 15)
            background = createCardDrawable("#F7FAFC", "#CBD5E0")
        }

        printerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (pairedDevicesList.isNotEmpty() && position < pairedDevicesList.size) {
                    val selectedDevice = pairedDevicesList[position]
                    saveSelectedPrinterMac(selectedDevice.address)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val btnRefresh = Button(this).apply {
            text = "🔄 تحديث الأجهزة المقترنة"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = createButtonDrawable("#3182CE")
            setPadding(20, 10, 20, 10)
            setOnClickListener { updatePrinterStatusUI() }
        }

        printerCardLayout.addView(statusText)
        printerCardLayout.addView(printerSpinner)
        printerCardLayout.addView(LinearLayout(this).apply {
            setPadding(0, 20, 0, 0)
            gravity = Gravity.CENTER
            addView(btnRefresh)
        })

        // Section: Label Test
        val labelCard = createSectionCard("🏷️ اختبار طباعة الملصقات (TSPL)")
        val labelInput = EditText(this).apply {
            hint = "أدخل رقم الباركود للتجربة..."
            textSize = 15f
            setBackgroundColor(Color.WHITE)
            setPadding(25, 20, 25, 20)
        }
        val btnTestLabel = Button(this).apply {
            text = "طباعة ملصق تجريبي بالعربي 🖨️"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = createButtonDrawable("#8E44AD")
            setOnClickListener {
                val code = labelInput.text.toString()
                if (code.isNotEmpty()) printLabelTSPL(code, "أنجم جرين بنادول سوبر", "150.00 ج.م", false)
                else showToast("⚠️ يرجى كتابة رقم الباركود أولاً")
            }
        }
        labelCard.addView(labelInput)
        labelCard.addView(btnTestLabel)

        // Section: Receipt Test
        val receiptCard = createSectionCard("🧾 اختبار طباعة الفواتير (Thermal)")
        val receiptInput = EditText(this).apply {
            hint = "أدخل نص التجربة للفاتورة..."
            textSize = 15f
            setBackgroundColor(Color.WHITE)
            setPadding(25, 20, 25, 20)
        }
        val btnTestReceipt = Button(this).apply {
            text = "طباعة فاتورة تجريبية 🧾"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = createButtonDrawable("#2980B9")
            setOnClickListener {
                val txt = receiptInput.text.toString()
                printReceiptDirect(if (txt.isNotEmpty()) txt else "اختبار الفاتورة\nالطباعة شغالة ممتاز!", false)
            }
        }
        receiptCard.addView(receiptInput)
        receiptCard.addView(btnTestReceipt)

        mainLayout.addView(titleText)
        mainLayout.addView(printerCardLayout)
        mainLayout.addView(labelCard)
        mainLayout.addView(receiptCard)

        scrollView.addView(mainLayout)
        setContentView(scrollView)

        updatePrinterStatusUI()
        handlePrintIntent(intent)
    }

    private fun createSectionCard(title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 30)
            background = createCardDrawable("#FFFFFF", "#E2E8F0")
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 30, 0, 0)
            layoutParams = params
        }
        val titleTv = TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2D3748"))
            setPadding(0, 0, 0, 20)
        }
        card.addView(titleTv)
        return card
    }

    private fun createCardDrawable(bgColor: String, strokeColor: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            setStroke(3, Color.parseColor(strokeColor))
            cornerRadius = 20f
        }
    }

    private fun createButtonDrawable(bgColor: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = 15f
        }
    }

    private fun saveSelectedPrinterMac(mac: String) {
        val sharedPref = getSharedPreferences("PrintBridgePrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("SELECTED_PRINTER_MAC", mac)
            apply()
        }
    }

    private fun getSavedPrinterMac(): String? {
        val sharedPref = getSharedPreferences("PrintBridgePrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("SELECTED_PRINTER_MAC", null)
    }

    @SuppressLint("MissingPermission")
    private fun updatePrinterStatusUI() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            statusText.text = "🔴 البلوتوث غير مدعوم على هذا الجهاز!"
            statusText.setTextColor(Color.parseColor("#E53E3E"))
            printerSpinner.visibility = View.GONE
            return
        }

        if (!adapter.isEnabled) {
            statusText.text = "⚠️ البلوتوث مغلق! يرجى تشغيله ثم التحديث"
            statusText.setTextColor(Color.parseColor("#DD6B20"))
            printerSpinner.visibility = View.GONE
            return
        }

        pairedDevicesList = adapter.bondedDevices?.toList() ?: emptyList()
        
        if (pairedDevicesList.isNotEmpty()) {
            statusText.text = "🟢 اختر الطابعة من القائمة:"
            statusText.setTextColor(Color.parseColor("#38A169"))
            printerSpinner.visibility = View.VISIBLE

            val deviceNames = pairedDevicesList.map { "${it.name}\n(${it.address})" }
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            printerSpinner.adapter = spinnerAdapter

            // تحديد الطابعة المحفوظة مسبقاً إن وجدت
            val savedMac = getSavedPrinterMac()
            if (savedMac != null) {
                val savedIndex = pairedDevicesList.indexOfFirst { it.address == savedMac }
                if (savedIndex >= 0) {
                    printerSpinner.setSelection(savedIndex)
                }
            }
        } else {
            statusText.text = "🔴 لم يتم العثور على أي أجهزة مقترنة!"
            statusText.setTextColor(Color.parseColor("#E53E3E"))
            printerSpinner.visibility = View.GONE
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 1)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    private fun handlePrintIntent(intent: Intent?) {
        if (intent == null || intent.data == null) return
        val data: Uri = intent.data!!

        if (data.scheme == "printbridge") {
            moveTaskToBack(true)

            val type = data.getQueryParameter("type")
            if (type == "label") {
                val barcode = data.getQueryParameter("barcode") ?: "0000"
                val name = data.getQueryParameter("name") ?: ""
                val price = data.getQueryParameter("price") ?: ""
                printLabelTSPL(barcode, name, price, true)
            } else {
                val text = data.getQueryParameter("text") ?: "TEST RECEIPT"
                printReceiptDirect(text, true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDirectToDevice(device: BluetoothDevice): BluetoothSocket {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter?.cancelDiscovery()

        return try {
            val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            socket.connect()
            socket
        } catch (e1: Exception) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                socket
            } catch (e2: Exception) {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val socket = method.invoke(device, 1) as BluetoothSocket
                socket.connect()
                socket
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSelectedPrinter(): BluetoothDevice? {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) return null
        
        val bondedDevices = adapter.bondedDevices ?: return null
        val savedMac = getSavedPrinterMac()
        
        // البحث عن الطابعة المحددة مسبقاً، وإلا اختيار أول جهاز كبديل
        return bondedDevices.find { it.address == savedMac } ?: bondedDevices.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun printLabelTSPL(barcode: String, prodName: String, prodPrice: String, autoClose: Boolean) {
        showToast("⏳ جاري إرسال الملصق...")
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val device = getSelectedPrinter()
                if (device != null) {
                    socket = connectDirectToDevice(device)
                    val out = socket.outputStream

                    val labelWidthPx = 304
                    val labelHeightPx = 200
                    val bitmap = Bitmap.createBitmap(labelWidthPx, labelHeightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 24f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val nameLayout = StaticLayout.Builder.obtain(prodName, 0, prodName.length, namePaint, labelWidthPx - 10)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(5f, 5f)
                    nameLayout.draw(canvas)
                    canvas.restore()

                    val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 26f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val priceLayout = StaticLayout.Builder.obtain(prodPrice, 0, prodPrice.length, pricePaint, labelWidthPx - 10)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(5f, (labelHeightPx - priceLayout.height - 5).toFloat())
                    priceLayout.draw(canvas)
                    canvas.restore()

                    val headerCommand = "SIZE 38 mm,25 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\n"
                    out.write(headerCommand.toByteArray())

                    val bmpBytes = bitmapToTsplBitmapCommand(bitmap, 0, 0)
                    out.write(bmpBytes)

                    val barcodeCommand = "BARCODE 30,55,\"128\",60,0,0,2,2,\"$barcode\"\r\nTEXT 100,120,\"2\",0,1,1,\"$barcode\"\r\nPRINT 1,1\r\n"
                    out.write(barcodeCommand.toByteArray())

                    out.flush()
                    Thread.sleep(1000)
                    showToast("✅ تمت طباعة الملصق بنجاح!")
                } else {
                    showToast("❌ يرجى تحديد الطابعة من القائمة أولاً!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("❌ خطأ بالاتصال: تأكد من تشغيل الطابعة المحددة")
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
                if (autoClose) {
                    runOnUiThread { finish() }
                }
            }
        }.start()
    }

    private fun bitmapToTsplBitmapCommand(bitmap: Bitmap, x: Int, y: Int): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val stream = java.io.ByteArrayOutputStream()

        val commandHead = "BITMAP $x,$y,$widthBytes,$height,0,"
        stream.write(commandHead.toByteArray())

        val imgData = ByteArray(widthBytes * height)
        var byteIndex = 0

        for (h in 0 until height) {
            for (w in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val pxX = w * 8 + bit
                    if (pxX < width) {
                        val color = bitmap.getPixel(pxX, h)
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val blue = Color.blue(color)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * blue).toInt()

                        if (luminance >= 128) {
                            b = b or (1 shl (7 - bit))
                        }
                    } else {
                        b = b or (1 shl (7 - bit))
                    }
                }
                imgData[byteIndex++] = b.toByte()
            }
        }
        stream.write(imgData)
        stream.write("\r\n".toByteArray())
        return stream.toByteArray()
    }

    @SuppressLint("MissingPermission")
    private fun printReceiptDirect(payloadText: String, autoClose: Boolean) {
        showToast("⏳ جاري إرسال الفاتورة...")
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val device = getSelectedPrinter()

                if (device != null) {
                    socket = connectDirectToDevice(device)
                    val out = socket.outputStream

                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 32f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    val formattedText = "\n$payloadText\n"
                    val printWidth = 576

                    val staticLayout = StaticLayout.Builder.obtain(formattedText, 0, formattedText.length, textPaint, printWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()

                    val bitmap = Bitmap.createBitmap(printWidth, staticLayout.height + 40, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.translate(0f, 20f)
                    staticLayout.draw(canvas)

                    out.write(byteArrayOf(0x1B, 0x40))

                    val bmpWidth = bitmap.width
                    val bmpHeight = bitmap.height
                    var offset = 0

                    while (offset < bmpHeight) {
                        val chunkHeight = if (bmpHeight - offset > 255) 255 else bmpHeight - offset
                        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
                        val xL = (bmpWidth / 8) % 256
                        val xH = (bmpWidth / 8) / 256
                        out.write(byteArrayOf(xL.toByte(), xH.toByte()))
                        val yL = chunkHeight % 256
                        val yH = chunkHeight / 256
                        out.write(byteArrayOf(yL.toByte(), yH.toByte()))

                        val rowBytes = ByteArray((bmpWidth / 8) * chunkHeight)
                        var index = 0
                        for (y in 0 until chunkHeight) {
                            for (x in 0 until bmpWidth step 8) {
                                var b = 0
                                for (k in 0..7) {
                                    if (x + k < bmpWidth) {
                                        val color = bitmap.getPixel(x + k, offset + y)
                                        val r = Color.red(color)
                                        val g = Color.green(color)
                                        val bColor = Color.blue(color)
                                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bColor).toInt()
                                        if (luminance < 128) {
                                            b = b or (1 shl (7 - k))
                                        }
                                    }
                                }
                                rowBytes[index++] = b.toByte()
                            }
                        }
                        out.write(rowBytes)
                        offset += chunkHeight
                    }

                    out.write(byteArrayOf(0x1B, 0x64, 0x05))
                    out.flush()
                    Thread.sleep(1500)
                    showToast("✅ تم طباعة الفاتورة بنجاح!")
                } else {
                    showToast("❌ يرجى تحديد الطابعة من القائمة أولاً!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("❌ خطأ بالاتصال: تأكد من تشغيل الطابعة المحددة")
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
                if (autoClose) {
                    runOnUiThread { finish() }
                }
            }
        }.start()
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}