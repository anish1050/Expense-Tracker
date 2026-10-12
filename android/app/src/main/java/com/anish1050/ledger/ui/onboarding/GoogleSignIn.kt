package com.anish1050.ledger.ui.onboarding

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.anish1050.ledger.BuildConfig
import com.anish1050.ledger.container
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// Credential Manager -> Google ID token -> backend exchange -> app JWT.
class GoogleSignIn(private val context: Context) {
    suspend fun run(): Boolean {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val response = runCatching {
            CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
        }.getOrNull() ?: return false
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        val result = runCatching { context.container.api.signInWithGoogle(idToken) }.getOrNull() ?: return false
        context.container.authStore.accessToken = result.accessToken
        context.container.authStore.userId = result.user.id
        return true
    }
}
