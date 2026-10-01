package com.tenantmanagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class RentalMathTest {
    private fun tenant(id: String = "t1", rent: Long = 5000, start: String = "2025-01-15", end: String? = null) =
        Tenant(id, "Asha", "", "b1", "101", rent, "", "", start, endDate = end, active = end == null)

    @Test
    fun billAddsRentAndElectricityAtTheBillRate() {
        val bill = RentBill("b", "t1", "2025-03", units = 50, rent = 5000)
        assertEquals(600, bill.electricity)
        assertEquals(5600, bill.total)
        assertEquals(5600, bill.due)
    }

    @Test
    fun partialPaymentsReduceDueAndNeverGoNegative() {
        val bill = RentBill("b", "t1", "2025-03", 10, 1000, rate = 10)
        val part = bill.copy(payments = listOf(Payment("p1", "2025-03-05", 400)))
        assertEquals(700, part.due)
        val over = part.copy(payments = part.payments + Payment("p2", "2025-03-09", 900))
        assertEquals(0, over.due)
        assertEquals(1300, over.paid)
    }

    @Test
    fun oldBillRateIsNotAffectedByDefaultRate() {
        assertEquals(1500, RentBill("b", "t", "2025-01", 100, 0, rate = 15).electricity)
    }

    @Test
    fun occupancyCoversPartialMonthsAndMoveOutMonth() {
        val t = tenant(start = "2025-01-15", end = "2025-03-10")
        assertFalse(tenantOccupiesMonth(t, YearMonth.of(2024, 12)))
        assertTrue(tenantOccupiesMonth(t, YearMonth.of(2025, 1)))
        assertTrue(tenantOccupiesMonth(t, YearMonth.of(2025, 3)))
        assertFalse(tenantOccupiesMonth(t, YearMonth.of(2025, 4)))
    }

    @Test
    fun monthlyTotalsSplitPaidAndOutstanding() {
        val data = RentalData(
            buildings = listOf(Building("b1", "Tower", "", listOf("101"))),
            tenants = listOf(tenant()),
            bills = listOf(RentBill("x", "t1", "2025-02", 10, 5000, 12, listOf(Payment("p", "2025-02-03", 3000))))
        )
        val totals = reportTotals(data, null, null, false, 2025, YearMonth.of(2025, 2))
        assertEquals(5000, totals.expectedRent)
        assertEquals(120, totals.electricityBilled)
        assertEquals(3000, totals.paid)
        assertEquals(2120, totals.due)
    }

    @Test
    fun aadhaarVerifierIsStableForOneKeyAndDiffersAcrossKeys() {
        val keyA = ByteArray(32) { 1 }
        val keyB = ByteArray(32) { 2 }
        assertEquals(aadhaarFingerprint("123412341234", keyA), aadhaarFingerprint("123412341234", keyA))
        assertFalse(aadhaarFingerprint("123412341234", keyA) == aadhaarFingerprint("123412341234", keyB))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aadhaarMustBeTwelveDigits() {
        aadhaarFingerprint("1234", ByteArray(32))
    }
}
