package com.android.apkupdater.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

private const val HEAP_FRACTION = 8L
private const val MIN_CACHE_BYTES = 4L * 1024 * 1024

private val cacheBytes = (Runtime.getRuntime().maxMemory() / HEAP_FRACTION)
    .coerceIn(MIN_CACHE_BYTES, Int.MAX_VALUE.toLong())
    .toInt()

internal object AppIconCache {

    private val cache = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun peek(packageName: String): Bitmap? = cache[packageName]

    fun load(context: Context, packageName: String, sizePx: Int): Bitmap? =
        cache[packageName] ?: runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        }.getOrNull()?.also { cache.put(packageName, it) }
}
