package com.example.minimalistlauncher

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "minimalist_settings"

private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

object Keys {
    val WHITELIST = stringSetPreferencesKey("focus_whitelist")
}

/**
 * Simple DataStore manager to read/write the whitelist set of package names.
 */
class DataStoreManager(private val context: Context) {

    val whitelistFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            prefs[Keys.WHITELIST] ?: emptySet()
        }

    suspend fun addToWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WHITELIST] ?: emptySet()
            prefs[Keys.WHITELIST] = current + pkg
        }
    }

    suspend fun removeFromWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WHITELIST] ?: emptySet()
            prefs[Keys.WHITELIST] = current - pkg
        }
    }

    suspend fun setWhitelist(newSet: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WHITELIST] = newSet
        }
    }
}
