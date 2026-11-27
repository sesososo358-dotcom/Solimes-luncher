package com.solimes.launcher.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.solimes.launcher.ui.GameWindowActivity
import java.io.File

class RealGameRuntime(private val context: Context) : GameRuntime {

    override val name: String = "Solimes Real Runtime (arm64-v8a)"

    companion object {
        private const val ABI = "arm64-v8a"

        const val EXTRA_JAVA_MAJOR = "solimes.javaMajor"
        const val EXTRA_JAVA_HOME = "solimes.javaHome"
        const val EXTRA_MAIN_CLASS = "solimes.mainClass"
        const val EXTRA_CLASSPATH = "solimes.classpath"
        const val EXTRA_JVM_ARGS = "solimes.jvmArgs"
        const val EXTRA_GAME_ARGS = "solimes.gameArgs"
        const val EXTRA_GAME_DIR = "solimes.gameDir"
        const val EXTRA_NATIVE_DIRS = "solimes.nativeDirs"

        private val REQUIRED_NATIVES = listOf("libgl4es.so", "liblwjgl.so")
    }

    @Volatile
    var lastUnavailableReason: String? = null
        private set

    private fun javaHome(major: Int) = File(context.filesDir, "runtime/java-$major")
    private fun appNativeDir() = File(context.applicationInfo.nativeLibraryDir)
    private fun extraNativeDir() = File(context.filesDir, "runtime/natives/$ABI")

    private fun jvmLibCandidates(home: File) = listOf(
        File(home, "lib/server/libjvm.so"),
        File(home, "lib/client/libjvm.so"),
        File(home, "lib/libjvm.so")
    )

    private fun findNative(name: String): File? =
        listOf(appNativeDir(), extraNativeDir())
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.length() > 0 }

    private fun checkForMajor(javaMajor: Int): String? {
        if (Build.SUPPORTED_ABIS.none { it == ABI }) {
            return "الجهاز لا يدعم المعمارية $ABI"
        }

        val home = javaHome(javaMajor)
        if (!home.isDirectory) {
            return "بيئة Java $javaMajor غير موجودة: ${home.absolutePath}"
        }

        val hasModules = File(home, "lib/modules").isFile
        val hasRtJar = File(home, "lib/rt.jar").isFile
        if (!hasModules && !hasRtJar) {
            return "ملفات Java $javaMajor ناقصة (lib/modules أو rt.jar غير موجود)"
        }

        if (jvmLibCandidates(home).none { it.isFile && it.length() > 0 }) {
            return "libjvm.so غير موجودة داخل ${home.absolutePath}/lib"
        }

        val missing = REQUIRED_NATIVES.filter { findNative(it) == null }
        if (missing.isNotEmpty()) {
            return "مكتبات أصلية ناقصة ($ABI): ${missing.joinToString()}"
        }

        return null
    }

    override fun isAvailable(): Boolean {
        val reason = checkForMajor(8) ?: checkForMajor(17)
        lastUnavailableReason = reason
        if (reason != null) {
            AppLog.add(1, "RealGameRuntime غير متاح: $reason")
            return false
        }
        return true
    }

    override fun launch(spec: LaunchSpec): Result<Unit> = runCatching {
        AppLog.add(0, "RealGameRuntime: بدء التجهيز لـ javaMajor=${spec.javaMajor}, mainClass=${spec.mainClass}")

        val reason = checkForMajor(spec.javaMajor)
        lastUnavailableReason = reason
        if (reason != null) {
            AppLog.add(2, "RealGameRuntime: فحص المتطلبات فشل: $reason")
            error(reason)
        }

        val home = javaHome(spec.javaMajor)
        val gameDir = spec.workDir.apply { mkdirs() }
        check(gameDir.isDirectory) { "تعذّر إنشاء مجلد اللعبة: ${gameDir.absolutePath}" }

        val missingCp = spec.classpath.filterNot { File(it).isFile }
        require(missingCp.isEmpty()) {
            "ملفات classpath مفقودة (${missingCp.size}): ${missingCp.first()}"
        }

        val nativeDirs = listOf(appNativeDir(), extraNativeDir())
            .filter { it.isDirectory }
            .map { it.absolutePath }

        val nativePathJoined = nativeDirs.joinToString(File.pathSeparator)
        val jvmArgs = buildList {
            addAll(spec.jvmArgs)
            add("-Djava.home=${home.absolutePath}")
            add("-Djava.library.path=$nativePathJoined")
            add("-Dorg.lwjgl.librarypath=$nativePathJoined")
        }

        val intent = Intent(context, GameWindowActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(context.packageName)
            putExtra(EXTRA_JAVA_MAJOR, spec.javaMajor)
            putExtra(EXTRA_JAVA_HOME, home.absolutePath)
            putExtra(EXTRA_MAIN_CLASS, spec.mainClass)
            putExtra(EXTRA_CLASSPATH, spec.classpath.toTypedArray())
            putExtra(EXTRA_JVM_ARGS, jvmArgs.toTypedArray())
            putExtra(EXTRA_GAME_ARGS, spec.gameArgs.toTypedArray())
            putExtra(EXTRA_GAME_DIR, gameDir.absolutePath)
            putExtra(EXTRA_NATIVE_DIRS, nativeDirs.toTypedArray())
        }

        AppLog.add(0, "RealGameRuntime: فتح GameWindowActivity")
        context.startActivity(intent)
    }.onFailure { e ->
        AppLog.add(2, "RealGameRuntime: فشل التشغيل: ${e.message}")
    }
}
