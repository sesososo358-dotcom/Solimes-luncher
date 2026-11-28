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
        workDir: String, logPath: String, libJli: String,
        preload: Array<String>, env: Array<String>, argv: Array<String>
    ): Int

    @Synchronized
    fun run(
        javaHome: File,
        workDir: File,
        logFile: File,
        jvmArgs: List<String>,
        classpath: List<String>,
        mainClass: String,
        gameArgs: List<String>,
        nativeDirs: List<String>,
        extraEnv: Map<String, String> = emptyMap()
    ): Int {
        check(!started) { "JVM تعمل بالفعل في هذه العملية" }
        val libJli = requireNotNull(JavaLayout.libjli(javaHome)) { "libjli.so غير موجود" }
        val libJvm = requireNotNull(JavaLayout.libjvm(javaHome)) { "libjvm.so غير موجود" }

        val arch = JavaLayout.archDir(javaHome)
        val preloadList = ArrayList<String>().apply {
            add(libJvm.absolutePath)
            listOf("libverify.so", "libjava.so", "libzip.so", "libnet.so", "libnio.so")
                .map { File(arch, it) }
                .filter { it.isFile }
                .forEach { add(it.absolutePath) }
        }

        val ldPath = (JavaLayout.libraryDirs(javaHome) + nativeDirs).joinToString(":")
        val envList = ArrayList<String>().apply {
            add("JAVA_HOME=${javaHome.absolutePath}")
            add("LD_LIBRARY_PATH=$ldPath")
            add("HOME=${workDir.absolutePath}")
            add("LIBGL_ES=2")
            extraEnv.forEach { (k, v) -> add("$k=$v") }
        }

        val flags = jvmArgs.toMutableList()
        if (flags.none { it.startsWith("-Xmx") }) flags.add("-Xmx1024M")
        if (flags.none { it.contains("UseSerialGC") || it.contains("UseG1GC") }) flags.add("-XX:+UseSerialGC")
        flags.add("-Djava.awt.headless=true")

        val argvList = ArrayList<String>().apply {
            add(File(javaHome, "bin/java").absolutePath)
            addAll(flags)
            add("-cp")
            add(classpath.joinToString(":"))
            add(mainClass)
            addAll(gameArgs)
        }

        started = true
        logFile.parentFile?.mkdirs()
        AppLog.i(TAG, "تشغيل JVM: flags=${flags.size}, classpath=${classpath.size}, main=$mainClass")
        return nativeRun(
            workDir.absolutePath,
            logFile.absolutePath,
            libJli.absolutePath,
            preloadList.toTypedArray(),
            envList.toTypedArray(),
            argvList.toTypedArray()
        )
    }
}
