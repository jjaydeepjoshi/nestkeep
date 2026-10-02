package com.tenantmanagement

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything printed on a monthly rent receipt. Built from the bill, so it is plain data and easy to test. */
data class ReceiptData(
    val receiptNo: String,
    val issuedOn: String,
    val ownerName: String,
    val buildingName: String,
    val buildingAddress: String,
    val tenantName: String,
    val flat: String,
    val periodLabel: String,
    val rent: Long,
    val units: Long,
    val rate: Long,
    val electricity: Long,
    val total: Long,
    val paid: Long,
    val due: Long,
    val payments: List<Payment>
)

internal fun buildReceipt(bill: RentBill, tenant: Tenant, building: Building, issuedOn: String = today()): ReceiptData {
    val period = runCatching { YearMonth.parse(bill.month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }
        .getOrDefault(bill.month)
    return ReceiptData(
        receiptNo = "NK-${bill.month.replace("-", "")}-${bill.id.filter { it.isLetterOrDigit() }.take(6).uppercase()}",
        issuedOn = issuedOn,
        ownerName = building.ownerName.trim(),
        buildingName = building.name,
        buildingAddress = building.address,
        tenantName = tenant.name,
        flat = tenant.flat.trim(),
        periodLabel = period,
        rent = bill.rent, units = bill.units, rate = bill.rate, electricity = bill.electricity,
        total = bill.total, paid = bill.paid, due = bill.due,
        payments = bill.payments.sortedBy { it.date }
    )
}

private val ones = arrayOf(
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
    "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
)
private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

private fun below100(n: Int) = if (n < 20) ones[n] else tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""

private fun below1000(n: Int): String {
    val hundreds = if (n >= 100) ones[n / 100] + " Hundred" else ""
    val rest = below100(n % 100)
    return listOf(hundreds, rest).filter { it.isNotEmpty() }.joinToString(" ")
}

/** Indian-system amount in words, e.g. 125000 -> "Rupees One Lakh Twenty Five Thousand Only". */
internal fun amountInWords(amount: Long): String {
    if (amount <= 0) return "Rupees Zero Only"
    var n = amount
    val parts = mutableListOf<String>()
    val crore = n / 10_000_000; n %= 10_000_000
    val lakh = n / 100_000; n %= 100_000
    val thousand = n / 1000; n %= 1000
    if (crore > 0) parts += (if (crore >= 1000) amountInWords(crore).removePrefix("Rupees ").removeSuffix(" Only") else below1000(crore.toInt())) + " Crore"
    if (lakh > 0) parts += below100(lakh.toInt()) + " Lakh"
    if (thousand > 0) parts += below100(thousand.toInt()) + " Thousand"
    if (n > 0) parts += below1000(n.toInt())
    return "Rupees " + parts.joinToString(" ") + " Only"
}

private fun rupees(amount: Long) = "Rs. " + "%,d".format(Locale.ENGLISH, amount)

/** Writes a one-page A4 receipt with a blank signature line for the owner to sign by hand after printing. */
internal fun writeReceiptPdf(receipt: ReceiptData, output: OutputStream) {
    val width = 595
    val height = 842
    val margin = 48f
    val right = width - margin
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
    val canvas = page.canvas

    fun paint(size: Float, bold: Boolean = false, color: Int = Color.rgb(25, 35, 30), align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    val green = Color.rgb(24, 99, 70)
    val grey = Color.rgb(95, 105, 100)
    val line = Paint().apply { color = Color.rgb(200, 210, 204); strokeWidth = 1f }

    // Border and heading
    canvas.drawRect(margin - 16, 32f, right + 16, height - 32f, Paint().apply {
        style = Paint.Style.STROKE; color = green; strokeWidth = 2f
    })
    canvas.drawText("RENT RECEIPT", width / 2f, 90f, paint(26f, true, green, Paint.Align.CENTER))
    canvas.drawText(receipt.buildingName, width / 2f, 114f, paint(13f, true, align = Paint.Align.CENTER))
    var y = 130f
    wrap(receipt.buildingAddress, paint(10.5f, color = grey), width - 2 * margin).forEach {
        canvas.drawText(it, width / 2f, y, paint(10.5f, color = grey, align = Paint.Align.CENTER))
        y += 14f
    }
    y = maxOf(y, 140f) + 10f
    canvas.drawLine(margin, y, right, y, line)
    y += 26f

    // Receipt number / date
    canvas.drawText("Receipt No: ${receipt.receiptNo}", margin, y, paint(11f, true))
    canvas.drawText("Date: ${receipt.issuedOn}", right, y, paint(11f, true, align = Paint.Align.RIGHT))
    y += 34f

    // Statement
    val flatPart = if (receipt.flat.isNotEmpty()) "Flat ${receipt.flat}, " else ""
    val statement = "Received with thanks from ${receipt.tenantName} a sum of ${rupees(receipt.paid)} " +
        "(${amountInWords(receipt.paid)}) towards the rent and electricity charges for the month of " +
        "${receipt.periodLabel} for ${flatPart}${receipt.buildingName}."
    wrap(statement, paint(12.5f), width - 2 * margin).forEach {
        canvas.drawText(it, margin, y, paint(12.5f)); y += 19f
    }
    y += 14f

    // Charges table
    fun row(label: String, value: String, bold: Boolean = false, color: Int = Color.rgb(25, 35, 30)) {
        canvas.drawText(label, margin + 8, y, paint(12f, bold, color))
        canvas.drawText(value, right - 8, y, paint(12f, bold, color, Paint.Align.RIGHT))
        y += 24f
    }
    val top = y - 18f
    canvas.drawRect(margin, top, right, y + 2f, Paint().apply { color = Color.rgb(232, 242, 237) })
    canvas.drawText("Description", margin + 8, y - 2f, paint(11f, true, green))
    canvas.drawText("Amount", right - 8, y - 2f, paint(11f, true, green, Paint.Align.RIGHT))
    y += 24f
    row("Monthly rent", rupees(receipt.rent))
    row("Electricity (${receipt.units} units x Rs. ${receipt.rate})", rupees(receipt.electricity))
    canvas.drawLine(margin, y - 14f, right, y - 14f, line)
    row("Total for the month", rupees(receipt.total), bold = true)
    row("Amount received", rupees(receipt.paid), bold = true, color = green)
    row("Balance due", rupees(receipt.due), bold = receipt.due > 0, color = if (receipt.due > 0) Color.rgb(170, 80, 20) else grey)
    canvas.drawRect(margin, top, right, y - 14f, Paint().apply { style = Paint.Style.STROKE; color = line.color; strokeWidth = 1f })
    y += 6f

    // Payment history
    if (receipt.payments.isNotEmpty()) {
        canvas.drawText("Payments received", margin, y, paint(11f, true, green)); y += 18f
        receipt.payments.take(10).forEach {
            canvas.drawText("${it.date}${if (it.note.isNotBlank()) "  -  ${it.note}" else ""}", margin + 8, y, paint(10.5f, color = grey))
            canvas.drawText(rupees(it.amount), right - 8, y, paint(10.5f, align = Paint.Align.RIGHT))
            y += 16f
        }
        y += 4f
    }

    // Tenant details
    y += 8f
    canvas.drawText("Tenant: ${receipt.tenantName}", margin, y, paint(11f)); y += 16f
    if (receipt.flat.isNotEmpty()) canvas.drawText("Flat: ${receipt.flat}", margin, y, paint(11f))

    // Signature block, anchored to the bottom so there is room to sign above the line
    val sigLineY = height - 130f
    canvas.drawText("Received by (Owner / Landlord)", right, sigLineY - 70f, paint(10f, color = grey, align = Paint.Align.RIGHT))
    canvas.drawLine(right - 190f, sigLineY, right, sigLineY, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
    canvas.drawText(receipt.ownerName, right, sigLineY + 18f, paint(12.5f, true, align = Paint.Align.RIGHT))
    canvas.drawText("Signature of owner", right, sigLineY + 33f, paint(9.5f, color = grey, align = Paint.Align.RIGHT))
    canvas.drawText(
        "This receipt is generated by NestKeep and is valid when signed by the owner.",
        width / 2f, height - 48f, paint(9f, color = grey, align = Paint.Align.CENTER)
    )

    document.finishPage(page)
    try {
        document.writeTo(output)
    } finally {
        document.close()
    }
}

private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val lines = mutableListOf<String>()
    var current = ""
    for (word in text.trim().split(Regex("\\s+"))) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
        else { lines += current; current = word }
    }
    if (current.isNotEmpty()) lines += current
    return lines
}
