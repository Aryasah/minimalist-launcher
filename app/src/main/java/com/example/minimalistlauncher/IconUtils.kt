package com.example.minimalistlauncher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Convert a Drawable to a monochrome ImageBitmap by desaturating and tinting.
 * tintColor is an Android color int (e.g., Color.BLACK). If null -> use original bitmap.
 */
fun Drawable.toMonochromeImageBitmap(tintColor: Int? = null, sizePx: Int = 72): ImageBitmap? {
    return try {
        val bmp = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            toBitmap(intrinsicWidth.coerceAtMost(sizePx), intrinsicHeight.coerceAtMost(sizePx), Bitmap.Config.ARGB_8888)
        } else {
            toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }

        // Create grayscale bitmap
        val grayBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBmp)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f) // desaturate
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        // Apply tint if requested by drawing into another bitmap using SRC_IN
        if (tintColor != null) {
            val tinted = Bitmap.createBitmap(grayBmp.width, grayBmp.height, Bitmap.Config.ARGB_8888)
            val c2 = Canvas(tinted)
            val p2 = Paint()
            // draw tint background
            p2.color = tintColor
            c2.drawRect(0f, 0f, tinted.width.toFloat(), tinted.height.toFloat(), p2)
            // mask with grayscale icon
            p2.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            c2.drawBitmap(grayBmp, 0f, 0f, p2)
            p2.xfermode = null
            return tinted.asImageBitmap()
        }

        grayBmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/** Safe helper to fetch app icon drawable for a package name */
fun Context.getAppIconDrawableSafe(packageName: String): Drawable? = try {
    packageManager.getApplicationIcon(packageName)
} catch (e: Exception) {
    null
}
