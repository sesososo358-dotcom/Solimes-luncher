package com.solimes.launcher.core

import java.io.File

/**
 * The piece that actually starts the JVM on Android.
 *
 * Looks for an arm64 Java runtime inside GamePaths.runtime (e.g. runtime/java8/bin/java),
 * prepares native/GL library paths, and launches the JVM process.
 */
interface GameRuntime {
    val name: String
    fun isAvailable(): Boolean
    fun launch(spec: LaunchSpec): Result<Unit>
}

class LocalJavaRuntime(
    private val paths: GamePaths,
    private val onLog: (String) -> Unit = {}
) : GameRuntime {

    override val name: String = "Local Android Java Runtime"

    private fun findJavaBin(): File? {
        val candidates = listOf(
            File(paths.runtime, "java8/bin/java"),
            File(paths.runtime, "jre8/bin/java"),
            File(paths.runtime, "bin/java")
        )
        candidates.firstOrNull { it.exists() }?.let { return it }

        val subs = paths.runtime.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (dir in subs) {
            val bin = File(dir, "bin/java")
            if (bin.exists()) return bin
        }
        return null
    }

    private fun collectSoDirs(javaBin: File): List<String> {
        val dirs = LinkedHashSet<String>()
        val javaHome = javaBin.parentFile?.parentFile
        if (javaHome != null && javaHome.exists()) {
            javaHome.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .mapNotNull { it.parentFile?.absolutePath }
                .forEach { dirs += it }
        }
        val nativesDir = File(paths.runtime, "natives")
        if (nativesDir.exists()) {
            nativesDir.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .mapNotNull { it.parentFile?.absolutePath }
                .forEach { dirs += it }
            dirs += nativesDir.absolutePath
        }
        return dirs.toList()
    }

    override fun isAvailable(): Boolean = findJavaBin() != null

    override fun launch(spec: LaunchSpec): Result<Unit> {
        val javaBin = findJavaBin()
        if (javaBin == null) {
            return Result.failure(
                IllegalStateException(
                    "Java ${spec.javaMajor} (arm64) not found in ${paths.runtime.absolutePath}. " +
                    "Place your extracted Java ${spec.javaMajor} runtime inside ${File(paths.runtime, "java8").absolutePath} " +
                    "(containing bin/java). Launch command saved to ${spec.workDir.path}/launch-command.txt"
                )
            )
        }

        return runCatching {
            if (!javaBin.canExecute()) {
                javaBin.setExecutable(true, false)
            }
            spec.workDir.mkdirs()

            val javaHome = javaBin.parentFile?.parentFile
            val soDirs = collectSoDirs(javaBin)
            val cmd = spec.commandLine(javaBin.absolutePath)

            val pb = ProcessBuilder(cmd)
            pb.directory(spec.workDir)
            pb.redirectErrorStream(true)

            val env = pb.environment()
            if (javaHome != null) {
                env["JAVA_HOME"] = javaHome.absolutePath
            }
            if (soDirs.isNotEmpty()) {
                val joined = soDirs.joinToString(":")
                env["LD_LIBRARY_PATH"] = joined
            }

            onLog("Starting ${spec.mainClass} with ${javaBin.absolutePath}...")
            val proc = pb.start()
            Thread {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> onLog(line) }
                }
            }.start()
        }
    }
}

object MissingRuntime : GameRuntime {
    override val name = "No runtime installed"
    override fun isAvailable() = false
    override fun launch(spec: LaunchSpec): Result<Unit> = Result.failure(
        IllegalStateException(
            "Game files are ready, but no Java/OpenGL runtime is installed. " +
            "Java ${spec.javaMajor} (arm64) + LWJGL + a GL layer are required. The full launch command was saved to " +
            "${spec.workDir.path}/launch-command.txt"
        )
    )
}

object RuntimeProvider {
    @Volatile
    var current: GameRuntime = MissingRuntime
}
