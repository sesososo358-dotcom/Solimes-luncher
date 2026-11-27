package com.solimes.launcher.runtime

import android.content.Context
import java.io.File

class GameRuntime(private val context: Context) {

    fun prepareJava8(onLog: (String) -> Unit = {}): File {
        val runtimeBaseDir = File(context.getExternalFilesDir(null), "solimes/runtime")
        if (!runtimeBaseDir.exists()) {
            runtimeBaseDir.mkdirs()
        }

        val java8Dir = Java8Downloader.ensureJava8Installed(runtimeBaseDir) { status ->
            onLog(status)
        }

        val javaExecutable = File(java8Dir, "bin/java")
        if (!javaExecutable.exists()) {
            throw IllegalStateException("Java 8 executable not found at: ${javaExecutable.absolutePath}")
        }

        javaExecutable.setExecutable(true, false)
        return javaExecutable
    }
}
