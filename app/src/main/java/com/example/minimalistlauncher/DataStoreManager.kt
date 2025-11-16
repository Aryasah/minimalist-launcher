package com.example.minimalistlauncher

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "minimalist_settings"
val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)
private const val TAG = "DataStoreManager"

object Keys {
    val WHITELIST = stringPreferencesKey("focus_whitelist")          // "pkg1|pkg2|..."
    val HOME_APPS = stringPreferencesKey("home_apps_ordered")       // "pkgA|pkgB|..."
    val ICON_PACK = stringPreferencesKey("icon_pack_package")       // package name or null

    // Font persistence keys (new)
    val FONT_TYPE = stringPreferencesKey("launcher_font_type")      // "res"|"uri"|"pkg"
    val FONT_VALUE = stringPreferencesKey("launcher_font_value")    // resKey or uri string or "pkg:fontResName"
    val FONT_SIZE = intPreferencesKey("launcher_font_size") // stored as SP int (e.g. 16)

    val FOCUS_ACTIVE = booleanPreferencesKey("focus_active")          // true while a focus session is active
    val FOCUS_START_MS = longPreferencesKey("focus_start_ms")         // epoch ms
    val FOCUS_DURATION_SEC = intPreferencesKey("focus_duration_sec")  // seconds
    val FOCUS_TYPE = stringPreferencesKey("focus_type")               // "pomodoro"/"custom"
    val FOCUS_BG_SOUND = stringPreferencesKey("focus_bg_sound")       // optional selected sound asset
    val FOCUS_END_ELAPSED_MS = longPreferencesKey("focus_end_elapsed_ms") // monotonic end time (SystemClock.elapsedRealtime())
    val FOCUS_PAUSED = booleanPreferencesKey("focus_paused")
    val FOCUS_REMAINING_SEC = intPreferencesKey("focus_remaining_sec")
    val SHAKE_TOGGLE = booleanPreferencesKey("shake_flashlight_enabled")
}
class DataStoreManager(private val context: Context) {

    private fun parseStringOrSet(raw: Any?): Set<String> {
        return when (raw) {
            is String -> raw.split("|").mapNotNull { it.takeIf { s -> s.isNotBlank() } }.toSet()
            is Set<*> -> raw.filterIsInstance<String>().toSet()
            else -> emptySet()
        }
    }

