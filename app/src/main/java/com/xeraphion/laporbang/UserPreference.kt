package com.xeraphion.laporbang

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_pref")

class UserPreference(private val context: Context) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val IS_ADMIN_KEY = stringPreferencesKey("is_admin")

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: UserPreference? = null


        fun getInstance(context: Context): UserPreference {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreference(context)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data
            .map { it[TOKEN_KEY] }
            .first()

    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
        }
    }

    suspend fun getUserId(): String? {
        return context.dataStore.data
            .map { it[USER_ID_KEY] }
            .first()
    }


    suspend fun saveIsAdmin(role: String) {
        context.dataStore.edit { preferences ->
            preferences[IS_ADMIN_KEY] = role
        }
    }

    suspend fun isAdmin(): Boolean {
        return context.dataStore.data
            .map { it[IS_ADMIN_KEY] == "admin" }
            .first()
    }

    suspend fun clearToken() {
        context.dataStore.edit {
            it.remove(TOKEN_KEY)
            it.remove(USER_ID_KEY)
            it.remove(IS_ADMIN_KEY)
        }
    }
}
