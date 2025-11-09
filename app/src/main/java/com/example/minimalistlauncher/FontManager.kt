package com.example.minimalistlauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.annotation.FontRes
import androidx.collection.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import java.io.File

public object FontManager {
    private const val PREFS = "launcher_fonts"
    private const val KEY_TYPE = "font_type"      // "res", "uri", "pkg"
    private const val KEY_VALUE = "font_value"    // resKey or uri string or "pkg:fontname"
    private val cache = LruCache<String, Typeface>(8)

    // Compose-facing FontFamily state (read this from Compose)
    var composeFontFamily by mutableStateOf<FontFamily?>(null)
        private set

    // Keep a copy of android Typeface as well for non-Compose usage (if needed)
    private var currentTypeface: Typeface? = null

    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun cacheKey(type: String, value: String) = "$type:$value"

    fun persistSelection(context: Context, type: String, value: String) {
        prefs(context).edit().putString(KEY_TYPE, type).putString(KEY_VALUE, value).apply()
    }

    fun clearSelection(context: Context) {
        prefs(context).edit().clear().apply()
        currentTypeface = null
        composeFontFamily = null
    }

    // ---- Load from bundled res/font (by resId) ----
    fun loadFromRes(context: Context, @FontRes resId: Int, resKeyForPersist: String): Typeface? {
        val key = cacheKey("res", resKeyForPersist)
        cache.get(key)?.let { return it }
        return try {
            val tf = ResourcesCompat.getFont(context, resId)
            if (tf != null) {
                cache.put(key, tf)
                currentTypeface = tf
                composeFontFamily = FontFamily(tf)
            }
            tf
        } catch (t: Throwable) { t.printStackTrace(); null }
    }

    // ---- Load font resource from another package if exposed as font resource ----
    fun loadFromPackageFont(context: Context, packageName: String, fontResName: String): Typeface? {
        val key = cacheKey("pkg", "$packageName:$fontResName")
        cache.get(key)?.let { return it }
        return try {
            val pkgCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val resId = pkgCtx.resources.getIdentifier(fontResName, "font", packageName)
            if (resId == 0) return null
            val tf = ResourcesCompat.getFont(pkgCtx, resId)
            if (tf != null) {
                cache.put(key, tf)
                currentTypeface = tf
                composeFontFamily = FontFamily(tf)
            }
            tf
        } catch (t: Throwable) { t.printStackTrace(); null }
    }

    // ---- Load from Uri (SAF). Caller is responsible for takePersistableUriPermission(...) if they want long-term access ----
    fun loadFromUri(context: Context, uri: Uri): Typeface? {
        val key = cacheKey("uri", uri.toString())
        cache.get(key)?.let { return it }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val ext = when {
                    uri.path?.contains(".otf", true) == true -> ".otf"
                    uri.path?.contains(".ttf", true) == true -> ".ttf"
                    else -> ".ttf"
                }
                val tmp = File(context.cacheDir, "font_${System.currentTimeMillis()}$ext")
                tmp.outputStream().use { out -> input.copyTo(out) }
                val tf = Typeface.createFromFile(tmp)
                if (tf != null) {
                    cache.put(key, tf)
                    currentTypeface = tf
                    composeFontFamily = FontFamily(tf)
                }
                tf
            }
        } catch (t: Throwable) { t.printStackTrace(); null }
    }

    /**
     * Try to re-apply persisted font on startup.
     * Returns true if a font was successfully loaded.
     * For res-types we expect the app to call loadFromRes with a mapping (see usage in MainActivity).
     */
    fun reapplyPersistedFont(context: Context): Boolean {
        val type = prefs(context).getString(KEY_TYPE, null) ?: return false
        val value = prefs(context).getString(KEY_VALUE, null) ?: return false

        val tf: Typeface? = when (type) {
            "res" -> {
                // res must be reloaded by mapping value->resId in your app (MainActivity shows an example)
                null
            }
            "pkg" -> {
                val parts = value.split(":", limit = 2)
                if (parts.size == 2) loadFromPackageFont(context, parts[0], parts[1]) else null
            }
            "uri" -> runCatching { loadFromUri(context, Uri.parse(value)) }.getOrNull()
            else -> null
        }

        // If we loaded success, currentTypeface & composeFontFamily are already set in the loaders.
        return tf != null
    }

    /**
     * Convenience helper to apply-and-persist when user chooses a font.
     * type: "res" | "uri" | "pkg"
     * value: resKey(for res), uriString, or "pkg:fontResName"
     */
    fun applyAndPersist(context: Context, type: String, value: String, tf: Typeface?) {
        if (tf != null) {
            // update internal
            currentTypeface = tf
            composeFontFamily = FontFamily(tf)
            // persist
            persistSelection(context, type, value)
        }
    }
}
