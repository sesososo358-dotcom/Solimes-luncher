package com.solimes.launcher.core

import java.io.File

/** تخطيط OpenJDK 8 على arm64 (نبحث في أكثر من موضع لأن حزم JRE تختلف). */
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

/** يشغّل JVM داخل العملية الحالية عبر JLI_Launch. لا يمكن تشغيله إلا مرة واحدة لكل عملية. */
object JvmLauncher {
    private const val TAG = "JvmLauncher"
    private var started = false

    init { System.loadLibrary("solimes_jvm") }

    @JvmStatic
    private external fun nativeRun(
        workDir: String, logPath: String, libJli: String,
        preload: Array<String>, env: Array<String>, argv: Array<String>
    ): Int

    /** يحجب الخيط حتى تنتهي اللعبة، فشغّله من خيط خلفي فقط. يرجع رمز الخروج. */
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
        check(!started) { "JVM تعمل بالفعل في هذه العملية؛ أعد تشغيل التطبيق" }
        val libJli = requireNotNull(JavaLayout.libjli(javaHome)) { "libjli.so غير موجود" }
        val libJvm = requireNotNull(JavaLayout.libjvm(javaHome)) { "libjvm.so غير موجود" }

        val arch = JavaLayout.archDir(javaHome)
        // الترتيب مهم: libjvm أولاً لأن libjava تعتمد عليها
        val preload = buildList {
            add(libJvm.absolutePath)
            listOf("libverify.so", "libjava.so", "libzip.so", "libnet.so", "libnio.so")
                .map { File(arch, it) }
                .filter { it.isFile }
                .forEach { add(it.absolutePath) }
        }.toTypedArray()

        val ldPath = (JavaLayout.libraryDirs(javaHome) + nativeDirs).joinToString(":")
        val env = buildList {
            add("JAVA_HOME=${javaHome.absolutePath}")
            add("LD_LIBRARY_PATH=$ldPath")
            add("HOME=${workDir.absolutePath}")
            add("LIBGL_ES=2")   // gl4es: استهداف GLES 2.0، عدّله حسب الحاجة
            extraEnv.forEach { (k, v) -> add("$k=$v") }
        }.toTypedArray()

        val flags = jvmArgs.toMutableList()
        if (flags.none { it.startsWith("-Xmx") }) flags.add("-Xmx1024M")
        if (flags.none { it.contains("UseSerialGC") || it.contains("UseG1GC") }) flags.add("-XX:+UseSerialGC")
        flags.add("-Djava.awt.headless=true")

        val argv = buildList {
            add(File(javaHome, "bin/java").absolutePath) // JLI يستنتج مسار JRE من libjli نفسها
            addAll(flags)
            add("-cp"); add(classpath.joinToString(":"))
            add(mainClass)
            addAll(gameArgs)
        }.toTypedArray()

        started = true
        logFile.parentFile?.mkdirs()
        AppLog.i(TAG, "تشغيل JVM: flags=${flags.size}, classpath=${classpath.size}, main=$mainClass")
        AppLog.i(TAG, "سجل اللعبة: ${logFile.absolutePath}")
        return nativeRun(workDir.absolutePath, logFile.absolutePath, libJli.absolutePath, preload, env, argv)
    }
}
