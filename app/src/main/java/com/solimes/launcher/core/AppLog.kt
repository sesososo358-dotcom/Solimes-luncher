package com.solimes.launcher.core

import com.solimes.launcher.nativebridge.NativeBridge

/** Small in-memory log shown on the Logs tab (also forwarded to the native logger / logcat). */
object AppLog {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(level: Int, msg: String) {
        if (lines.size >= 500) lines.removeFirst()
        val tag = when (level) { 2 -> "E"; 1 -> "W"; else -> "I" }
        lines.addLast("$tag $msg")
        NativeBridge.log(level, msg)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
