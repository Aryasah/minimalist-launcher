package com.example.minimalistlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Convert a Drawable to ImageBitmap, applying a tint when rendered in Compose.
 * Returns null if conversion fails.
 */
fun Drawable.toImageBitmapSafe(tint: Int? = null): ImageBitmap? {
    return try {
        // Ensure drawable has intrinsic size or fallback
        val bmp: Bitmap = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            toBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        } else {
            toBitmap(72, 72, Bitmap.Config.ARGB_8888)
        }
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * A helper that fetches an app icon drawable and returns it (original Drawable).
 */
fun Context.getAppIconDrawable(packageName: String) = try {
    val pm = packageManager
    pm.getApplicationIcon(packageName)
} catch (e: Exception) {
    null
}
