package com.tenantmanagement

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Per-owner storage in Cloud Firestore under users/{uid}. Firestore keeps an offline cache, so the app works without a
 * connection and syncs when it returns; security rules (firebase/firestore.rules) restrict each user to their own tree.
 */
class CloudRepository(private val uid: String, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val root get() = db.collection("users").document(uid)
    private val _data = MutableStateFlow<RentalData?>(null)
    val data: StateFlow<RentalData?> = _data
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var identityKey: ByteArray = ByteArray(0)
        private set

    private var buildings: List<Building>? = null
    private var tenants: List<Tenant>? = null
    private var bills: List<RentBill>? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    /** Loads (or creates) the per-user secret used for Aadhaar duplicate detection, then starts live sync. */
    suspend fun start() {
        val doc = root.get().await()
        val stored = doc.getString("idKey")
        identityKey = if (stored != null) Base64.decode(stored, Base64.NO_WRAP) else {
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            root.set(mapOf("idKey" to Base64.encodeToString(fresh, Base64.NO_WRAP)), SetOptions.merge()).await()
            fresh
        }
        listen("buildings", ::readBuilding) { buildings = it }
        listen("tenants", ::readTenant) { tenants = it }
        listen("bills", ::readBill) { bills = it }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        buildings = null
        tenants = null
        bills = null
        _data.value = null
    }

    private fun <T> listen(name: String, read: (DocumentSnapshot) -> T?, assign: (List<T>) -> Unit) {
        listeners += root.collection(name).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "listen $name failed", error)
                _error.value = "Sync problem: ${error.localizedMessage ?: "unknown error"}"
                return@addSnapshotListener
            }
            _error.value = null
            assign(snapshot?.documents.orEmpty().mapNotNull { runCatching { read(it) }.getOrNull() })
            publish()
        }
    }

    private fun publish() {
        val b = buildings ?: return
        val t = tenants ?: return
        val r = bills ?: return
        _data.value = RentalData(b.sortedBy { it.name.lowercase() }, t, r)
    }

    /** Writes only what changed between [old] and [new]. Updates apply locally at once and sync in the background. */
    fun save(old: RentalData, new: RentalData) {
        val batch = db.batch()
        diff(old.buildings, new.buildings, { it.id }, "buildings", ::buildingMap, batch)
        diff(old.tenants, new.tenants, { it.id }, "tenants", ::tenantMap, batch)
        diff(old.bills, new.bills, { it.id }, "bills", ::billMap, batch)
        batch.commit().addOnFailureListener {
            Log.w(TAG, "save failed", it)
            _error.value = "Could not save changes: ${it.localizedMessage ?: "unknown error"}"
        }
        _data.value = new
    }

    private fun <T> diff(
        old: List<T>, new: List<T>, id: (T) -> String, name: String,
        toMap: (T) -> Map<String, Any?>, batch: WriteBatch
    ) {
        val before = old.associateBy(id)
        val after = new.associateBy(id)
        after.forEach { (key, value) -> if (before[key] != value) batch.set(root.collection(name).document(key), toMap(value)) }
        before.keys.filter { it !in after }.forEach { batch.delete(root.collection(name).document(it)) }
    }

    /** Copies data from an older on-device install into the cloud. */
    fun importLegacy(legacy: RentalData) {
        val current = _data.value ?: RentalData()
        save(
            current,
            RentalData(
                (current.buildings + legacy.buildings).distinctBy { it.id },
                (current.tenants + legacy.tenants).distinctBy { it.id },
                (current.bills + legacy.bills).distinctBy { it.id }
            )
        )
    }

    companion object {
        private const val TAG = "CloudRepository"

        private fun docs(files: List<DocumentFile>) = files.map { mapOf("uri" to it.uri, "name" to it.name) }

        @Suppress("UNCHECKED_CAST")
        private fun readDocs(value: Any?): List<DocumentFile> =
            (value as? List<Map<String, Any?>>).orEmpty().map { DocumentFile(it["uri"] as String, it["name"] as String) }

        internal fun buildingMap(b: Building) = mapOf("name" to b.name, "address" to b.address, "flats" to b.flats, "ownerName" to b.ownerName)

        internal fun tenantMap(t: Tenant) = mapOf(
            "name" to t.name, "phone" to t.phone, "buildingId" to t.buildingId, "flat" to t.flat, "rent" to t.rent,
            "family" to t.family, "documents" to t.documents, "startDate" to t.startDate,
            "documentFiles" to docs(t.documentFiles), "photoUri" to t.photoUri,
            "familyMembers" to t.familyMembers.map {
                mapOf("name" to it.name, "relationship" to it.relationship, "photoUri" to it.photoUri, "documentFiles" to docs(it.documentFiles))
            },
            "endDate" to t.endDate, "active" to t.active, "identityKey" to t.identityKey, "maskedAadhaar" to t.maskedAadhaar
        )

        internal fun billMap(b: RentBill) = mapOf(
            "tenantId" to b.tenantId, "month" to b.month, "units" to b.units, "rent" to b.rent, "rate" to b.rate,
            "payments" to b.payments.map { mapOf("id" to it.id, "date" to it.date, "amount" to it.amount, "note" to it.note) }
        )

        private fun readBuilding(d: DocumentSnapshot) = Building(
            d.id, d.getString("name") ?: "", d.getString("address") ?: "",
            (d.get("flats") as? List<*>).orEmpty().map { it.toString() },
            d.getString("ownerName") ?: ""
        )

        @Suppress("UNCHECKED_CAST")
        private fun readTenant(d: DocumentSnapshot) = Tenant(
            id = d.id, name = d.getString("name") ?: "", phone = d.getString("phone") ?: "",
            buildingId = d.getString("buildingId") ?: "", flat = d.getString("flat") ?: "",
            rent = d.getLong("rent") ?: 0, family = d.getString("family") ?: "", documents = d.getString("documents") ?: "",
            startDate = d.getString("startDate") ?: "", documentFiles = readDocs(d.get("documentFiles")),
            photoUri = d.getString("photoUri"),
            familyMembers = (d.get("familyMembers") as? List<Map<String, Any?>>).orEmpty().map {
                FamilyMember(it["name"] as String, it["relationship"] as String, it["photoUri"] as String, readDocs(it["documentFiles"]))
            },
            endDate = d.getString("endDate"), active = d.getBoolean("active") ?: true,
            identityKey = d.getString("identityKey") ?: "", maskedAadhaar = d.getString("maskedAadhaar") ?: ""
        )

        @Suppress("UNCHECKED_CAST")
        private fun readBill(d: DocumentSnapshot) = RentBill(
            id = d.id, tenantId = d.getString("tenantId") ?: "", month = d.getString("month") ?: "",
            units = d.getLong("units") ?: 0, rent = d.getLong("rent") ?: 0, rate = d.getLong("rate") ?: DEFAULT_ELECTRICITY_RATE,
            payments = (d.get("payments") as? List<Map<String, Any?>>).orEmpty().map {
                Payment(it["id"] as String, it["date"] as String, (it["amount"] as Number).toLong(), (it["note"] as? String).orEmpty())
            }
        )
    }
}
