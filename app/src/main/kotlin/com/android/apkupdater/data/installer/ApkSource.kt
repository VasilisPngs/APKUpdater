package com.android.apkupdater.data.installer

import java.io.InputStream

internal const val COPY_BUFFER_SIZE = 64 * 1024

class ApkSource(val name: String, val size: Long, val openStream: () -> InputStream)
