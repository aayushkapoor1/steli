package com.steli.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Manages authentication state and token persistence via EncryptedSharedPreferences.
 */
object AuthManager {
    private const val LEGACY_PREFS_NAME = "steli_auth"
    private const val PREFS_NAME = "steli_auth_encrypted"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"

    private lateinit var prefs: SharedPreferences

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserPublic?>(null)
    val currentUser: StateFlow<UserPublic?> = _currentUser.asStateFlow()

    fun init(context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        migrateFromLegacyPrefs(context)

        val token = prefs.getString(KEY_TOKEN, null)
        if (token != null) {
            val user = UserPublic(
                id = prefs.getInt(KEY_USER_ID, 0),
                username = prefs.getString(KEY_USERNAME, "") ?: "",
                firstName = prefs.getString(KEY_FIRST_NAME, "") ?: "",
                lastName = prefs.getString(KEY_LAST_NAME, "") ?: "",
            )
            _currentUser.value = user
            _isLoggedIn.value = true
        }
    }

    private fun migrateFromLegacyPrefs(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyToken = legacy.getString(KEY_TOKEN, null) ?: return

        if (prefs.getString(KEY_TOKEN, null) == null) {
            prefs.edit()
                .putString(KEY_TOKEN, legacyToken)
                .putInt(KEY_USER_ID, legacy.getInt(KEY_USER_ID, 0))
                .putString(KEY_USERNAME, legacy.getString(KEY_USERNAME, ""))
                .putString(KEY_FIRST_NAME, legacy.getString(KEY_FIRST_NAME, ""))
                .putString(KEY_LAST_NAME, legacy.getString(KEY_LAST_NAME, ""))
                .apply()
        }
        legacy.edit().clear().apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveSession(token: String, user: UserPublic) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FIRST_NAME, user.firstName)
            .putString(KEY_LAST_NAME, user.lastName)
            .apply()
        _currentUser.value = user
        _isLoggedIn.value = true
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _isLoggedIn.value = false
    }
}
