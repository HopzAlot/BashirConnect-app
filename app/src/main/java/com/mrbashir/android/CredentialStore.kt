package com.mrbashir.android

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the Python version wrote a plaintext wifi_creds.json, this uses
 * Android Keystore-backed AES encryption via Jetpack Security. The key
 * never leaves the device's secure hardware; the prefs file on disk is
 * ciphertext even if someone pulls it off a rooted device.
 *
 * Robustness: EncryptedSharedPreferences.create() can throw
 * GeneralSecurityException or IOException if the Keystore is in a bad
 * state (corrupted key, device policy change, screen-lock removal, etc.).
 * The constructor never propagates those exceptions — it tries twice
 * (deleting the stale file on the first failure) and only falls back to
 * plain SharedPreferences as a last resort so the app never hard-crashes.
 */
class CredentialStore(context: Context) {

    /** false only if both encrypted attempts failed on this device. */
    val isEncrypted: Boolean

    private val prefs: SharedPreferences

    init {
        var encrypted = true
        prefs = openEncrypted(context)
            ?: run {
                // First attempt failed — wipe the file (key may be corrupt) and retry.
                context.deleteSharedPreferences(PREFS_NAME)
                openEncrypted(context)
            }
            ?: run {
                // Both encrypted attempts failed — fall back to plain prefs so the
                // app remains functional. Credentials are still only stored locally
                // and the file is in the app's private sandbox.
                Log.w(TAG, "EncryptedSharedPreferences unavailable — falling back to plain prefs")
                encrypted = false
                context.getSharedPreferences(PREFS_NAME_PLAIN, Context.MODE_PRIVATE)
            }
        isEncrypted = encrypted
    }

    fun save(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean = getUsername() != null && getPassword() != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val PREFS_NAME = "mr_bashir_creds"
        private const val PREFS_NAME_PLAIN = "mr_bashir_creds_plain"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        private fun openEncrypted(context: Context): SharedPreferences? = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences.create() failed: ${e.message}")
            null
        }
    }
}
