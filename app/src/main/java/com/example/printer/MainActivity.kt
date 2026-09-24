if (Intent.ACTION_VIEW == action && data != null) {
            val type = data.getQueryParameter("type")
            
            // لو الموقع طلب طباعة ليبل باركود
            if (type == "label") {
                val name = data.getQueryParameter("name") ?: ""
                val price = data.getQueryParameter("price") ?: ""
                val barcode = data.getQueryParameter("barcode") ?: ""
                
                printBtn.text = "جاري طباعة ملصق الباركود..."
                printLabelFromWeb(name, price, barcode)
                
            } else {
                // لو الموقع طلب طباعة فاتورة عادية
                val printPayload = data.getQueryParameter("text")
                if (!printPayload.isNullOrEmpty()) {
                    printBtn.text = "جاري طباعة الفاتورة..."
                    printReceiptFromWeb(printPayload)
                }
            }
        }

        printBtn.setOnClickListener {
            printReceiptFromWeb("فاتورة تجريبية يدوية")
        }
    }

    // دالة مخصصة لطباعة الفواتير
    private fun printReceiptFromWeb(payloadText: String) {
        try {
            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
            if (printerConnection != null) {
                val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                val formattedText = "[C]<u><font size='big'>El Sayeh Store</font></u>\n" +
                                    "[L]\n" +
                                    "[L]${payloadText.replace("\n", "\n[L]")}\n" +
                                    "[C]--------------------------------\n"
                printer.printFormattedText(formattedText)
                Toast.makeText(this, "تم طباعة الفاتورة", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "يرجى ربط الطابعة بالبلوتوث", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "حدث خطأ أثناء الطباعة", Toast.LENGTH_LONG).show()
        }
    }

    // دالة جديدة مخصصة لطباعة ملصق الباركود (الليبل)
    private fun printLabelFromWeb(name: String, price: String, barcode: String) {
        try {
            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
            if (printerConnection != null) {
                // هنا نضبط الطابعة، والسر في تاج الباركود
                val printer = EscPosPrinter(printerConnection, 203, 72f, 48)
                val formattedText = "[C]<b>$name</b>\n" +
                                    "[C]السعر : $price ج.م\n" +
                                    "[C]<barcode type='128' width='2' height='10'>$barcode</barcode>\n"
                
                printer.printFormattedText(formattedText)
                Toast.makeText(this, "تم طباعة الباركود", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "يرجى ربط الطابعة بالبلوتوث", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "حدث خطأ أثناء طباعة الباركود", Toast.LENGTH_LONG).show()
        }
    }
}