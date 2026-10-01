package com.tenantmanagement

import java.time.YearMonth

internal data class RentalTotals(
    val expectedRent: Long = 0,
    val rentBilled: Long = 0,
    val electricityBilled: Long = 0,
    val paid: Long = 0,
    val due: Long = 0,
    val tenantMonths: Int = 0
) {
    val totalBilled: Long get() = rentBilled + electricityBilled

    operator fun plus(other: RentalTotals) = RentalTotals(
        expectedRent + other.expectedRent,
        rentBilled + other.rentBilled,
        electricityBilled + other.electricityBilled,
        paid + other.paid,
        due + other.due,
        tenantMonths + other.tenantMonths
    )
}

internal data class FlatReport(val flat: String, val tenants: List<Tenant>, val totals: RentalTotals)
internal data class TenantReport(val tenants: List<Tenant>, val totals: RentalTotals) {
    val primary: Tenant get() = tenants.first()
}

internal fun reportTotals(
    data: RentalData,
    buildingId: String?,
    flat: String?,
    yearly: Boolean,
    year: Int,
    month: YearMonth
): RentalTotals {
    val relevantTenants = data.tenants.filter { tenant ->
        (buildingId == null || tenant.buildingId == buildingId) &&
            (flat == null || tenant.flat == flat)
    }
    val tenantById = relevantTenants.associateBy { it.id }
    val relevantBills = data.bills.filter { bill ->
        val periodMatches = if (yearly) bill.month.startsWith("$year-") else bill.month == month.toString()
        periodMatches && bill.tenantId in tenantById
    }
    val monthCount = if (yearly) 12 else 1
    val expected = (0 until monthCount).sumOf { offset ->
        val occupancyMonth = if (yearly) YearMonth.of(year, 1).plusMonths(offset.toLong()) else month
        relevantTenants.filter { tenantOccupiesMonth(it, occupancyMonth) }.sumOf { it.rent }
    }
    val tenantMonths = (0 until monthCount).sumOf { offset ->
        val occupancyMonth = if (yearly) YearMonth.of(year, 1).plusMonths(offset.toLong()) else month
        relevantTenants.count { tenantOccupiesMonth(it, occupancyMonth) }
    }
    return RentalTotals(
        expectedRent = expected,
        rentBilled = relevantBills.sumOf { it.rent },
        electricityBilled = relevantBills.sumOf { it.electricity },
        paid = relevantBills.sumOf { it.paid },
        due = relevantBills.sumOf { it.due },
        tenantMonths = tenantMonths
    )
}

internal fun reportTotalsForTenants(
    data: RentalData,
    tenants: List<Tenant>,
    yearly: Boolean,
    year: Int,
    month: YearMonth
): RentalTotals {
    val tenantIds = tenants.map { it.id }.toSet()
    val monthCount = if (yearly) 12 else 1
    val expected = (0 until monthCount).sumOf { offset ->
        val occupancyMonth = if (yearly) YearMonth.of(year, 1).plusMonths(offset.toLong()) else month
        tenants.filter { tenantOccupiesMonth(it, occupancyMonth) }.sumOf { it.rent }
    }
    val tenantMonths = (0 until monthCount).sumOf { offset ->
        val occupancyMonth = if (yearly) YearMonth.of(year, 1).plusMonths(offset.toLong()) else month
        tenants.count { tenantOccupiesMonth(it, occupancyMonth) }
    }
    val bills = data.bills.filter { bill ->
        bill.tenantId in tenantIds &&
            if (yearly) bill.month.startsWith("$year-") else bill.month == month.toString()
    }
    return RentalTotals(
        expectedRent = expected,
        rentBilled = bills.sumOf { it.rent },
        electricityBilled = bills.sumOf { it.electricity },
        paid = bills.sumOf { it.paid },
        due = bills.sumOf { it.due },
        tenantMonths = tenantMonths
    )
}

internal fun tenancyTouchesPeriod(tenant: Tenant, yearly: Boolean, year: Int, month: YearMonth): Boolean =
    if (yearly) (1..12).any { tenantOccupiesMonth(tenant, YearMonth.of(year, it)) }
    else tenantOccupiesMonth(tenant, month)

internal fun tenantOccupiesMonth(tenant: Tenant, month: YearMonth): Boolean {
    val start = runCatching { java.time.LocalDate.parse(tenant.startDate) }.getOrNull() ?: return false
    val end = tenant.endDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()
    return !start.isAfter(monthEnd) && (end == null || !end.isBefore(monthStart))
}
