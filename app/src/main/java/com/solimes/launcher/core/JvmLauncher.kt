package com.solimes.launcher.core

import java.io.File

object JavaLayout {
    private fun first(vararg f: File) = f.firstOrNull { it.isFile && it.length() > 0 }

    fun archDir(home: File) = File(home, "lib/aarch64")

    fun libjli(home: File) = first(
        File(home, "lib/aarch64/jli/libjli.so"),
        File(home, "lib/aarch64/libjli.so"),
        File(home, "lib/libjli.so")
    )

    fun libjvm(home: File) = first(
        File(home, "lib/aarch64/server/libjvm.so"),
        File(home, "lib/aarch64/client/libjvm.so"),
        File(home, "lib/aarch64/libjvm.so")
    )

    fun libraryDirs(home: File): List<String> = listOf(
        File(home, "lib/aarch64/jli"),
        File(home, "lib/aarch64/server"),
        File(home, "lib/aarch64/client"),
        File(home, "lib/aarch64")
    ).filter { it.isDirectory }.map { it.absolutePath }
}

object JvmLauncher {
    private const val TAG = "JvmLauncher"
    private var started = false

    init { System.loadLibrary("solimes_jvm") }

    @JvmStatic
    private external fun nativeRun(
        workDir: String, logPath: String, libJli: String
