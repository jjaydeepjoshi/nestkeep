package com.tenantmanagement

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Reads (and clears) the data older versions kept in this device's SharedPreferences, so it can be moved to the cloud. */
class LegacyStore(context: Context) {
    private val preferences = context.getSharedPreferences("tenant_manager_data", Context.MODE_PRIVATE)

    fun hasData(): Boolean = preferences.getString("data", null) != null

    fun clear() {
        preferences.edit().remove("data").apply()
    }

    fun load(): RentalData {
        val raw = preferences.getString("data", null) ?: return RentalData()
        val root = JSONObject(raw)
        val buildings = root.getJSONArray("buildings").toList { item ->
            val v = item as JSONObject
            Building(v.getString("id"), v.getString("name"), v.getString("address"), v.getJSONArray("flats").toList { it as String })
        }
        val tenants = root.getJSONArray("tenants").toList { item ->
            val v = item as JSONObject
            Tenant(
                id = v.getString("id"), name = v.getString("name"), phone = v.getString("phone"),
                buildingId = v.getString("buildingId"), flat = v.getString("flat"), rent = v.getLong("rent"),
                family = v.getString("family"), documents = v.getString("documents"), startDate = v.getString("startDate"),
                documentFiles = v.optJSONArray("documentFiles")?.toList { readDoc(it as JSONObject) }.orEmpty(),
                photoUri = v.optString("photoUri").takeIf(String::isNotEmpty),
                familyMembers = v.optJSONArray("familyMembers")?.toList { m ->
                    val member = m as JSONObject
                    FamilyMember(
                        member.getString("name"), member.getString("relationship"), member.getString("photoUri"),
                        member.optJSONArray("documentFiles")?.toList { readDoc(it as JSONObject) }.orEmpty()
                    )
                }.orEmpty(),
                endDate = v.optString("endDate").takeIf(String::isNotEmpty),
                active = v.getBoolean("active"),
                identityKey = v.optString("identityKey"),
                maskedAadhaar = v.optString("maskedAadhaar")
            )
        }
        val bills = root.getJSONArray("bills").toList { item ->
            val v = item as JSONObject
            val paid = v.getLong("paid")
            val month = v.getString("month")
            RentBill(
                id = v.getString("id"), tenantId = v.getString("tenantId"), month = month,
                units = v.getLong("units"), rent = v.getLong("rent"),
                payments = if (paid > 0) listOf(Payment(UUID.randomUUID().toString(), "$month-01", paid, "Imported from earlier version")) else emptyList()
            )
        }
        return RentalData(buildings, tenants, bills)
    }

    private fun readDoc(o: JSONObject) = DocumentFile(o.getString("uri"), o.getString("name"))

    private inline fun <T> JSONArray.toList(read: (Any) -> T): List<T> = (0 until length()).map { read(get(it)) }
}
