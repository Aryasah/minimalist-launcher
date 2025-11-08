package com.example.minimalistlauncher

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "minimalist_settings"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

object Keys {
    // We store these as pipe-separated strings (stable, ordered for HOME_APPS)
    val WHITELIST = stringPreferencesKey("focus_whitelist")      // stored as "pkg1|pkg2|..."
    val HOME_APPS = stringPreferencesKey("home_apps_ordered")   // stored as "pkgA|pkgB|..."
}

/**
 * DataStore manager with robust read logic that tolerates older Set<String> values.
 * (Some earlier code paths may have stored a Set; this will handle both.)
 */
class DataStoreManager(private val context: Context) {

    /**
     * Helper: convert any possible backing value into a Set<String>.
     * prefs[KEY] can be:
     *  - a String (our canonical format: "p1|p2|p3")
     *  - a Set<String> (older format from previous implementations)
     *  - null
     */
    private fun parseStringOrSet(raw: Any?): Set<String> {
        return when (raw) {
            is String -> raw.split("|").mapNotNull { it.takeIf { s -> s.isNotBlank() } }.toSet()
            is Set<*> -> raw.filterIsInstance<String>().toSet()
            else -> emptySet()
        }
    }

    /**
     * whitelistFlow returns a Set<String> of package names.
     * We read the preference value safely and normalize it to a Set<String>.
     */
    val whitelistFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            val raw: Any? = prefs[Keys.WHITELIST]
            // If raw is null -> try to recover from older typed entry stored under same key as set
            parseStringOrSet(raw)
        }

    suspend fun addToWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            // Read current in a tolerant way
            val currentRaw: Any? = prefs[Keys.WHITELIST]
            val current = parseStringOrSet(currentRaw).toMutableSet()
            current.add(pkg)
            prefs[Keys.WHITELIST] = current.joinToString("|")
        }
    }

    suspend fun removeFromWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            val currentRaw: Any? = prefs[Keys.WHITELIST]
            val current = parseStringOrSet(currentRaw).toMutableSet()
            current.remove(pkg)
            prefs[Keys.WHITELIST] = current.joinToString("|")
        }
    }

    suspend fun setWhitelist(newSet: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WHITELIST] = newSet.joinToString("|")
        }
    }

    /**
     * HOME APPS - ordered list of package names (stored as pipe-separated string)
     * We tolerate older set formats here too.
     */
    val homeAppsFlow: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            val raw: Any? = prefs[Keys.HOME_APPS]
            when (raw) {
                is String -> raw.split("|").mapNotNull { it.takeIf { s -> s.isNotBlank() } }
                is Set<*> -> raw.filterIsInstance<String>()
                else -> emptyList()
            }
        }

    suspend fun setHomeApps(newOrderedList: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_APPS] = newOrderedList.joinToString("|")
        }
    }

    suspend fun addHomeApp(pkg: String) {
        context.dataStore.edit { prefs ->
            // Read current toleranty
            val currentRaw: Any? = prefs[Keys.HOME_APPS]
            val current = when (currentRaw) {
                is String -> currentRaw.split("|").filter { it.isNotBlank() }.toMutableList()
                is Set<*> -> currentRaw.filterIsInstance<String>().toMutableList()
                else -> mutableListOf()
            }
            if (!current.contains(pkg) && current.size < 5) {
                current.add(pkg)
            }
            prefs[Keys.HOME_APPS] = current.joinToString("|")
        }
    }

    suspend fun removeHomeApp(pkg: String) {
        context.dataStore.edit { prefs ->
            val currentRaw: Any? = prefs[Keys.HOME_APPS]
            val current = when (currentRaw) {
                is String -> currentRaw.split("|").filter { it.isNotBlank() }.toMutableList()
                is Set<*> -> currentRaw.filterIsInstance<String>().toMutableList()
                else -> mutableListOf()
            }
            current.remove(pkg)
            prefs[Keys.HOME_APPS] = current.joinToString("|")
        }
    }
}
