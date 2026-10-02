package com.tenantmanagement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTest {
    @Test
    fun amountsAreWrittenInTheIndianSystem() {
        assertEquals("Rupees Zero Only", amountInWords(0))
        assertEquals("Rupees Five Thousand Only", amountInWords(5000))
        assertEquals("Rupees Twelve Thousand Five Hundred Fifty Only", amountInWords(12550))
        assertEquals("Rupees One Lakh Twenty Five Thousand Only", amountInWords(125000))
        assertEquals("Rupees Two Crore Three Lakh Four Only", amountInWords(20300004))
    }

    @Test
    fun receiptCarriesOwnerPeriodAndAmounts() {
        val building = Building("b1", "Sunrise", "MG Road", listOf("101"), ownerName = " Ramesh Shah ")
        val tenant = Tenant("t1", "Asha", "", "b1", " 101 ", 5000, "", "", "2026-01-01")
        val bill = RentBill("abc-123-def", "t1", "2026-09", units = 50, rent = 5000, rate = 12,
            payments = listOf(Payment("p2", "2026-09-20", 100), Payment("p1", "2026-09-05", 5000)))
        val r = buildReceipt(bill, tenant, building, issuedOn = "2026-10-02")
        assertEquals("Ramesh Shah", r.ownerName)
        assertEquals("September 2026", r.periodLabel)
        assertEquals("101", r.flat)
        assertEquals(5100, r.paid)
        assertEquals(500, r.due)
        assertEquals("NK-202609-ABC123", r.receiptNo)
        assertEquals("2026-09-05", r.payments.first().date)
        assertEquals("PART PAID", r.status)
    }

    @Test
    fun statusReflectsPaidPartAndUnpaid() {
        val building = Building("b1", "Sunrise", "", listOf("101"), "Owner")
        val tenant = Tenant("t1", "Asha", "", "b1", "101", 5000, "", "", "2026-01-01")
        val unpaid = RentBill("x", "t1", "2026-09", 0, 5000)
        assertEquals("UNPAID", buildReceipt(unpaid, tenant, building).status)
        val part = unpaid.copy(payments = listOf(Payment("p", "2026-09-05", 2000)))
        val r = buildReceipt(part, tenant, building)
        assertEquals("PART PAID", r.status)
        assertEquals(3000, r.due)
        val over = unpaid.copy(payments = listOf(Payment("p", "2026-09-05", 6000)))
        assertEquals(1000, buildReceipt(over, tenant, building).extra)
    }
}
