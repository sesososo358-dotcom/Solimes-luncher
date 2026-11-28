package com.solimes.launcher.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.solimes.launcher.ui.GameWindowActivity
import java.io.File

class RealGameRuntime(private val context: Context) : GameRuntime {

    override val name: String = "Solimes Native Runtime (arm64-v8a)"

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

    private fun findNative(fileName: String): File? =
        listOf(appNativeDir(), extraNativeDir())
            .map { File(it, fileName) }
            .firstOrNull { it.isFile && it.length() > 0L }

    private fun toStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is Collection<*> -> value.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
        is Array<*> -> value.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
        else -> value.toString()
            .split(File.pathSeparatorChar, ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun checkJavaAndNatives(major: Int): String? {
        if (Build.SUPPORTED_ABIS.none { it == ABI }) {
            return "Unsupported ABI: device does not support $ABI"
        }

        val home = javaHome(major)
        if (!home.isDirectory) {
            return "Missing Java $major runtime directory: ${home.absolutePath}"
        }

        val hasCoreRuntime = File(home, "lib/modules").isFile || File(home, "lib/rt.jar").isFile
        if (!hasCoreRuntime) {
            return "Missing Java $major core archive (lib/modules or lib/rt.jar)"
        }

        if (jvmLibCandidates(home).none { it.isFile && it.length() > 0L }) {
            return "Missing libjvm.so inside ${home.absolutePath}/lib"
        }

        val missingLibs = REQUIRED_NATIVES.filter { findNative(it) == null }
        if (missingLibs.isNotEmpty()) {
            return "Missing native libraries ($ABI): ${missingLibs.joinToString()}"
        }

        return null
    }

    override fun isAvailable(): Boolean {
        val reason = checkJavaAndNatives(8) ?: checkJavaAndNatives(17)
        lastUnavailableReason = reason
        if (reason != null) {
            AppLog.add(1, "Runtime unavailable: $reason")
            return false
        }
        return true
    }

    override fun launch(spec: LaunchSpec): Result<Unit> = runCatching {
        AppLog.add(0, "Preparing launch: java=${spec.javaMajor}, mainClass=${spec.mainClass}")

        val reason = checkJavaAndNatives(spec.javaMajor)
        lastUnavailableReason = reason
        if (reason != null) {
            AppLog.add(2, "Runtime check failed: $reason")
            error(reason)
        }

        val home = javaHome(spec.javaMajor)
        val gameDir = File(spec.workDir.toString()).apply { mkdirs() }
        check(gameDir.isDirectory) { "Failed to create game directory: ${gameDir.absolutePath}" }

        val classpathEntries = toStringList(spec.classpath)
        val missingCp = classpathEntries.filterNot { File(it).isFile }
        require(missingCp.isEmpty()) {
            "Missing classpath entries (${missingCp.size}): ${missingCp.first()}"
        }

        val nativeDirs = listOf(appNativeDir(), extraNativeDir())
            .filter { it.isDirectory }
            .map { it.absolutePath }

        val nativePathValue = nativeDirs.joinToString(File.pathSeparator)
        val jvmArgs = buildList {
            addAll(toStringList(spec.jvmArgs))
            add("-Djava.home=${home.absolutePath}")
            add("-Djava.library.path=$nativePathValue")
            add("-Dorg.lwjgl.librarypath=$nativePathValue")
        }
        val gameArgs = toStringList(spec.gameArgs)

        val intent = Intent(context, GameWindowActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(context.packageName)
            putExtra(EXTRA_JAVA_MAJOR, spec.javaMajor)
            putExtra(EXTRA_JAVA_HOME, home.absolutePath)
            putExtra(EXTRA_MAIN_CLASS, spec.mainClass)
            putExtra(EXTRA_CLASSPATH, classpathEntries.toTypedArray())
            putExtra(EXTRA_JVM_ARGS, jvmArgs.toTypedArray())
            putExtra(EXTRA_GAME_ARGS, gameArgs.toTypedArray())
            putExtra(EXTRA_GAME_DIR, gameDir.absolutePath)
            putExtra(EXTRA_NATIVE_DIRS, nativeDirs.toTypedArray())
        }

        AppLog.add(0, "Starting GameWindowActivity")
        context.startActivity(intent)
    }.onFailure { err ->
        AppLog.add(2, "Launch failed: ${err.message}")
    }
}
