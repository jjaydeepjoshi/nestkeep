package com.tenantmanagement

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** SHA fingerprints of the certificate this install is signed with (what Firebase must have registered for Google sign-in). */
data class SigningFingerprints(val sha1: String, val sha256: String)

fun signingFingerprints(context: Context): SigningFingerprints? = runCatching {
    val pm = context.packageManager
    val signature: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    }
    signature?.toByteArray()?.let {
        SigningFingerprints(hex(MessageDigest.getInstance("SHA-1").digest(it)), hex(MessageDigest.getInstance("SHA-256").digest(it)))
    }
}.getOrNull()

private fun hex(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it) }
