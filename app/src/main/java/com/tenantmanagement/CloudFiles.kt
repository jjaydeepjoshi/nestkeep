package com.tenantmanagement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

private const val CLOUD_PREFIX = "cloud:"
private const val CHUNK_BYTES = 700_000 // Firestore documents are limited to 1 MiB
private const val MAX_FILE_BYTES = 6_000_000
private const val MAX_IMAGE_EDGE = 1600

fun isCloudFile(uri: String) = uri.startsWith(CLOUD_PREFIX)
fun cloudFileId(uri: String) = uri.removePrefix(CLOUD_PREFIX)

/**
 * Stores identity photos and documents in the owner's Firestore tree (users/{uid}/files), so Google sign-in is the only
 * permission prompt. Images are downscaled and recompressed; every file is split into chunks under Firestore's
 * document size limit. Free on the Spark plan and covered by the per-user rules in firebase/firestore.rules.
 */
class CloudFiles(
    private val context: Context,
    uid: String,
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val files = db.collection("users").document(uid).collection("files")
    private val db = db

    /** Uploads a local content URI and returns its "cloud:<id>" reference. */
    suspend fun upload(source: Uri, name: String, mime: String): String = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Could not read $name")
        val (bytes, storedMime) = if (mime.startsWith("image/")) compress(raw) to "image/jpeg" else raw to mime
        if (bytes.size > MAX_FILE_BYTES) throw IOException("$name is too large (limit ${MAX_FILE_BYTES / 1_000_000} MB)")
        val id = UUID.randomUUID().toString()
        val chunks = bytes.toList().chunked(CHUNK_BYTES).map { it.toByteArray() }
        val doc = files.document(id)
        val batch = db.batch()
        batch.set(doc, mapOf("name" to name, "mime" to storedMime, "size" to bytes.size, "chunks" to chunks.size))
        chunks.forEachIndexed { i, c -> batch.set(doc.collection("chunks").document(i.toString()), mapOf("data" to Blob.fromBytes(c))) }
        withTimeout(60_000) { batch.commit().await() }
        "$CLOUD_PREFIX$id"
    }

    /** Downloads a stored file into the app cache (reusing an earlier download) and returns it. */
    suspend fun download(uri: String): File = withContext(Dispatchers.IO) {
        val id = cloudFileId(uri)
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid file reference" }
        val dir = File(context.cacheDir, "cloud").apply { mkdirs() }
        val file = File(dir, id)
        if (!file.exists()) {
            val doc = files.document(id)
            val count = (doc.get().await().getLong("chunks") ?: throw IOException("File not found")).toInt()
            val out = ByteArrayOutputStream()
            for (i in 0 until count) {
                val data = doc.collection("chunks").document(i.toString()).get().await().getBlob("data")
                    ?: throw IOException("File is incomplete")
                out.write(data.toBytes())
            }
            file.writeBytes(out.toByteArray())
        }
        file
    }

    suspend fun delete(uri: String) = withContext(Dispatchers.IO) {
        if (!isCloudFile(uri)) return@withContext
        val id = cloudFileId(uri)
        runCatching {
            val doc = files.document(id)
            val batch = db.batch()
            doc.collection("chunks").get().await().documents.forEach { batch.delete(it.reference) }
            batch.delete(doc)
            batch.commit().await()
        }
        File(File(context.cacheDir, "cloud"), id).delete()
    }

    /** Downscales to [MAX_IMAGE_EDGE], applies EXIF rotation and re-encodes as JPEG. */
    private fun compress(raw: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return raw
        val scale = MAX_IMAGE_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            val orientation = runCatching {
                ExifInterface(raw.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        var quality = 82
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_FILE_BYTES || quality <= 40) return out.toByteArray()
            quality -= 12
        }
    }
}
