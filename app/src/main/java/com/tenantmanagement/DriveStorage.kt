package com.tenantmanagement

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val DRIVE_PREFIX = "drive:"

fun isDriveUri(uri: String) = uri.startsWith(DRIVE_PREFIX)
fun driveId(uri: String) = uri.removePrefix(DRIVE_PREFIX)

/**
 * Stores identity photos and documents in the signed-in owner's own Google Drive "app data" folder: free, private to this
 * app, and not visible in the Drive UI. [token] returns a fresh OAuth access token for [DRIVE_APPDATA_SCOPE].
 */
class DriveStorage(private val context: Context, private val token: suspend (forceRefresh: Boolean) -> String) {

    /** Uploads a local content URI and returns its "drive:<id>" reference. */
    suspend fun upload(source: Uri, name: String, mime: String): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Could not read $name")
        "$DRIVE_PREFIX${uploadBytes(bytes, name, mime)}"
    }

    private suspend fun uploadBytes(bytes: ByteArray, name: String, mime: String): String {
        val boundary = "nestkeep-${UUID.randomUUID()}"
        val metadata = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val body = java.io.ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val response = call("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST") { conn ->
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        return JSONObject(String(response)).getString("id")
    }

    /** Downloads a stored file into the app cache (reusing an earlier download) and returns it. */
    suspend fun download(uri: String): File = withContext(Dispatchers.IO) {
        val id = driveId(uri)
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid file reference" }
        val dir = File(context.cacheDir, "drive").apply { mkdirs() }
        val file = File(dir, id)
        if (!file.exists()) {
            val bytes = call("https://www.googleapis.com/drive/v3/files/$id?alt=media", "GET") { }
            file.writeBytes(bytes)
        }
        file
    }

    suspend fun delete(uri: String) = withContext(Dispatchers.IO) {
        if (!isDriveUri(uri)) return@withContext
        runCatching { call("https://www.googleapis.com/drive/v3/files/${driveId(uri)}", "DELETE") { } }
        File(File(context.cacheDir, "drive"), driveId(uri)).delete()
    }

    private suspend fun call(url: String, method: String, configure: (HttpURLConnection) -> Unit): ByteArray {
        var refresh = false
        repeat(2) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Authorization", "Bearer ${token(refresh)}")
                configure(conn)
                val code = conn.responseCode
                if (code == 401 && !refresh) {
                    refresh = true
                    return@repeat
                }
                if (code !in 200..299) {
                    val detail = conn.errorStream?.use { String(it.readBytes()) }.orEmpty().take(200)
                    throw IOException("Google Drive error $code $detail")
                }
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Google Drive authorization failed")
    }
}
