package com.example.minimalistlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object CacheManager {
    private const val TAG = "CacheManager"
    // memory cache size in KB (tune: here ~8MB)
    private val memoryCacheSizeKb: Int by lazy {
        ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceAtLeast(4 * 1024)
    }

    private val memCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(memoryCacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private fun makeKey(iconPack: String?, pkg: String, sizePx: Int): String {
        val base = "${iconPack ?: "sys"}|$pkg|$sizePx"
        // short hash to keep filename friendly
        val md = MessageDigest.getInstance("MD5")
        md.update(base.toByteArray())
        val digest = md.digest().joinToString("") { "%02x".format(it) }
        return digest
    }

    private fun diskCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "iconcache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun diskCacheFile(context: Context, key: String): File {
        return File(diskCacheDir(context), "$key.png")
    }

    // public API: try memory -> disk -> load-and-cache
    suspend fun getBitmap(
        context: Context,
        iconPack: String?,
        pkg: String,
        sizePx: Int
    ): Bitmap? {
        val key = makeKey(iconPack, pkg, sizePx)

        // memory hit
        memCache.get(key)?.let {
            //Log.d(TAG, "memCache HIT $pkg")
            return it
        }

        // disk hit
        val diskFile = diskCacheFile(context, key)
        if (diskFile.exists()) {
            return try {
                // decode on IO
                withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(diskFile.absolutePath)?.also { bmp ->
                        memCache.put(key, bmp)
                        //Log.d(TAG, "diskCache HIT $pkg")
                        bmp
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }

        // load from resources (icon pack or system) and cache
        return try {
            val bmp = withContext(Dispatchers.IO) {
                val drawable = if (!iconPack.isNullOrEmpty()) {
                    tryLoadIconFromIconPack(context, iconPack, pkg)
                } else null
                val chosen = drawable ?: context.getAppIconDrawableSafe(pkg)
                chosen?.let { d ->
                    // create scaled bitmap using your function
                    drawableToBitmap(d, sizePx)
                }
            }
            if (bmp != null) {
                // write to memory + disk (do disk write on IO)
                memCache.put(key, bmp)
                try {
                    withContext(Dispatchers.IO) {
                        val out = FileOutputStream(diskFile)
                        // choose PNG for lossless; use JPEG for smaller size if ok
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                        out.close()
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            bmp
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    // prewarm multiple packages (concurrent)
    suspend fun prewarm(
        context: Context,
        iconPack: String?,
        pkgs: List<String>,
        sizePx: Int,
        parallelism: Int = 4
    ) {
        // simple concurrent prewarm: launch multiple concurrent loads with dispatcher IO
        withContext(Dispatchers.IO) {
            pkgs.chunked(parallelism).forEach { chunk ->
                chunk.map { pkg ->
                    // load synchronously in IO pool; getBitmap handles caching
                    try {
                        getBitmap(context, iconPack, pkg, sizePx)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    // optional: expose memory cache info for debug
    fun debugStats(): String {
        return "memCache size=${memCache.size()} / ${memCache.maxSize()}"
    }
}
