package com.example.minimalistlauncher

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.content.res.XmlResourceParser
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

private const val TAG = "IconUtils"


/**
 * Attempt to parse common appfilter.xml-like mapping in an icon pack.
 * Returns a map packageName -> drawableName if found, otherwise empty map.
 */
public fun parseAppFilterMappings(context: Context, iconPackPackage: String): Map<String, String> {
    val pm = context.packageManager
    try {
        val res = pm.getResourcesForApplication(iconPackPackage)
        // Common file names used by many icon packs:
        val candidateXmlNames = listOf("appfilter", "appfilter.xml", "appfilter_full", "appfilter_custom")
        for (name in candidateXmlNames) {
            val id = res.getIdentifier(name, "xml", iconPackPackage)
            if (id != 0) {
                val parser = res.getXml(id)
                return parseAppFilterXml(parser)
            }
        }

        // some packs ship XML in assets; try to open assets/appfilter.xml
        try {
            val apk = pm.getApplicationInfo(iconPackPackage, 0).sourceDir
            // can't open APK directly here without unpacking; skip for now
        } catch (_: Exception) {
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
    return emptyMap()
}

/**
 * Parse appfilter.xml-style xml from a XmlResourceParser and return mapping package -> drawableName.
 * Supports tags like: <item component="ComponentInfo{com.example/com.example.MainActivity}" drawable="icon_name"/>
 */
fun parseAppFilterXml(parser: XmlResourceParser): Map<String, String> {
    val mappings = mutableMapOf<String, String>()
    try {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                var componentAttr: String? = null
                var drawableAttr: String? = null
                for (i in 0 until parser.attributeCount) {
                    val attrName = parser.getAttributeName(i)
                    val attrValue = parser.getAttributeValue(i)
                    if (attrName.equals("component", true)) componentAttr = attrValue
                    if (attrName.equals("drawable", true)) drawableAttr = attrValue
                }
                if (!componentAttr.isNullOrBlank() && !drawableAttr.isNullOrBlank()) {
                    // component looks like: ComponentInfo{com.google.android.apps.photos/com.google.android.apps.photos.home.MainActivity}
                    val pkg = componentAttr.substringAfter('{').substringBefore('/').trim()
                    if (pkg.isNotBlank()) {
                        mappings[pkg] = drawableAttr
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    } finally {
        try { parser.close() } catch (_: Exception) {}
    }
    return mappings
}

/**
 * Render drawable into an ARGB bitmap of sizePx x sizePx.
 * Handles AdaptiveIconDrawable correctly by drawing background + foreground.
 */
fun drawableToBitmap(drawable: Drawable, sizePx: Int = 128): Bitmap {
    if (drawable is BitmapDrawable) {
        val bmp = drawable.bitmap
        if (bmp.width == sizePx && bmp.height == sizePx) return bmp
        return Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
    }

    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    try {
        if (drawable is ColorDrawable) {
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            // adaptive icon: draw background then foreground to canvas (full size)
            val bg = drawable.background
            val fg = drawable.foreground
            bg?.setBounds(0, 0, canvas.width, canvas.height)
            bg?.draw(canvas)
            fg?.setBounds(0, 0, canvas.width, canvas.height)
            fg?.draw(canvas)
        } else {
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    } catch (e: Exception) {
        Log.w(TAG, "drawableToBitmap failed: ${e.message}")
        e.printStackTrace()
    }

    return bmp
}

/**
 * Tint the drawable converting to a monochrome/tinted ImageBitmap.
 * Uses PorterDuff.Mode.SRC_IN which preserves the alpha mask more reliably than SRC_ATOP.
 * If the tinted result looks like a near-solid color (e.g. white box), returns the original bitmap instead.
 *
 * @param tintColor ARGB color to apply (e.g. Color.WHITE)
 * @param sizePx output size in pixels (use 128 for safer results)
 */
fun drawableToMonochromeImageBitmap(drawable: Drawable, tintColor: Int = Color.WHITE, sizePx: Int = 128): ImageBitmap? {
    return try {
        val src = drawableToBitmap(drawable, sizePx)

        // create colored bitmap using SRC_IN so alpha mask is respected
        val colored = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(colored)

        // draw tint color full-rect
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = tintColor
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)

        // apply src alpha as mask with SRC_IN -> keeps alpha of source, replaces color
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null

        val mostlySingle = isBitmapMostlySingleColor(colored, threshold = 0.98f)
        if (mostlySingle) {
            Log.d(TAG, "Tint result mostly single color — returning original bitmap to preserve icon colors.")
            return src.asImageBitmap()
        }

        colored.asImageBitmap()
    } catch (e: Exception) {
        Log.w(TAG, "drawableToMonochromeImageBitmap failed: ${e.message}")
        e.printStackTrace()
        null
    }
}

/**
 * Heuristic: returns true if more than `threshold` fraction of non-transparent pixels have the same color.
 * This helps detect "flat white" icons produced by some icon packs when tinted.
 */
fun isBitmapMostlySingleColor(bitmap: Bitmap, threshold: Float = 0.98f): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    var countNonTransparent = 0
    val colorCounts = mutableMapOf<Int, Int>()
    for (px in pixels) {
        val alpha = px ushr 24 and 0xff
        if (alpha == 0) continue
        countNonTransparent++
        // consider only RGB for grouping (drop alpha)
        val rgb = px and 0x00FFFFFF
        colorCounts[rgb] = (colorCounts[rgb] ?: 0) + 1
    }
    if (countNonTransparent == 0) return false
    val maxCount = colorCounts.values.maxOrNull() ?: 0
    return (maxCount.toFloat() / countNonTransparent.toFloat()) >= threshold
}

/**
 * Best-effort: try to load a drawable from an installed icon-pack package.
 * Many icon packs use different naming schemes; we try a few common patterns and resource types.
 *
 * This function logs the candidate names (so you can watch Logcat and see what was tried).
 */
fun tryLoadIconFromIconPack(context: Context, iconPackPackage: String, targetPkg: String): Drawable? {
    try {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(iconPackPackage)
        Log.d(TAG, "Attempting icon pack lookup: pack=$iconPackPackage for target=$targetPkg")

        // Try appfilter mapping first (if any)
        val mappings = parseAppFilterMappings(context, iconPackPackage)
        if (mappings.isNotEmpty()) {
            val drawableName = mappings[targetPkg]
            if (!drawableName.isNullOrBlank()) {
                val id = res.getIdentifier(drawableName, "drawable", iconPackPackage).takeIf { it != 0 }
                    ?: res.getIdentifier(drawableName, "mipmap", iconPackPackage).takeIf { it != 0 }
                if (id != null && id != 0) {
                    try {
                        return res.getDrawable(id, null)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to load mapped drawable $drawableName from pack $iconPackPackage : ${t.message}")
                    }
                }
            }
        }

        // fallback: previous candidate-based scanning (drawable/mipmap)
        // ... (keep the existing candidate logic you already have)
    } catch (e: Exception) {
        Log.w(TAG, "tryLoadIconFromIconPack: exception while loading pack=$iconPackPackage -> ${e.message}")
    }
    return null
}
