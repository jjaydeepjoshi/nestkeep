package com.tenantmanagement

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.Color as AndroidColor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth

private val Ink = Color(0xFF172B26)
private val Green = Color(0xFF176B55)
private val Muted = Color(0xFF73827C)
private val Canvas = Color(0xFFF5F7F4)
private val Mint = Color(0xFFE6F3EC)
private val Amber = Color(0xFFB66A16)
private val SoftAmber = Color(0xFFFFF1DB)
private val Red = Color(0xFFB8463D)

private data class FamilyDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val relationship: String = ""
)

private val LocalDrive = androidx.compose.runtime.staticCompositionLocalOf<DriveStorage?> { null }

class MainActivity : ComponentActivity() {
    private lateinit var auth: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthManager(this)
        auth.consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            auth.onConsentResult(it.data)
        }
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Green,
                    onPrimary = Color.White,
                    secondary = Color(0xFF397D67),
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    AppRoot(auth)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(auth: AuthManager) {
    var user by remember { mutableStateOf(auth.user) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val remove = auth.addListener { user = it }
        onDispose { remove() }
    }
    val current = user
    if (current == null) SignInScreen(auth) else SignedIn(auth, current.uid, current.email.orEmpty())
}

@Composable
private fun SignInScreen(auth: AuthManager) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).background(Mint, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Apartment, contentDescription = null, tint = Green, modifier = Modifier.size(38.dp))
        }
        Text("NestKeep", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 18.dp))
        Text(
            "Sign in with Google to keep your buildings, tenants and bills safely backed up and in sync on every device.",
            color = Muted, modifier = Modifier.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(
            onClick = {
                busy = true
                message = null
                failed = false
                scope.launch {
                    try {
                        auth.signIn()
                    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                        message = "Sign-in was cancelled."
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: "Sign-in failed. Check your connection and try again."
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (busy) "Signing in…" else "Sign in with Google", fontWeight = FontWeight.SemiBold) }
        message?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
        if (failed) {
            val fingerprints = remember { signingFingerprints(context) }
            if (fingerprints != null) {
                val details = "App signing SHA-1:\n${fingerprints.sha1}\n\nApp signing SHA-256:\n${fingerprints.sha256}"
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(details, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 14.dp))
                }
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(details))
                    Toast.makeText(context, "Fingerprints copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy fingerprints", color = Green) }
                Text(
                    "Add the SHA-1 in Firebase console > Project settings > NestKeep Android app > Add fingerprint, then try again.",
                    color = Muted, style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SignedIn(auth: AuthManager, uid: String, email: String) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repo = remember(uid) { CloudRepository(uid) }
    val drive = remember(uid) { DriveStorage(context.applicationContext) { refresh -> auth.driveToken(refresh) } }
    var startError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(repo, attempt) {
        startError = null
        try {
            repo.start()
        } catch (e: Exception) {
            startError = e.localizedMessage ?: "Could not load your data."
        }
    }
    androidx.compose.runtime.DisposableEffect(repo) { onDispose { repo.stop() } }
    val data by repo.data.collectAsState()
    val syncError by repo.error.collectAsState()
    val loaded = data
    if (loaded == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (startError == null) {
                Text("Loading your data…", color = Muted)
            } else {
                Text(startError.orEmpty(), color = Red)
                Button(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                TextButton(onClick = { scope.launch { auth.signOut() } }) { Text("Sign out") }
            }
        }
    } else {
        androidx.compose.runtime.CompositionLocalProvider(LocalDrive provides drive) {
            RentalApp(repo, loaded, email, syncError) { scope.launch { auth.signOut() } }
        }
    }
}

private enum class Page(val title: String, val icon: ImageVector) {
    OVERVIEW("Home", Icons.Filled.Home),
    BUILDINGS("Buildings", Icons.Filled.Apartment),
    TENANTS("Tenants", Icons.Filled.People),
    BILLS("Bills", Icons.AutoMirrored.Filled.ReceiptLong),
    REPORTS("Reports", Icons.Filled.BarChart),
    HISTORY("History", Icons.Filled.History)
}

@Composable
private fun RentalApp(repo: CloudRepository, data: RentalData, email: String, syncError: String?, onSignOut: () -> Unit) {
    val drive = LocalDrive.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val legacyStore = remember { LegacyStore(appContext) }
    var legacyPending by remember { mutableStateOf(legacyStore.hasData()) }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Page.OVERVIEW) }
    var showBuildingDialog by remember { mutableStateOf(false) }
    var showTenantDialog by remember { mutableStateOf(false) }
    var showBillDialog by remember { mutableStateOf(false) }
    var registrationDocuments by remember { mutableStateOf<Map<String, List<DocumentFile>>>(emptyMap()) }
    var registrationPhotos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var movingOut by remember { mutableStateOf<Tenant?>(null) }
    var payingBill by remember { mutableStateOf<RentBill?>(null) }
    var documentOwner by remember { mutableStateOf("tenant") }
    var cameraTarget by remember { mutableStateOf("tenant") }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val added = mutableListOf<DocumentFile>()
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                added += DocumentFile(uri.toString(), documentDisplayName(context, uri))
            } catch (_: SecurityException) {
                Toast.makeText(context, "Could not keep access to ${documentDisplayName(context, uri)}. Please choose it again.", Toast.LENGTH_LONG).show()
            }
        }
        registrationDocuments = registrationDocuments + (documentOwner to
            ((registrationDocuments[documentOwner].orEmpty() + added).distinctBy { it.uri }))
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingPhotoFile
        val uri = pendingPhotoUri
        if (success && uri != null && file?.exists() == true) {
            registrationPhotos = registrationPhotos + (cameraTarget to uri.toString())
        } else {
            file?.delete()
            Toast.makeText(context, "Photo capture was cancelled. Please capture a photo to continue.", Toast.LENGTH_SHORT).show()
        }
        pendingPhotoFile = null
        pendingPhotoUri = null
    }

    fun captureRegistrationPhoto(target: String) {
        try {
            val directory = File(context.filesDir, "tenant_photos")
            if (!directory.exists() && !directory.mkdirs()) {
                Toast.makeText(context, "Could not prepare private photo storage.", Toast.LENGTH_LONG).show()
                return
            }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTarget = target
            pendingPhotoFile = file
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            pendingPhotoFile?.delete()
            pendingPhotoFile = null
            pendingPhotoUri = null
            Toast.makeText(context, "Could not start the camera: ${error.localizedMessage ?: "camera unavailable"}", Toast.LENGTH_LONG).show()
        }
    }

    fun update(next: RentalData) = repo.save(data, next)

    val activeTenants = data.tenants.filter { it.active }
    val activeBills = data.bills.filter { it.month == currentMonth() }
    val monthlyRent = activeTenants.sumOf { it.rent }
    val collected = activeBills.sumOf { it.paid }
    val pending = activeBills.sumOf { it.due }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Mint, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Apartment, contentDescription = null, tint = Green)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("NESTKEEP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green, letterSpacing = 1.5.sp)
                    Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                }
                TextButton(onClick = { showAccount = true }) {
                    Text(email.take(1).uppercase().ifEmpty { "A" }, color = Green, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Page.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = page == destination,
                        onClick = { page = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title, maxLines = 1) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (syncError != null) {
                Text(syncError, color = Red, style = MaterialTheme.typography.bodySmall)
            }
            when (page) {
                Page.OVERVIEW -> OverviewPage(
                    data = data,
                    monthlyRent = monthlyRent,
                    collected = collected,
                    pending = pending,
                    onOpen = { page = it },
                    onAddBuilding = { showBuildingDialog = true },
                    onAddTenant = { showTenantDialog = true },
                    onCreateBill = { showBillDialog = true }
                )
                Page.BUILDINGS -> BuildingsPage(data, onAdd = { showBuildingDialog = true }, onAddTenant = { showTenantDialog = true })
                Page.TENANTS -> TenantsPage(data, onAdd = { showTenantDialog = true }, onMoveOut = { movingOut = it })
                Page.BILLS -> BillsPage(data, onCreate = { showBillDialog = true }, onPay = { payingBill = it })
                Page.REPORTS -> ReportsPage(data)
                Page.HISTORY -> HistoryPage(data)
            }
        }
    }

    if (showBuildingDialog) {
        AddBuildingDialog(
            onDismiss = { showBuildingDialog = false },
            onAdd = { name, address, count ->
                val flats = (1..count).map { "${100 + it}" }
                update(data.copy(buildings = data.buildings + Building(UUID.randomUUID().toString(), name, address, flats)))
                showBuildingDialog = false
            }
        )
    }
    if (showTenantDialog) {
        AddTenantDialog(
            buildings = data.buildings,
            tenants = data.tenants,
            existingIdentityKeys = data.tenants.filter { it.active }.map { it.identityKey }.filter(String::isNotBlank).toSet(),
            identityFingerprint = { aadhaarFingerprint(it, repo.identityKey) },
            documentFiles = registrationDocuments,
            photoUris = registrationPhotos,
            onDocumentFilesChange = { owner, files ->
                val retainedUris = files.map { it.uri }.toSet()
                registrationDocuments[owner].orEmpty().filterNot { it.uri in retainedUris }.forEach { removed ->
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(removed.uri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                registrationDocuments = registrationDocuments + (owner to files)
            },
            onPickDocuments = { owner ->
                documentOwner = owner
                documentPicker.launch(arrayOf("image/*", "application/pdf"))
            },
            onCapturePhoto = ::captureRegistrationPhoto,
            onDiscardPhoto = { owner ->
                registrationPhotos[owner]?.let { deletePrivateAttachment(context, it) }
                registrationPhotos = registrationPhotos - owner
            },
            onDismiss = {
                if (!saving) {
                    registrationDocuments.values.flatten().forEach { file ->
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(file.uri),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
                    registrationDocuments = emptyMap()
                    registrationPhotos.values.forEach { deletePrivateAttachment(context, it) }
                    registrationPhotos = emptyMap()
                    showTenantDialog = false
                }
            },
            onAdd = { name, phone, buildingId, flat, rent, family, documents, startDate, files, photoUri, familyMembers, identityKey, maskedAadhaar ->
                if (data.tenants.any { it.active && it.identityKey == identityKey }) {
                    Toast.makeText(context, "This Aadhaar is already registered.", Toast.LENGTH_LONG).show()
                } else if (data.tenants.any { it.active && it.buildingId == buildingId && it.flat == flat }) {
                    Toast.makeText(context, "That flat was just occupied. Please pick another.", Toast.LENGTH_LONG).show()
                } else if (drive == null) {
                    Toast.makeText(context, "Cloud storage is not ready. Please try again.", Toast.LENGTH_LONG).show()
                } else if (!saving) {
                    saving = true
                    scope.launch {
                        val uploaded = mutableListOf<String>()
                        suspend fun upload(local: String, fileName: String, mime: String): String =
                            drive.upload(Uri.parse(local), fileName, mime).also { uploaded += it }
                        suspend fun uploadDocs(list: List<DocumentFile>) = list.map {
                            val mime = context.contentResolver.getType(Uri.parse(it.uri)) ?: "application/octet-stream"
                            DocumentFile(upload(it.uri, it.name, mime), it.name)
                        }
                        try {
                            val tenant = Tenant(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                phone = phone.trim(),
                                buildingId = buildingId,
                                flat = flat,
                                rent = rent,
                                family = family.trim(),
                                documents = documents.trim(),
                                startDate = startDate,
                                documentFiles = uploadDocs(files),
                                photoUri = upload(photoUri, "tenant-photo.jpg", "image/jpeg"),
                                familyMembers = familyMembers.map { member ->
                                    member.copy(
                                        photoUri = upload(member.photoUri, "${member.name}-photo.jpg", "image/jpeg"),
                                        documentFiles = uploadDocs(member.documentFiles)
                                    )
                                },
                                identityKey = identityKey,
                                maskedAadhaar = maskedAadhaar
                            )
                            update(data.copy(tenants = data.tenants + tenant))
                            registrationDocuments.values.flatten().forEach {
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(Uri.parse(it.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            registrationPhotos.values.forEach { deletePrivateAttachment(context, it) }
                            registrationDocuments = emptyMap()
                            registrationPhotos = emptyMap()
                            showTenantDialog = false
                        } catch (error: Exception) {
                            uploaded.forEach { runCatching { drive.delete(it) } }
                            Toast.makeText(context, "Could not upload documents: ${error.localizedMessage ?: "check your connection"}. Nothing was saved.", Toast.LENGTH_LONG).show()
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
    }
    if (saving) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Saving tenant") },
            text = { Text("Uploading photos and documents to your Google Drive…") }
        )
    }
    if (showBillDialog) {
        CreateBillDialog(
            tenants = activeTenants,
            bills = data.bills,
            onDismiss = { showBillDialog = false },
            onCreate = { tenant, month, units, rate ->
                val bill = RentBill(UUID.randomUUID().toString(), tenant.id, month, units, tenant.rent, rate)
                update(data.copy(bills = data.bills + bill))
                showBillDialog = false
            }
        )
    }
    movingOut?.let { tenant ->
        val unpaid = data.bills.filter { it.tenantId == tenant.id }.sumOf { it.due }
        AlertDialog(
            onDismissRequest = { movingOut = null },
            title = { Text("Archive this tenant?") },
            text = {
                Text(
                    "${tenant.name} will be removed from the active tenants and marked as moved out of ${flatAddress(data, tenant)}. Their registration, documents, bills and flat history will remain available in History." +
                        if (unpaid > 0) "\n\nWarning: ${money(unpaid)} is still unpaid on their bills." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = data.tenants.map {
                        if (it.id == tenant.id) it.copy(active = false, endDate = today()) else it
                    }
                    update(data.copy(tenants = updated))
                    movingOut = null
                }) { Text("Archive tenant", color = Red) }
            },
            dismissButton = { TextButton(onClick = { movingOut = null }) { Text("Cancel") } }
        )
    }
    payingBill?.let { bill ->
        PaymentDialog(
            bill = bill,
            tenant = data.tenants.firstOrNull { it.id == bill.tenantId },
            onDismiss = { payingBill = null },
            onPay = { amount, date, note ->
                update(data.copy(bills = data.bills.map {
                    if (it.id == bill.id) it.copy(payments = it.payments + Payment(UUID.randomUUID().toString(), date, amount, note)) else it
                }))
                payingBill = null
            }
        )
    }
    if (legacyPending) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Move data from this phone?") },
            text = { Text("Records saved on this device by an earlier version can be uploaded to your Google account so they sync everywhere.") },
            confirmButton = {
                TextButton(enabled = !importing && drive != null, onClick = {
                    importing = true
                    scope.launch {
                        try {
                            val legacy = legacyStore.load()
                            suspend fun move(uri: String, name: String, mime: String): String =
                                if (isDriveUri(uri)) uri else runCatching { drive!!.upload(Uri.parse(uri), name, mime) }.getOrDefault(uri)
                            suspend fun moveDocs(list: List<DocumentFile>) = list.map {
                                val mime = runCatching { context.contentResolver.getType(Uri.parse(it.uri)) }.getOrNull() ?: "application/octet-stream"
                                DocumentFile(move(it.uri, it.name, mime), it.name)
                            }
                            val moved = legacy.copy(tenants = legacy.tenants.map { t ->
                                t.copy(
                                    photoUri = t.photoUri?.let { move(it, "tenant-photo.jpg", "image/jpeg") },
                                    documentFiles = moveDocs(t.documentFiles),
                                    familyMembers = t.familyMembers.map { m ->
                                        m.copy(photoUri = move(m.photoUri, "${m.name}-photo.jpg", "image/jpeg"), documentFiles = moveDocs(m.documentFiles))
                                    }
                                )
                            })
                            repo.importLegacy(moved)
                            legacyStore.clear()
                            legacyPending = false
                        } catch (error: Exception) {
                            Toast.makeText(context, "Could not move data: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        } finally {
                            importing = false
                        }
                    }
                }) { Text(if (importing) "Uploading…" else "Upload to cloud") }
            },
            dismissButton = {
                TextButton(enabled = !importing, onClick = { legacyPending = false }) { Text("Not now") }
            }
        )
    }
    if (showAccount) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("Account") },
            text = { Text("Signed in as $email.\n\nYour records sync to your private cloud space; photos and documents are kept in your own Google Drive app folder.") },
            confirmButton = { TextButton(onClick = { showAccount = false; onSignOut() }) { Text("Sign out", color = Red) } },
            dismissButton = { TextButton(onClick = { showAccount = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun OverviewPage(
    data: RentalData,
    monthlyRent: Long,
    collected: Long,
    pending: Long,
    onOpen: (Page) -> Unit,
    onAddBuilding: () -> Unit,
    onAddTenant: () -> Unit,
    onCreateBill: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PROPERTY OVERVIEW  ·  ${currentMonth()}", color = Color(0xFFC2DED1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Your rentals,\nin good order.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("${data.buildings.size} buildings")
                    SmallPill("${data.tenants.count { it.active }} active tenants")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("MONTHLY RENT", money(monthlyRent), "From occupied flats", Icons.Filled.Home, Modifier.weight(1f))
            MetricCard("COLLECTED", money(collected), "This month", Icons.Filled.CheckCircle, Modifier.weight(1f), accent = Green)
        }
        Card(colors = CardDefaults.cardColors(containerColor = SoftAmber), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Payments, contentDescription = null, tint = Amber)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Bills still due this month", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Across generated bills", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(money(pending), color = Amber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        SectionHeading("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction("Building", Icons.Filled.Apartment, onAddBuilding, Modifier.weight(1f))
            QuickAction("Tenant", Icons.Filled.People, onAddTenant, Modifier.weight(1f))
            QuickAction("Monthly bill", Icons.AutoMirrored.Filled.ReceiptLong, onCreateBill, Modifier.weight(1f))
        }
        SectionHeading("Your portfolio", action = "See all", onAction = { onOpen(Page.BUILDINGS) })
        if (data.buildings.isEmpty()) {
            EmptyCard(
                title = "Start with a building",
                message = "Add a property and its flats. Then register tenants and start tracking rent.",
                button = "Add first building",
                onClick = onAddBuilding
            )
        } else {
            data.buildings.take(3).forEach { building ->
                BuildingCard(building, data, onAddTenant)
            }
        }
    }
}

@Composable
private fun BuildingsPage(data: RentalData, onAdd: () -> Unit, onAddTenant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageIntro("Properties & flats", "${data.buildings.size} properties · ${data.buildings.sumOf { it.flats.size }} total flats")
        PrimaryAction("Add building", onAdd)
        if (data.buildings.isEmpty()) {
            EmptyCard("No buildings yet", "Create your first property and enter the number of flats.", "Add building", onAdd)
        } else {
            data.buildings.forEach { BuildingCard(it, data, onAddTenant) }
        }
    }
}

@Composable
private fun BuildingCard(building: Building, data: RentalData, onAddTenant: () -> Unit) {
    val tenants = data.tenants.filter { it.buildingId == building.id && it.active }
    val occupiedFlats = tenants.map { it.flat }.toSet()
    val buildingTenantIds = tenants.map { it.id }.toSet()
    val dueThisMonth = data.bills
        .filter { it.tenantId in buildingTenantIds && it.month == currentMonth() }
        .sumOf { it.due }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(21.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).background(Mint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Apartment, contentDescription = null, tint = Green)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(building.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Ink)
                    if (building.address.isNotBlank()) Text(building.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("${occupiedFlats.size} occupied  ·  ${building.flats.size - occupiedFlats.size} vacant", color = Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
                }
            }
            HorizontalDivider(color = Color(0xFFEAF0EC))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MONTHLY RENT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                            Text(money(tenants.sumOf { it.rent }), color = Ink, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BILLED DUE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                            Text(money(dueThisMonth), color = if (dueThisMonth > 0) Amber else Green, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEAF0EC))
                    Text("FLATS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                building.flats.take(5).forEach { flat ->
                    val occupied = flat in occupiedFlats
                    Surface(shape = RoundedCornerShape(10.dp), color = if (occupied) Mint else Canvas) {
                        Text(flat.trim(), modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = if (occupied) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (building.flats.size > 5) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Canvas) {
                        Text("+${building.flats.size - 5}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            if (building.flats.any { it !in occupiedFlats }) {
                OutlinedButton(onClick = onAddTenant, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Assign a tenant", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TenantsPage(data: RentalData, onAdd: () -> Unit, onMoveOut: (Tenant) -> Unit) {
    val tenants = data.tenants.filter { it.active }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageIntro("People & occupancy", "${tenants.size} active tenants")
        PrimaryAction("Register tenant", onAdd)
        if (tenants.isEmpty()) {
            EmptyCard("No active tenants", "Register a tenant and assign an available flat to begin.", "Register tenant", onAdd)
        } else {
            tenants.forEach { tenant ->
                val building = data.buildings.firstOrNull { it.id == tenant.buildingId }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(tenant.name)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(tenant.name, fontWeight = FontWeight.Bold, color = Ink)
                                Text("${building?.name ?: "Building"} · Flat ${tenant.flat.trim()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(money(tenant.rent), color = Green, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Color(0xFFEAF0EC))
                        Text("☎  ${tenant.phone.ifBlank { "No phone added" }}   ·   Since ${tenant.startDate}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (tenant.family.isNotBlank()) Text("Family: ${tenant.family}", color = Ink, style = MaterialTheme.typography.bodySmall)
                        if (tenant.documents.isNotBlank()) Text("Documents: ${tenant.documents}", color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TenantDocumentLinks(personFiles(tenant.photoUri, tenant.documentFiles, "Tenant live photo.jpg"))
                        tenant.familyMembers.forEach { member ->
                            Text("${member.name} · ${member.relationship}", color = Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            TenantDocumentLinks(personFiles(member.photoUri, member.documentFiles, "${member.name} live photo.jpg"))
                        }
                        TextButton(onClick = { onMoveOut(tenant) }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Red, modifier = Modifier.size(16.dp))
                            Text("Move out", color = Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillsPage(data: RentalData, onCreate: () -> Unit, onPay: (RentBill) -> Unit) {
    val sortedBills = data.bills.sortedWith(compareByDescending<RentBill> { it.month }.thenByDescending { it.id })
    val monthBills = data.bills.filter { it.month == currentMonth() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageIntro("Rent & electricity", "${money(monthBills.sumOf { it.total })} billed this month")
        Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(19.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color(0xFFFFD887), modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Electricity rate", color = Color(0xFFC2DED1), style = MaterialTheme.typography.labelMedium)
                    Text("Default ${money(DEFAULT_ELECTRICITY_RATE)} per unit", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Text("Rent + units × rate", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        PrimaryAction("Generate monthly bill", onCreate)
        if (sortedBills.isEmpty()) {
            EmptyCard("No bills generated", "Choose a tenant, enter the meter units and generate their monthly bill.", "Create first bill", onCreate)
        } else {
            sortedBills.forEach { bill ->
                val tenant = data.tenants.firstOrNull { it.id == bill.tenantId }
                BillCard(bill, tenant, onPay)
            }
        }
    }
}

@Composable
private fun BillCard(bill: RentBill, tenant: Tenant?, onPay: (RentBill) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tenant?.name ?: "Former tenant", color = Ink, fontWeight = FontWeight.Bold)
                    Text("${bill.month}  ·  Flat ${tenant?.flat?.trim() ?: "—"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    color = if (bill.due == 0L) Mint else SoftAmber,
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(if (bill.due == 0L) "PAID" else if (bill.paid > 0) "PART PAID" else "DUE", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = if (bill.due == 0L) Green else Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = Color(0xFFEAF0EC))
            BillLine("Monthly rent", money(bill.rent))
            BillLine("Electricity · ${bill.units} units × ₹${bill.rate}", money(bill.electricity))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total bill", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(money(bill.total), color = Ink, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid ${money(bill.paid)}", color = Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("Due ${money(bill.due)}", color = if (bill.due == 0L) Green else Amber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            if (bill.due > 0) {
                Button(onClick = { onPay(bill) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Record payment", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportsPage(data: RentalData) {
    var yearly by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableStateOf(java.time.Year.now().value) }
    var periodInput by remember { mutableStateOf(currentMonth()) }
    var periodError by remember { mutableStateOf<String?>(null) }
    var selectedTenantKey by remember { mutableStateOf<String?>(null) }
    var selectedBuildingReportIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingPdfLines by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val pdfLauncher = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the selected file")
                output.use { writeReportPdf(pendingPdfLines, it) }
                Toast.makeText(context, "Report PDF saved.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Could not save report PDF: ${error.localizedMessage ?: "storage error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val periodLabel = if (yearly) selectedYear.toString() else selectedMonth.toString()
    val selectedBuildings = data.buildings.filter { it.id in selectedBuildingReportIds }
    val selectedTenantIds = data.tenants.filter { it.buildingId in selectedBuildingReportIds }.map { it.id }.toSet()
    val selectedData = data.copy(
        buildings = selectedBuildings,
        tenants = data.tenants.filter { it.buildingId in selectedBuildingReportIds },
        bills = data.bills.filter { it.tenantId in selectedTenantIds }
    )
    val allTotals = reportTotals(selectedData, null, null, yearly, selectedYear, selectedMonth)
    val tenantReports = selectedData.tenants
        .filter { tenancyTouchesPeriod(it, yearly, selectedYear, selectedMonth) }
        .groupBy { tenant -> tenant.identityKey.ifBlank { "legacy:${tenant.id}" } }
        .values
        .map { records ->
            TenantReport(
                tenants = records.sortedBy { it.startDate },
                totals = reportTotalsForTenants(selectedData, records, yearly, selectedYear, selectedMonth)
            )
        }
        .sortedBy { it.primary.name.lowercase() }
    val visibleTenantReports = if (selectedTenantKey == null) tenantReports else
        tenantReports.filter { tenantReportKey(it) == selectedTenantKey }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LaunchedEffect(yearly, selectedMonth, selectedYear) {
            periodInput = if (yearly) selectedYear.toString() else selectedMonth.toString()
            periodError = null
        }
        PageIntro("Rental reports", "Income and occupancy by building and flat")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !yearly, onClick = { yearly = false }, label = { Text("Monthly") })
            FilterChip(selected = yearly, onClick = { yearly = true }, label = { Text("Yearly") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = periodInput,
                onValueChange = {
                    periodInput = if (yearly) it.filter(Char::isDigit).take(4) else it.filter { char -> char.isDigit() || char == '-' }.take(7)
                    periodError = null
                },
                label = { Text(if (yearly) "Select year" else "Select month (YYYY-MM)") },
                supportingText = {
                    Text(
                        periodError ?: if (yearly) "Enter a year, for example 2025." else "Enter a month, for example 2025-03.",
                        color = if (periodError != null) Red else Muted
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (yearly) {
                        val year = periodInput.toIntOrNull()
                        if (year != null && year in 1900..9999) {
                            selectedYear = year
                            periodError = null
                        } else {
                            periodError = "Enter a valid year from 1900 to 9999."
                        }
                    } else {
                        val month = runCatching { YearMonth.parse(periodInput) }.getOrNull()
                        if (month != null) {
                            selectedMonth = month
                            selectedYear = month.year
                            periodError = null
                        } else {
                            periodError = "Enter a valid month in YYYY-MM format."
                        }
                    }
                },
                enabled = if (yearly) periodInput.toIntOrNull() in 1900..9999 else
                    runCatching { YearMonth.parse(periodInput) }.isSuccess,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Apply") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    if (yearly) selectedYear-- else selectedMonth = selectedMonth.minusMonths(1)
                }) { Text("‹ Previous") }
                Text(periodLabel, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    if (yearly) selectedYear++ else selectedMonth = selectedMonth.plusMonths(1)
                }) { Text("Next ›") }
            }
        }
        Text(
            if (yearly) "Year totals include the selected year's monthly bills and occupied tenant-months."
            else "Figures are for this bill month. Occupancy includes tenants who lived in the flat during any part of the month.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        SectionHeading("Select buildings")
        if (data.buildings.isEmpty()) {
            EmptyCard("No buildings available", "Add a building to create rental reports.", null, null)
        } else {
            BuildingReportSelector(
                buildings = data.buildings,
                selectedBuildingIds = selectedBuildingReportIds,
                onToggle = { buildingId ->
                    selectedBuildingReportIds = if (buildingId in selectedBuildingReportIds) {
                        selectedBuildingReportIds - buildingId
                    } else {
                        selectedBuildingReportIds + buildingId
                    }
                    selectedTenantKey = null
                },
                onSelectAll = {
                    selectedBuildingReportIds = data.buildings.map { it.id }.toSet()
                    selectedTenantKey = null
                },
                onClear = {
                    selectedBuildingReportIds = emptySet()
                    selectedTenantKey = null
                }
            )
        }
        if (selectedBuildings.isEmpty()) {
            EmptyCard(
                "Choose one or more buildings",
                "Tap the building selector above and check the buildings you want to include. Report totals and details will appear here.",
                null,
                null
            )
        } else {
        PrimaryAction("Download selected buildings PDF", onClick = {
            pendingPdfLines = buildReportPdfLines(
                selectedData, yearly, selectedYear, selectedMonth, visibleTenantReports, selectedTenantKey != null
            )
            pdfLauncher.launch("Rental_Report_$periodLabel.pdf")
        })
        Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(21.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SELECTED BUILDINGS  ·  $periodLabel", color = Color(0xFFC2DED1), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .8.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportMetric("EXPECTED RENT", money(allTotals.expectedRent), Modifier.weight(1f), true)
                    ReportMetric("RENT BILLED", money(allTotals.rentBilled), Modifier.weight(1f), true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportMetric("PAID", money(allTotals.paid), Modifier.weight(1f), true)
                    ReportMetric("OUTSTANDING", money(allTotals.due), Modifier.weight(1f), true)
                }
                HorizontalDivider(color = Color(0x665CA18B))
                Text(
                    "Electricity ${money(allTotals.electricityBilled)} · Total billed ${money(allTotals.totalBilled)} · ${if (yearly) "Occupied tenant-months" else "Tenants in period"} ${allTotals.tenantMonths}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        selectedBuildings.forEach { building ->
                val flatReports = building.flats.map { flat ->
                    val tenants = selectedData.tenants.filter {
                        it.buildingId == building.id && it.flat == flat &&
                            tenancyTouchesPeriod(it, yearly, selectedYear, selectedMonth)
                    }
                    val totals = reportTotals(selectedData, building.id, flat, yearly, selectedYear, selectedMonth)
                    FlatReport(flat, tenants, totals)
                }
                val buildingTotals = flatReports.fold(RentalTotals()) { sum, report -> sum + report.totals }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Apartment, contentDescription = null, tint = Green)
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(building.name, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (building.address.isNotBlank()) Text(building.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        ReportTotalLines(buildingTotals, yearly)
                        HorizontalDivider(color = Color(0xFFEAF0EC))
                        Text("FLAT-WISE DETAIL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                        flatReports.forEachIndexed { index, report ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFEAF0EC))
                            FlatReportRow(report, yearly)
                        }
                    }
                }
            }
        HorizontalDivider(color = Color(0xFFDDE7E0), thickness = 1.dp)
        SectionHeading("Tenant-wise report")
        Text(
            "Each person is identified by their unique Aadhaar verifier. Aadhaar numbers are never displayed in full.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        if (tenantReports.isEmpty()) {
            EmptyCard("No tenants in this period", "Tenant-wise billed and payment totals will appear here.", null, null)
        } else {
            SearchableTenantFilter(
                tenants = tenantReports,
                selectedKey = selectedTenantKey,
                onSelect = { selectedTenantKey = it }
            )
            PrimaryAction(
                if (selectedTenantKey == null) "Download tenant-wise PDF" else "Download selected tenant PDF",
                onClick = {
                    pendingPdfLines = buildTenantReportPdfLines(
                        data,
                        visibleTenantReports,
                        yearly,
                        selectedYear,
                        selectedMonth
                    )
                    val tenantFileName = visibleTenantReports.singleOrNull()?.primary?.name
                        ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
                        ?.take(40)
                        ?.let { "_$it" }
                        .orEmpty()
                    pdfLauncher.launch("Tenant_Report$tenantFileName" +
                        "_${if (yearly) selectedYear else selectedMonth}.pdf")
                }
            )
            Text(
                "${visibleTenantReports.size} unique tenant${if (visibleTenantReports.size == 1) "" else "s"} in $periodLabel",
                color = Muted,
                style = MaterialTheme.typography.labelMedium
            )
            visibleTenantReports.forEach { report ->
                TenantReportCard(report, data.buildings, yearly)
            }
        }
        HorizontalDivider(color = Color(0xFFDDE7E0), thickness = 1.dp)
        SectionHeading(if (yearly) "Building-wise tenant report · yearly" else "Building-wise tenant report · monthly")
        Text(
            "Building breakdown for the selected reporting period and checked buildings.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        selectedBuildings.forEach { selectedBuilding ->
            val buildingTenantReports = selectedData.tenants
                .filter {
                    it.buildingId == selectedBuilding.id &&
                        tenancyTouchesPeriod(it, yearly, selectedYear, selectedMonth)
                }
                .groupBy { tenant -> tenant.identityKey.ifBlank { "legacy:${tenant.id}" } }
                .values
                .map { records ->
                    TenantReport(
                        tenants = records.sortedBy { it.startDate },
                        totals = reportTotalsForTenants(selectedData, records, yearly, selectedYear, selectedMonth)
                    )
                }
                .sortedBy { it.primary.name.lowercase() }
            val buildingPeriodTotals = buildingTenantReports.fold(RentalTotals()) { sum, report -> sum + report.totals }
            Text(
                "${selectedBuilding.name} · $periodLabel · ${buildingTenantReports.size} unique tenant${if (buildingTenantReports.size == 1) "" else "s"}",
                color = Green,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportLine("Expected rent", money(buildingPeriodTotals.expectedRent))
                    ReportLine("Rent billed", money(buildingPeriodTotals.rentBilled))
                    ReportLine("Electricity billed", money(buildingPeriodTotals.electricityBilled))
                    ReportLine("Paid / outstanding", "${money(buildingPeriodTotals.paid)} / ${money(buildingPeriodTotals.due)}", emphasize = true)
                }
            }
            PrimaryAction("Download ${selectedBuilding.name} tenant PDF", onClick = {
                pendingPdfLines = buildBuildingTenantReportPdfLines(
                    selectedData,
                    selectedBuilding,
                    buildingTenantReports,
                    selectedMonth,
                    yearly,
                    selectedYear
                )
                val safeBuildingName = selectedBuilding.name
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                    .take(40)
                pdfLauncher.launch("Tenant_Report_${safeBuildingName}_$periodLabel.pdf")
            })
            if (buildingTenantReports.isEmpty()) {
                EmptyCard("No tenants for this building and period", "Tenant records active during the selected period will appear here.", null, null)
            } else {
                buildingTenantReports.forEach { report ->
                    TenantReportCard(report, selectedData.buildings, yearly)
                }
            }
        }
        Text(
            "Payment amounts are attributed to the bill month, not the actual payment date. The app currently stores each bill's total paid amount, not individual payment dates.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        }
    }
}

@Composable
private fun TenantReportCard(report: TenantReport, buildings: List<Building>, yearly: Boolean) {
    val tenant = report.primary
    val locations = report.tenants.map { record ->
        val buildingName = buildings.firstOrNull { it.id == record.buildingId }?.name ?: "Building"
        "$buildingName · Flat ${record.flat.trim()}"
    }.distinct().joinToString()
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(tenant.name)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(tenant.name, color = Ink, fontWeight = FontWeight.Bold)
                    Text(
                        tenant.maskedAadhaar.ifBlank { "Legacy tenant · Aadhaar not recorded" },
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("${report.tenants.size} record${if (report.tenants.size == 1) "" else "s"}", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(locations, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(
                report.tenants.joinToString { "${it.startDate}–${it.endDate ?: "present"}" },
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(color = Color(0xFFEAF0EC))
            ReportTotalLines(report.totals, yearly)
        }
    }
}

@Composable
private fun SearchableTenantFilter(
    tenants: List<TenantReport>,
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = tenants.firstOrNull { tenantReportKey(it) == selectedKey }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(
                selected?.let { "${it.primary.name} · ${it.primary.maskedAadhaar.ifBlank { "No Aadhaar" }}" }
                    ?: "All tenants",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search name or Aadhaar") },
                singleLine = true,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
            DropdownMenuItem(
                text = { Text("All tenants") },
                onClick = {
                    onSelect(null)
                    query = ""
                    expanded = false
                }
            )
            tenants.filter { report ->
                val search = query.trim()
                search.isBlank() ||
                    report.primary.name.contains(search, ignoreCase = true) ||
                    report.primary.maskedAadhaar.contains(search, ignoreCase = true)
            }.forEach { report ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(report.primary.name)
                            Text(report.primary.maskedAadhaar.ifBlank { "Aadhaar unavailable" }, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelect(tenantReportKey(report))
                        query = ""
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun tenantReportKey(report: TenantReport): String =
    report.primary.identityKey.ifBlank { "legacy:${report.primary.id}" }

@Composable
private fun BuildingReportSelector(
    buildings: List<Building>,
    selectedBuildingIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedBuildings = buildings.filter { it.id in selectedBuildingIds }
    val summary = when (selectedBuildings.size) {
        0 -> "Choose buildings"
        buildings.size -> "All ${buildings.size} buildings selected"
        1 -> selectedBuildings.single().name
        else -> "${selectedBuildings.size} buildings selected"
    }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(summary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select buildings", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Select all") }, onClick = onSelectAll)
            DropdownMenuItem(text = { Text("Clear selection") }, onClick = onClear)
            HorizontalDivider()
            buildings.forEach { building ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = building.id in selectedBuildingIds, onCheckedChange = null)
                            Text(building.name, modifier = Modifier.padding(start = 6.dp))
                        }
                    },
                    onClick = { onToggle(building.id) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Done", color = Green, fontWeight = FontWeight.Bold) },
                onClick = { expanded = false }
            )
        }
    }
}

private fun buildBuildingTenantReportPdfLines(
    data: RentalData,
    building: Building,
    reports: List<TenantReport>,
    month: YearMonth,
    yearly: Boolean,
    year: Int
): List<String> {
    val totals = reports.fold(RentalTotals()) { sum, report -> sum + report.totals }
    val lines = mutableListOf(
        "NESTKEEP BUILDING TENANT REPORT",
        "Building: ${building.name}",
        "Address: ${building.address.ifBlank { "Not specified" }}",
        "Period: ${if (yearly) year else month}",
        "Unique tenants: ${reports.size}",
        "",
        "Building totals",
        "Expected rent: ${money(totals.expectedRent)}",
        "Rent billed: ${money(totals.rentBilled)}",
        "Electricity billed: ${money(totals.electricityBilled)}",
        "Total billed: ${money(totals.totalBilled)}",
        "Paid: ${money(totals.paid)}",
        "Outstanding: ${money(totals.due)}",
        ""
    )
    if (reports.isEmpty()) {
        lines += "No tenants occupied flats in this building during this month."
    }
    reports.forEach { report ->
        val tenant = report.primary
        lines += "TENANT: ${tenant.name}"
        lines += "Aadhaar: ${tenant.maskedAadhaar.ifBlank { "Not recorded" }}"
        lines += "Monthly flat records: ${report.tenants.size}"
        report.tenants.forEach { record ->
            lines += "  Flat ${record.flat.trim()} | ${record.startDate} to ${record.endDate ?: "Present"}"
        }
        lines += "Expected rent: ${money(report.totals.expectedRent)}"
        lines += "Rent billed: ${money(report.totals.rentBilled)}"
        lines += "Electricity billed: ${money(report.totals.electricityBilled)}"
        lines += "Total billed: ${money(report.totals.totalBilled)}"
        lines += "Paid: ${money(report.totals.paid)}"
        lines += "Outstanding: ${money(report.totals.due)}"
        lines += ""
    }
    lines += "Payment totals are attributed to the bill month; individual payment dates are not recorded."
    return lines
}

private fun buildReportPdfLines(
    data: RentalData,
    yearly: Boolean,
    year: Int,
    month: YearMonth,
    tenantReports: List<TenantReport>,
    tenantFiltered: Boolean
): List<String> {
    val period = if (yearly) year.toString() else month.toString()
    val lines = mutableListOf("NESTKEEP RENTAL REPORT", "Period: $period", "")
    val allTotals = reportTotals(data, null, null, yearly, year, month)
    lines += "ALL BUILDINGS"
    lines += "Expected rent: ${money(allTotals.expectedRent)}"
    lines += "Rent billed: ${money(allTotals.rentBilled)}"
    lines += "Electricity billed: ${money(allTotals.electricityBilled)}"
    lines += "Total billed: ${money(allTotals.totalBilled)}"
    lines += "Paid: ${money(allTotals.paid)}"
    lines += "Outstanding: ${money(allTotals.due)}"
    lines += "${if (yearly) "Occupied tenant-months" else "Tenants in period"}: ${allTotals.tenantMonths}"
    lines += ""

    data.buildings.forEach { building ->
        lines += "BUILDING: ${building.name}"
        val flatReports = building.flats.map { flat ->
            val tenants = data.tenants.filter {
                it.buildingId == building.id && it.flat == flat && tenancyTouchesPeriod(it, yearly, year, month)
            }
            FlatReport(flat, tenants, reportTotals(data, building.id, flat, yearly, year, month))
        }
        val totals = flatReports.fold(RentalTotals()) { sum, flatReport -> sum + flatReport.totals }
        lines += "Expected ${money(totals.expectedRent)} | Rent billed ${money(totals.rentBilled)} | Electricity ${money(totals.electricityBilled)}"
        lines += "Paid ${money(totals.paid)} | Outstanding ${money(totals.due)}"
        flatReports.forEach { flatReport ->
            lines += "Flat ${flatReport.flat.trim()} — ${if (flatReport.tenants.isEmpty()) "Vacant" else flatReport.tenants.joinToString { it.name }}"
            lines += "  Expected ${money(flatReport.totals.expectedRent)} | Billed ${money(flatReport.totals.totalBilled)} | Paid ${money(flatReport.totals.paid)} | Due ${money(flatReport.totals.due)}"
            flatReport.tenants.forEach { tenant ->
                lines += "  ${tenant.name} | ${tenant.maskedAadhaar.ifBlank { "Aadhaar not recorded" }} | ${tenant.startDate} to ${tenant.endDate ?: "Present"}"
            }
        }
        lines += ""
    }

    lines += if (tenantFiltered) "SELECTED TENANT REPORT" else "TENANT-WISE REPORT"
    if (tenantReports.isEmpty()) {
        lines += "No tenants found for this period."
    }
    tenantReports.forEach { report ->
        val primary = report.primary
        lines += "${primary.name} | ${primary.maskedAadhaar.ifBlank { "Aadhaar not recorded" }}"
        lines += "Records: ${report.tenants.size} | Periods: ${report.tenants.joinToString { "${it.startDate}–${it.endDate ?: "present"}" }}"
        report.tenants.forEach { tenant ->
            val building = data.buildings.firstOrNull { it.id == tenant.buildingId }?.name ?: "Building"
            lines += "  $building, Flat ${tenant.flat.trim()}"
        }
        lines += "Expected rent ${money(report.totals.expectedRent)} | Rent billed ${money(report.totals.rentBilled)}"
        lines += "Electricity ${money(report.totals.electricityBilled)} | Paid ${money(report.totals.paid)} | Due ${money(report.totals.due)}"
        lines += ""
    }
    lines += "Payment amounts are attributed to bill month; individual payment dates are not recorded."
    return lines
}

private fun buildTenantReportPdfLines(
    data: RentalData,
    reports: List<TenantReport>,
    yearly: Boolean,
    year: Int,
    month: YearMonth
): List<String> {
    val period = if (yearly) year.toString() else month.toString()
    val lines = mutableListOf("NESTKEEP TENANT REPORT", "Period: $period", "")
    if (reports.isEmpty()) {
        lines += "No tenant records found for this period."
        return lines
    }
    reports.forEach { report ->
        val primary = report.primary
        lines += "TENANT: ${primary.name}"
        lines += "Aadhaar: ${primary.maskedAadhaar.ifBlank { "Not recorded" }}"
        lines += "Unique tenant records: ${report.tenants.size}"
        lines += "Expected rent: ${money(report.totals.expectedRent)}"
        lines += "Rent billed: ${money(report.totals.rentBilled)}"
        lines += "Electricity billed: ${money(report.totals.electricityBilled)}"
        lines += "Total billed: ${money(report.totals.totalBilled)}"
        lines += "Paid: ${money(report.totals.paid)}"
        lines += "Outstanding: ${money(report.totals.due)}"
        lines += "${if (yearly) "Occupied tenant-months" else "Tenants in period"}: ${report.totals.tenantMonths}"
        lines += "Occupancy history:"
        report.tenants.forEach { tenant ->
            val building = data.buildings.firstOrNull { it.id == tenant.buildingId }?.name ?: "Building"
            lines += "  $building, Flat ${tenant.flat.trim()} | ${tenant.startDate} to ${tenant.endDate ?: "Present"}"
        }
        val recordIds = report.tenants.map { it.id }.toSet()
        val billMonths = if (yearly) data.bills.filter {
            it.tenantId in recordIds && it.month.startsWith("$year-")
        }.map { it.month }.distinct().sorted() else listOf(month.toString())
        billMonths.forEach { billMonth ->
            val monthBills = data.bills.filter { it.tenantId in recordIds && it.month == billMonth }
            lines += "  $billMonth: billed ${money(monthBills.sumOf { it.total })}, paid ${money(monthBills.sumOf { it.paid })}, due ${money(monthBills.sumOf { it.due })}"
        }
        lines += ""
    }
    lines += "Payment amounts are attributed to bill month; individual payment dates are not recorded."
    return lines
}

private fun writeReportPdf(lines: List<String>, output: java.io.OutputStream) {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(23, 43, 38)
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    var page: PdfDocument.Page? = null
    var canvas: AndroidCanvas? = null
    var pageNumber = 0
    var y = 0f

    fun startPage() {
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        canvas = page!!.canvas
        canvas!!.drawColor(AndroidColor.WHITE)
        y = 48f
    }

    fun finishPage() {
        page?.let(document::finishPage)
        page = null
        canvas = null
    }

    fun drawLine(text: String) {
        val target = canvas ?: error("PDF page not initialized")
        val maxWidth = 495f
        if (text.isBlank()) {
            y += 10f
            return
        }
        var remaining = text
        while (remaining.isNotEmpty()) {
            var end = remaining.length
            while (end > 1 && paint.measureText(remaining.substring(0, end)) > maxWidth) end--
            if (end < remaining.length) {
                val space = remaining.lastIndexOf(' ', end)
                if (space > 0) end = space
            }
            if (y > 790f) {
                finishPage()
                startPage()
            }
            canvas!!.drawText(remaining.substring(0, end), 50f, y, paint)
            y += 17f
            remaining = remaining.substring(end).trimStart()
        }
    }

    try {
        startPage()
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 17f
            } else if (line.startsWith("BUILDING:") || line == "ALL BUILDINGS" ||
                line.contains("TENANT REPORT") || line == "TENANT-WISE REPORT"
            ) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 12f
            } else {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 11f
            }
            drawLine(line)
        }
        finishPage()
        document.writeTo(output)
    } finally {
        document.close()
    }
}

@Composable
private fun ReportMetric(label: String, amount: String, modifier: Modifier, onDark: Boolean = false) {
    Column(modifier) {
        Text(label, color = if (onDark) Color(0xFFC2DED1) else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
        Text(amount, color = if (onDark) Color.White else Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun ReportTotalLines(totals: RentalTotals, yearly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ReportLine("Expected rent", money(totals.expectedRent))
        ReportLine("Rent billed", money(totals.rentBilled))
        ReportLine("Electricity billed", money(totals.electricityBilled))
        ReportLine("Paid / outstanding", "${money(totals.paid)} / ${money(totals.due)}", emphasize = true)
        Text(
            "${if (yearly) "Occupied tenant-months" else "Tenants in period"}: ${totals.tenantMonths}",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FlatReportRow(report: FlatReport, yearly: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Flat ${report.flat.trim()}", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (report.tenants.isEmpty()) "VACANT" else "${report.tenants.size} tenant record${if (report.tenants.size == 1) "" else "s"}",
                color = if (report.tenants.isEmpty()) Muted else Green,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (report.tenants.isNotEmpty()) {
            Text(
                report.tenants.joinToString { tenant ->
                    "${tenant.name} (${tenant.startDate}–${tenant.endDate ?: "present"})"
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            "Expected ${money(report.totals.expectedRent)} · Billed ${money(report.totals.rentBilled)} · Paid ${money(report.totals.paid)} · Due ${money(report.totals.due)}",
            color = Ink,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Electricity ${money(report.totals.electricityBilled)} · ${if (yearly) "Tenant-months" else "Tenants"} ${report.totals.tenantMonths}",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReportLine(label: String, value: String, emphasize: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = if (emphasize) Green else Ink, style = MaterialTheme.typography.bodySmall, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun HistoryPage(data: RentalData) {
    val history = data.tenants.sortedWith(compareByDescending<Tenant> { it.startDate }.thenBy { it.name })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageIntro("Flat & tenant history", "${history.size} tenancy records")
        Text("Every move-in is retained here, including current occupants. Moving a tenant out adds their end date without deleting their rent bills.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (history.isEmpty()) {
            EmptyCard("Your history starts here", "Tenant move-in and move-out details will appear here.", null, null)
        } else {
            history.forEach { tenant ->
                val building = data.buildings.firstOrNull { it.id == tenant.buildingId }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(38.dp).background(if (tenant.active) Mint else Canvas, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (tenant.active) Icons.Filled.Home else Icons.Filled.History, contentDescription = null, tint = if (tenant.active) Green else Muted, modifier = Modifier.size(19.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tenant.name, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (tenant.active) "CURRENT" else "PAST", color = if (tenant.active) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${building?.name ?: "Building"} · Flat ${tenant.flat.trim()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text("${tenant.startDate}  →  ${tenant.endDate ?: "Present"}", color = Ink, style = MaterialTheme.typography.bodySmall)
                            Text("Rent ${money(tenant.rent)} / month · ${tenant.phone}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (tenant.documents.isNotBlank()) Text("Document notes: ${tenant.documents}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            TenantDocumentLinks(personFiles(tenant.photoUri, tenant.documentFiles, "Tenant live photo.jpg"))
                            tenant.familyMembers.forEach { member ->
                                Text("${member.name} · ${member.relationship}", color = Ink, style = MaterialTheme.typography.bodySmall)
                                TenantDocumentLinks(personFiles(member.photoUri, member.documentFiles, "${member.name} live photo.jpg"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBuildingDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var flatCount by remember { mutableStateOf("4") }
    val count = flatCount.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a building") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Building name") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address (optional)") }, singleLine = true)
                OutlinedTextField(flatCount, { flatCount = it.filter(Char::isDigit).take(3) }, label = { Text("Number of flats") }, singleLine = true)
                Text("Flats will be numbered 101 onwards. You can see occupancy by flat.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, address, count ?: 0) }, enabled = name.isNotBlank() && count != null && count in 1..500) { Text("Add building") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddTenantDialog(
    buildings: List<Building>,
    tenants: List<Tenant>,
    existingIdentityKeys: Set<String>,
    identityFingerprint: (String) -> String,
    documentFiles: Map<String, List<DocumentFile>>,
    photoUris: Map<String, String>,
    onDocumentFilesChange: (String, List<DocumentFile>) -> Unit,
    onPickDocuments: (String) -> Unit,
    onCapturePhoto: (String) -> Unit,
    onDiscardPhoto: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, Long, String, String, String, List<DocumentFile>, String, List<FamilyMember>, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var rentText by remember { mutableStateOf("") }
    var documents by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(today()) }
    var familyCountText by remember { mutableStateOf("0") }
    var familyMembers by remember { mutableStateOf(emptyList<FamilyDraft>()) }
    var selectedBuildingId by remember { mutableStateOf(buildings.firstOrNull()?.id.orEmpty()) }
    var selectedFlat by remember { mutableStateOf("") }
    var buildingMenu by remember { mutableStateOf(false) }
    var flatMenu by remember { mutableStateOf(false) }
    val selectedBuilding = buildings.firstOrNull { it.id == selectedBuildingId }
    val availableFlats = selectedBuilding?.flats.orEmpty().filter { flat ->
        tenants.none { it.active && it.buildingId == selectedBuildingId && it.flat == flat }
    }
    if (selectedFlat !in availableFlats) selectedFlat = availableFlats.firstOrNull().orEmpty()
    val rent = rentText.toLongOrNull()
    val familyCount = familyCountText.toIntOrNull()
    val aadhaarFingerprintResult = remember(aadhaar) {
        if (aadhaar.length == 12) runCatching { identityFingerprint(aadhaar) } else null
    }
    val duplicateAadhaar = aadhaarFingerprintResult?.getOrNull()?.let { it in existingIdentityKeys } == true
    val aadhaarError = aadhaarFingerprintResult?.exceptionOrNull()?.localizedMessage

    fun resizeFamilyMembers(count: Int) {
        val detailCount = if (count > 0) 1 else 0
        familyMembers.drop(detailCount).forEach { removed ->
            val owner = "family:${removed.id}"
            onDocumentFilesChange(owner, emptyList())
            onDiscardPhoto(owner)
        }
        familyMembers = when {
            detailCount < familyMembers.size -> familyMembers.take(detailCount)
            detailCount > familyMembers.size -> familyMembers + List(detailCount - familyMembers.size) { FamilyDraft() }
            else -> familyMembers
        }
    }

    fun updateMember(updated: FamilyDraft) {
        familyMembers = familyMembers.map { if (it.id == updated.id) updated else it }
    }

    val mainDocuments = documentFiles["tenant"].orEmpty()
    val validFamily = familyMembers.all { member ->
        member.name.isNotBlank() &&
            member.relationship.isNotBlank() &&
            !photoUris["family:${member.id}"].isNullOrBlank() &&
            documentFiles["family:${member.id}"].orEmpty().isNotEmpty()
    }
    val registrationReady = name.isNotBlank() &&
        selectedBuilding != null &&
        selectedFlat.isNotBlank() &&
        rent != null && rent > 0 &&
        aadhaar.length == 12 &&
        aadhaarFingerprintResult?.isSuccess == true &&
        !duplicateAadhaar &&
        isValidStartDate(startDate) &&
        !photoUris["tenant"].isNullOrBlank() &&
        mainDocuments.isNotEmpty() &&
        familyCount != null && familyCount in 0..20 &&
        familyMembers.size == (if (familyCount > 0) 1 else 0) && validFamily

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register a tenant") },
        text = {
            Column(
                Modifier.height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Tenant full name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = aadhaar,
                    onValueChange = { aadhaar = it.filter(Char::isDigit).take(12) },
                    label = { Text("Aadhaar number *") },
                    supportingText = {
                        Text(
                            when {
                                duplicateAadhaar -> "This Aadhaar is already registered. Use the existing tenant record."
                                aadhaarError != null -> "Could not securely verify Aadhaar: $aadhaarError"
                                aadhaar.length < 12 -> "Enter 12 digits. The full Aadhaar is not saved; duplicate checking uses a device-keyed verifier."
                                else -> "Aadhaar accepted; only its masked form and secure verifier are retained."
                            },
                            color = if (duplicateAadhaar || aadhaarError != null) Red else Muted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownChoice("Building", selectedBuilding?.name ?: "Choose building", buildingMenu, { buildingMenu = it }) {
                    buildings.forEach { building ->
                        DropdownMenuItem(text = { Text(building.name) }, onClick = {
                            selectedBuildingId = building.id
                            selectedFlat = ""
                            buildingMenu = false
                        })
                    }
                }
                DropdownChoice("Available flat", selectedFlat.trim().ifBlank { if (availableFlats.isEmpty()) "No flats available" else "Choose flat" }, flatMenu, { flatMenu = it }) {
                    availableFlats.forEach { flat ->
                        DropdownMenuItem(text = { Text("Flat ${flat.trim()}") }, onClick = { selectedFlat = flat; flatMenu = false })
                    }
                }
                OutlinedTextField(rentText, { rentText = it.filter(Char::isDigit).take(9) }, label = { Text("Monthly rent (₹) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    startDate,
                    { startDate = it.take(10) },
                    label = { Text("Living in flat since (YYYY-MM-DD) *") },
                    supportingText = { Text("Defaults to today; change this for an earlier move-in date.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PhotoCaptureButton(
                    label = "Capture tenant's live photo *",
                    photoUri = photoUris["tenant"],
                    onCapture = { onCapturePhoto("tenant") }
                )
                OutlinedTextField(
                    familyCountText,
                    { value ->
                        val filtered = value.filter(Char::isDigit).take(2)
                        familyCountText = filtered
                        filtered.toIntOrNull()?.takeIf { it in 0..20 }?.let(::resizeFamilyMembers)
                    },
                    label = { Text("Number of family members, excluding tenant") },
                    supportingText = { Text("Enter 0 if the tenant lives alone. For any number above 0, capture details for one family representative.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                familyMembers.forEachIndexed { index, member ->
                    Card(colors = CardDefaults.cardColors(containerColor = Canvas), shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Family member ${index + 1}", color = Green, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                member.name,
                                { updateMember(member.copy(name = it)) },
                                label = { Text("Full name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                member.relationship,
                                { updateMember(member.copy(relationship = it)) },
                                label = { Text("Relationship to tenant *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PhotoCaptureButton(
                                label = "Capture live photo *",
                                photoUri = photoUris["family:${member.id}"],
                                onCapture = { onCapturePhoto("family:${member.id}") }
                            )
                            DocumentPickerSection(
                                label = "Member documents *",
                                owner = "family:${member.id}",
                                files = documentFiles["family:${member.id}"].orEmpty(),
                                onPick = onPickDocuments,
                                onRemove = onDocumentFilesChange
                            )
                        }
                    }
                }
                OutlinedTextField(documents, { documents = it }, label = { Text("Tenant document notes (optional)") }, supportingText = { Text("Add document reference notes if needed.") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                DocumentPickerSection(
                    label = "Tenant's identity document *",
                    owner = "tenant",
                    files = mainDocuments,
                    onPick = onPickDocuments,
                    onRemove = onDocumentFilesChange
                )
                Text("Required: tenant photo and at least one tenant document. If the family count is above 0, provide details, live photo and at least one document for one family representative only. Other family members do not need individual records. With 0 family members, only the tenant's photo and document are required.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val savedFamily = familyMembers.map { member ->
                        FamilyMember(
                            name = member.name.trim(),
                            relationship = member.relationship.trim(),
                            photoUri = photoUris.getValue("family:${member.id}"),
                            documentFiles = documentFiles["family:${member.id}"].orEmpty()
                        )
                    }
                    onAdd(
                        name, phone, selectedBuildingId, selectedFlat, rent ?: 0,
                        savedFamily.joinToString { it.name }, documents, startDate, mainDocuments,
                        photoUris.getValue("tenant"), savedFamily,
                        aadhaarFingerprintResult!!.getOrThrow(),
                        "XXXX XXXX ${aadhaar.takeLast(4)}"
                    )
                },
                enabled = registrationReady
            ) { Text("Register tenant") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PhotoCaptureButton(label: String, photoUri: String?, onCapture: () -> Unit) {
    OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = if (photoUri == null) Green else Color(0xFF25845E), modifier = Modifier.size(18.dp))
        Text(
            if (photoUri == null) label else "${label.removeSuffix(" *")} captured · Retake",
            modifier = Modifier.padding(start = 7.dp),
            color = if (photoUri == null) Green else Color(0xFF25845E)
        )
    }
}

@Composable
private fun DocumentPickerSection(
    label: String,
    owner: String,
    files: List<DocumentFile>,
    onPick: (String) -> Unit,
    onRemove: (String, List<DocumentFile>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { onPick(owner) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Choose image or PDF", modifier = Modifier.padding(start = 7.dp))
        }
        files.forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                Text(file.name, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onRemove(owner, files.filterNot { it.uri == file.uri }) }) { Text("Remove", color = Red) }
            }
        }
    }
}

@Composable
private fun TenantDocumentLinks(files: List<DocumentFile>) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    val drive = LocalDrive.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Attached documents", color = Muted, style = MaterialTheme.typography.labelSmall)
        files.forEach { file ->
            TextButton(
                onClick = {
                    scope.launch {
                        try {
                            val (uri, mime) = if (isDriveUri(file.uri)) {
                                val local = drive?.download(file.uri) ?: error("Cloud storage is not ready.")
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local) to
                                    (java.net.URLConnection.guessContentTypeFromName(file.name) ?: "*/*")
                            } else {
                                val u = Uri.parse(file.uri)
                                u to (context.contentResolver.getType(u) ?: "*/*")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No app is available to open ${file.name}.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open ${file.name}: ${e.localizedMessage ?: "access problem"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                Text(file.name, color = Green, modifier = Modifier.padding(start = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CreateBillDialog(
    tenants: List<Tenant>,
    bills: List<RentBill>,
    onDismiss: () -> Unit,
    onCreate: (Tenant, String, Long, Long) -> Unit
) {
    var rateText by remember { mutableStateOf(DEFAULT_ELECTRICITY_RATE.toString()) }
    var selectedTenantId by remember { mutableStateOf(tenants.firstOrNull()?.id.orEmpty()) }
    var month by remember { mutableStateOf(currentMonth()) }
    var unitsText by remember { mutableStateOf("") }
    var tenantMenu by remember { mutableStateOf(false) }
    val tenant = tenants.firstOrNull { it.id == selectedTenantId }
    val units = unitsText.toLongOrNull()
    val rate = rateText.toLongOrNull()
    val alreadyGenerated = tenant != null && bills.any { it.tenantId == tenant.id && it.month == month }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate monthly bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownChoice("Tenant", tenant?.name ?: "Choose tenant", tenantMenu, { tenantMenu = it }) {
                    tenants.forEach { item ->
                        DropdownMenuItem(text = { Text("${item.name} · Flat ${item.flat.trim()}") }, onClick = {
                            selectedTenantId = item.id
                            tenantMenu = false
                        })
                    }
                }
                OutlinedTextField(month, { month = it.take(7) }, label = { Text("Bill month (YYYY-MM)") }, supportingText = { Text("Use a different month to create a past or future bill.") }, singleLine = true)
                OutlinedTextField(unitsText, { unitsText = it.filter(Char::isDigit).take(8) }, label = { Text("Electricity units used") }, singleLine = true)
                OutlinedTextField(rateText, { rateText = it.filter(Char::isDigit).take(4) }, label = { Text("Rate per unit (₹)") }, singleLine = true)
                if (tenant != null && units != null && units >= 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Bill preview", color = Green, fontWeight = FontWeight.Bold)
                            Text("Rent ${money(tenant.rent)} + electricity ${money(units * (rate ?: 0))} = ${money(tenant.rent + units * (rate ?: 0))}", color = Ink, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (alreadyGenerated) Text("A bill already exists for this tenant and month.", color = Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (tenant != null && units != null && rate != null) onCreate(tenant, month, units, rate) },
                enabled = tenant != null && month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")) && units != null && units >= 0 && rate != null && rate > 0 && !alreadyGenerated
            ) { Text("Generate bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentDialog(bill: RentBill, tenant: Tenant?, onDismiss: () -> Unit, onPay: (Long, String, String) -> Unit) {
    var amountText by remember { mutableStateOf(bill.due.toString()) }
    var date by remember { mutableStateOf(today()) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("${tenant?.name ?: "Tenant"} · ${bill.month}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text("Outstanding balance: ${money(bill.due)}", color = Ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(amountText, { amountText = it.filter(Char::isDigit).take(9) }, label = { Text("Payment received (₹)") }, singleLine = true)
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Date received (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(note, { note = it.take(80) }, label = { Text("Note (cash, UPI…) optional") }, singleLine = true)
                Text("Enter a smaller amount to record a partial payment.", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (bill.payments.isNotEmpty()) {
                    Text("Earlier payments", color = Muted, style = MaterialTheme.typography.labelMedium)
                    bill.payments.forEach { Text("${it.date} · ${money(it.amount)} ${it.note}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount != null) onPay(amount, date, note.trim()) },
                enabled = amount != null && amount > 0 && amount <= bill.due && isValidStartDate(date)
            ) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DropdownChoice(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(onClick = { onExpandedChange(true) }, enabled = value != "No flats available", modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(value, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, content = content)
        }
    }
}

@Composable
private fun PageIntro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 7.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Green)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, caption: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = Amber) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Text(value, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, fontSize = 18.sp)
            Text(caption, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Green) }
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String, button: String?, onClick: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(48.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Apartment, contentDescription = null, tint = Green)
            }
            Text(title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (button != null && onClick != null) {
                Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(button) }
            }
        }
    }
}

@Composable
private fun SmallPill(text: String) {
    Surface(color = Color(0xFF2C7A62), shape = RoundedCornerShape(30.dp)) {
        Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InitialAvatar(name: String) {
    Box(Modifier.size(42.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "T", color = Green, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BillLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun flatAddress(data: RentalData, tenant: Tenant): String {
    val buildingName = data.buildings.firstOrNull { it.id == tenant.buildingId }?.name ?: "building"
    return "$buildingName, flat ${tenant.flat.trim()}"
}

private fun personFiles(photoUri: String?, documents: List<DocumentFile>, photoName: String): List<DocumentFile> =
    listOfNotNull(photoUri?.let { DocumentFile(it, photoName) }) + documents

private fun deletePrivateAttachment(context: android.content.Context, attachmentUri: String) {
    val uri = Uri.parse(attachmentUri)
    if (uri.authority != "${context.packageName}.fileprovider") return
    val fileName = uri.lastPathSegment ?: return
    when {
        fileName.matches(Regex("[0-9a-fA-F-]{36}\\.jpg")) ->
            File(File(context.filesDir, "tenant_photos"), fileName).delete()
        fileName.matches(Regex("[0-9a-fA-F-]{36}\\.pdf")) ->
            File(File(context.filesDir, "tenant_documents"), fileName).delete()
    }
}

private fun documentDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Attached document"
}

private fun isValidStartDate(value: String): Boolean =
    runCatching { java.time.LocalDate.parse(value) }.isSuccess

private fun money(amount: Long): String = "₹" + "%,d".format(amount)
