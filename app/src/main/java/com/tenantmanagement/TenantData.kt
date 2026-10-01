package com.tenantmanagement

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val DEFAULT_ELECTRICITY_RATE = 12L

data class Building(
    val id: String,
    val name: String,
    val address: String,
    val flats: List<String>
)

data class Tenant(
    val id: String,
    val name: String,
    val phone: String,
    val buildingId: String,
    val flat: String,
    val rent: Long,
    val family: String,
    val documents: String,
    val startDate: String,
    val documentFiles: List<DocumentFile> = emptyList(),
    val photoUri: String? = null,
    val familyMembers: List<FamilyMember> = emptyList(),
    val endDate: String? = null,
    val active: Boolean = true,
    val identityKey: String = "",
    val maskedAadhaar: String = ""
)

/** [uri] is "drive:<fileId>" once uploaded to the owner's Google Drive, or a local content URI before that. */
data class DocumentFile(val uri: String, val name: String)

data class FamilyMember(
    val name: String,
    val relationship: String,
    val photoUri: String,
    val documentFiles: List<DocumentFile>
)

data class Payment(val id: String, val date: String, val amount: Long, val note: String = "")

data class RentBill(
    val id: String,
    val tenantId: String,
    val month: String,
    val units: Long,
    val rent: Long,
    val rate: Long = DEFAULT_ELECTRICITY_RATE,
    val payments: List<Payment> = emptyList()
) {
    val paid: Long get() = payments.sumOf { it.amount }
    val electricity: Long get() = units * rate
    val total: Long get() = rent + electricity
    val due: Long get() = (total - paid).coerceAtLeast(0)
}

data class RentalData(
    val buildings: List<Building> = emptyList(),
    val tenants: List<Tenant> = emptyList(),
    val bills: List<RentBill> = emptyList()
)

/**
 * Keyed hash of an Aadhaar number, used only to detect duplicate registrations. The key is a random secret kept in the
 * owner's private cloud record, so the same number gives the same verifier on every device the owner signs in on.
 */
fun aadhaarFingerprint(aadhaar: String, key: ByteArray): String {
    require(aadhaar.matches(Regex("[0-9]{12}"))) { "Aadhaar number must contain exactly 12 digits." }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(aadhaar.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun today(): String = LocalDate.now().toString()
internal fun currentMonth(): String = LocalDate.now().toString().take(7)
