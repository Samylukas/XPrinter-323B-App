package com.example.printer

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تصميم واجهة التطبيق (زر الطباعة) برمجياً
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 200, 50, 50)

        val printBtn = Button(this)
        printBtn.text = "طباعة فاتورة تجريبية"
        printBtn.textSize = 24f
        
        layout.addView(printBtn)
        setContentView(layout)

        printBtn.setOnClickListener {
            printReceipt()
        }
    }

    private fun printReceipt() {
        try {
            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
            
            if (printerConnection != null) {
                val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                
                printer.printFormattedText(
                    "[C]<u><font size='big'>فاتورة تجريبية</font></u>\n" +
                    "[L]\n" +
                    "[C]================================\n" +
                    "[L]<b>الصنف</b>[R]<b>السعر</b>\n" +
                    "[L]وجبة سريعة[R]150 ج.م\n" +
                    "[C]--------------------------------\n" +
                    "[R]الاجمالي : 150 ج.م\n" +
                    "[L]\n" +
                    "[C]<barcode type='ean13' height='10'>1234567890128</barcode>\n"
                )
                Toast.makeText(this, "تم أمر الطباعة بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "يرجى ربط الطابعة بالبلوتوث أولاً", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "حدث خطأ في الطباعة", Toast.LENGTH_LONG).show()
        }
    }
}