package com.android.gupdater.data.installer

import java.io.File
import java.io.InputStream

class ApkSource(val name: String, val size: Long, private val open: () -> InputStream) {

    fun openStream(): InputStream = open()

    companion object {
        fun of(file: File): ApkSource = ApkSource(file.name, file.length()) { file.inputStream() }
    }
}
