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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var printerInfoText: TextView
    private lateinit var printerCardLayout: LinearLayout

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
        }

        printerInfoText = TextView(this).apply {
            text = "اسم الطابعة: --"
            textSize = 13f
            setTextColor(Color.parseColor("#718096"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        val btnRefresh = Button(this).apply {
            text = "🔄 تحديث حالة الطابعة"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = createButtonDrawable("#3182CE")
            setPadding(20, 10, 20, 10)
            setOnClickListener { updatePrinterStatusUI() }
        }

        printerCardLayout.addView(statusText)
        printerCardLayout.addView(printerInfoText)
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

        // Assemble Layout
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

    @SuppressLint("MissingPermission")
    private fun updatePrinterStatusUI() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            statusText.text = "🔴 البلوتوث غير مدعوم على هذا الجهاز!"
            statusText.setTextColor(Color.parseColor("#E53E3E"))
            printerInfoText.text = "الحالة: خطأ في العتاد"
            return
        }

        if (!adapter.isEnabled) {
            statusText.text = "⚠️ البلوتوث مغلق! يرجى تشغيله"