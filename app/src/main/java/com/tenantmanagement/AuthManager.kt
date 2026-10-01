package com.tenantmanagement

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await

/** Google sign-in (Credential Manager -> Firebase Auth) plus the Drive access token used for document storage. */
class AuthManager(private val activity: Activity) {
    private val auth = FirebaseAuth.getInstance()
    private var consent: CompletableDeferred<String?>? = null
    var consentLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    val user: FirebaseUser? get() = auth.currentUser

    fun addListener(onChange: (FirebaseUser?) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChange(it.currentUser) }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(): FirebaseUser {
        val clientId = webClientId(activity)
            ?: error("Google sign-in is not configured yet (missing web client ID in google-services.json).")
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected sign-in response."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("Sign-in returned no user.")
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Returns a Drive app-data access token, showing Google's consent screen the first time. */
    suspend fun driveToken(@Suppress("UNUSED_PARAMETER") forceRefresh: Boolean): String {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE))).build()
        val result = Identity.getAuthorizationClient(activity).authorize(request).await()
        if (result.hasResolution()) {
            val launcher = consentLauncher ?: error("Drive consent is unavailable.")
            val pending = CompletableDeferred<String?>().also { consent = it }
            launcher.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
            return pending.await() ?: error("Google Drive access was not granted.")
        }
        return result.accessToken ?: error("Google Drive access was not granted.")
    }

    fun onConsentResult(data: android.content.Intent?) {
        val token = runCatching {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
        }.getOrNull()
        consent?.complete(token)
        consent = null
    }

    companion object {
        /** The OAuth web client id Firebase generates once Google sign-in is enabled for the project. */
        fun webClientId(context: Context): String? {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            return if (id == 0) null else context.getString(id)
        }
    }
}
