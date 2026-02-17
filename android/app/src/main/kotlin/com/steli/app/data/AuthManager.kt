package com.steli.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Manages authentication state and token persistence via SharedPreferences.
 */
object AuthManager {
    private const val PREFS_NAME = "steli_auth"
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
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