    val whitelistFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            val raw: Any? = prefs[Keys.WHITELIST]
            parseStringOrSet(raw)
        }

    suspend fun addToWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            val currentRaw: Any? = prefs[Keys.WHITELIST]
            val current = parseStringOrSet(currentRaw).toMutableSet()
            current.add(pkg)
            if (current.isEmpty()) prefs.remove(Keys.WHITELIST) else prefs[Keys.WHITELIST] = current.joinToString("|")
            Log.d(TAG, "addToWhitelist -> ${current.joinToString("|")}")
        }
    }

    suspend fun removeFromWhitelist(pkg: String) {
        context.dataStore.edit { prefs ->
            val currentRaw: Any? = prefs[Keys.WHITELIST]
            val current = parseStringOrSet(currentRaw).toMutableSet()
            current.remove(pkg)
            if (current.isEmpty()) prefs.remove(Keys.WHITELIST) else prefs[Keys.WHITELIST] = current.joinToString("|")
            Log.d(TAG, "removeFromWhitelist -> ${current.joinToString("|")}")
        }
    }

    suspend fun setWhitelist(newSet: Set<String>) {
        context.dataStore.edit { prefs ->
            if (newSet.isEmpty()) prefs.remove(Keys.WHITELIST) else prefs[Keys.WHITELIST] = newSet.joinToString("|")
            Log.d(TAG, "setWhitelist -> ${newSet.joinToString("|")}")
        }
    }

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
        Log.d("DataStoreManager", "setHomeApps wrote: ${newOrderedList.joinToString(",")}")
    }

    suspend fun addHomeApp(pkg: String) {
        context.dataStore.edit { prefs ->
            val currentRaw: Any? = prefs[Keys.HOME_APPS]
            val current = when (currentRaw) {
                is String -> currentRaw.split("|").filter { it.isNotBlank() }.toMutableList()
                is Set<*> -> currentRaw.filterIsInstance<String>().toMutableList()
                else -> mutableListOf()
            }
            if (!current.contains(pkg) && current.size < 5) current.add(pkg)
            if (current.isEmpty()) prefs.remove(Keys.HOME_APPS) else prefs[Keys.HOME_APPS] = current.joinToString("|")
            Log.d(TAG, "addHomeApp -> ${current.joinToString("|")}")
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
            if (current.isEmpty()) prefs.remove(Keys.HOME_APPS) else prefs[Keys.HOME_APPS] = current.joinToString("|")
            Log.d(TAG, "removeHomeApp -> ${current.joinToString("|")}")
        }
    }

    // icon pack storage
    val selectedIconPackFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ICON_PACK]
    }

    suspend fun setSelectedIconPack(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(Keys.ICON_PACK)
                Log.d(TAG, "setSelectedIconPack -> removed (using system icons)")
            } else {
                prefs[Keys.ICON_PACK] = packageName
                Log.d(TAG, "setSelectedIconPack -> $packageName")
            }
        }
    }

    // helper to read the current selected pack once (useful in tests or quick debug)
    suspend fun getSelectedIconPackOnce(): String? {
        val value = context.dataStore.data.first()[Keys.ICON_PACK]
        Log.d(TAG, "getSelectedIconPackOnce -> $value")
        return value
    }

    // --------------------
    // Font storage helpers (new)
    // --------------------

    /** Flow: current font type (res/uri/pkg) or null */
    val launcherFontTypeFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.FONT_TYPE] }

    /** Flow: current font value (resKey or uri string or pkg:fontResName) or null */
    val launcherFontValueFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.FONT_VALUE] }

    /** Flow: launcher font size in SP (default 16) */
    val launcherFontSizeFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[Keys.FONT_SIZE] ?: 16 }

    /** Atomically set font selection (type + value) */
    suspend fun setLauncherFont(type: String, value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_TYPE] = type
            prefs[Keys.FONT_VALUE] = value
        }
        Log.d(TAG, "setLauncherFont -> type=$type value=$value")
    }

    /** Clear stored launcher font */
    suspend fun clearLauncherFont() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.FONT_TYPE)
            prefs.remove(Keys.FONT_VALUE)
        }
        Log.d(TAG, "clearLauncherFont -> cleared")
    }

    /** Convenience: read both values once (useful on startup) */
    suspend fun getLauncherFontOnce(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.FONT_TYPE] to prefs[Keys.FONT_VALUE]
    }

    /** Atomically set font size (store int SP) */
    suspend fun setLauncherFontSize(sizeSp: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = sizeSp
        }
        Log.d(TAG, "setLauncherFontSize -> $sizeSp")
    }

    /** Read once (useful on startup) */
    suspend fun getLauncherFontSizeOnce(): Int {
        return context.dataStore.data.first()[Keys.FONT_SIZE] ?: 16
    }

    // flows
    val focusActiveFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_ACTIVE] ?: false }

    val focusStartMsFlow: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_START_MS] }

    val focusDurationSecFlow: Flow<Int?> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_DURATION_SEC] }

    // setters
    suspend fun setFocusActive(active: Boolean) {
        context.dataStore.edit { prefs ->
            if (!active) {
                prefs.remove(Keys.FOCUS_ACTIVE)
                prefs.remove(Keys.FOCUS_START_MS)
                prefs.remove(Keys.FOCUS_DURATION_SEC)
                prefs.remove(Keys.FOCUS_TYPE)
            } else {
                prefs[Keys.FOCUS_ACTIVE] = true
            }
        }
        Log.d(TAG, "setFocusActive -> $active")
    }

    suspend fun writeFocusSession(startMs: Long, durationSec: Int, type: String = "custom", bgSound: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FOCUS_ACTIVE] = true
            prefs[Keys.FOCUS_START_MS] = startMs
            prefs[Keys.FOCUS_DURATION_SEC] = durationSec
            prefs[Keys.FOCUS_TYPE] = type
            if (bgSound == null) prefs.remove(Keys.FOCUS_BG_SOUND) else prefs[Keys.FOCUS_BG_SOUND] = bgSound
        }
        Log.d(TAG, "writeFocusSession -> start=$startMs dur=$durationSec type=$type")
    }

    suspend fun clearFocusSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.FOCUS_ACTIVE)
            prefs.remove(Keys.FOCUS_START_MS)
            prefs.remove(Keys.FOCUS_DURATION_SEC)
            prefs.remove(Keys.FOCUS_TYPE)
            prefs.remove(Keys.FOCUS_BG_SOUND)
        }
        Log.d(TAG, "clearFocusSession")
    }

    // read once helpers (optional)
    suspend fun getCurrentFocusOnce(): Triple<Boolean, Long?, Int?> {
        val prefs = context.dataStore.data.first()
        val active = prefs[Keys.FOCUS_ACTIVE] ?: false
        val start = prefs[Keys.FOCUS_START_MS]
        val dur = prefs[Keys.FOCUS_DURATION_SEC]
        return Triple(active, start, dur)
    }

    val focusEndElapsedFlow: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_END_ELAPSED_MS] }

    suspend fun writeFocusEndElapsed(endElapsedMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FOCUS_END_ELAPSED_MS] = endElapsedMs
        }
    }

    suspend fun clearFocusEndElapsed() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.FOCUS_END_ELAPSED_MS)
        }
    }

    val focusPausedFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_PAUSED] ?: false }

    val focusRemainingFlow: Flow<Int?> = context.dataStore.data
        .map { prefs -> prefs[Keys.FOCUS_REMAINING_SEC] }

    suspend fun setFocusPaused(paused: Boolean) {
        context.dataStore.edit { prefs ->
            if (!paused) prefs.remove(Keys.FOCUS_PAUSED) else prefs[Keys.FOCUS_PAUSED] = true
        }
    }

    suspend fun setFocusRemainingSeconds(sec: Int?) {
        context.dataStore.edit { prefs ->
            if (sec == null) prefs.remove(Keys.FOCUS_REMAINING_SEC) else prefs[Keys.FOCUS_REMAINING_SEC] = sec
        }
    }

    val shakeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.SHAKE_TOGGLE] ?: false }

    suspend fun setShakeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHAKE_TOGGLE] = enabled
        }
    }

    suspend fun getShakeEnabledOnce(): Boolean {
        return context.dataStore.data.first()[Keys.SHAKE_TOGGLE] ?: false
    }


}

