package com.tenantmanagement

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Google sign-in (Credential Manager -> Firebase Auth). */
class AuthManager(private val activity: Activity) {
    private val auth = FirebaseAuth.getInstance()

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

    companion object {
        /** The OAuth web client id Firebase generates once Google sign-in is enabled for the project. */
        fun webClientId(context: Context): String? {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            return if (id == 0) null else context.getString(id)
        }
    }
}
