package com.example.minimalistlauncher
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Return a Drawable for the app's launcher icon, or null if not available.
 * Safe to call from Compose remember { } blocks.
 */
fun Context.getAppIconDrawableSafe(packageName: String): Drawable? {
    return try {
        val pm: PackageManager = this.packageManager
        // getApplicationIcon may throw if package not found
        pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        // fallback: try resolveActivity icon (if package has a launcher activity)
        try {
            val pm: PackageManager = this.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            val resolveInfo = if (intent != null) pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) else null
            resolveInfo?.loadIcon(pm)
        } catch (t: Throwable) {
            null
        }
    } catch (t: Throwable) {
        // defensive fallback
        null
    }
}
