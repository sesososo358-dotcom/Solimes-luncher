package com.solimes.launcher.core

import java.io.File

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

    private fun candidateDirsFor(javaMajor: Int): List<File> {
        val exact = when (javaMajor) {
            8 -> listOf(
                File(paths.runtime, "java8"),
                File(paths.runtime, "jre8"),
                File(paths.runtime, "jre8-arm64")
            )
            17 -> listOf(
                File(paths.runtime, "java17"),
                File(paths.runtime, "jre17"),
                File(paths.runtime, "jre17-arm64")
            )
            21 -> listOf(
                File(paths.runtime, "java21"),
                File(paths.runtime, "jre21"),
                File(paths.runtime, "jre21-arm64")
            )
            else -> listOf(
                File(paths.runtime, "java$javaMajor"),
                File(paths.runtime, "jre$javaMajor")
            )
        }
        val fallbackSubs = paths.runtime.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()

        return (exact + fallbackSubs + paths.runtime).distinctBy { it.absolutePath }
    }

    private fun findJavaBin(javaMajor: Int): File? {
        for (dir in candidateDirsFor(javaMajor)) {
            val direct = File(dir, "bin/java")
            if (direct.exists()) return direct

            val nested = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (sub in nested) {
                val bin = File(sub, "bin/java")
                if (bin.exists()) return bin
            }
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

    override fun isAvailable(): Boolean = findJavaBin(8) != null

    override fun launch(spec: LaunchSpec): Result<Unit> {
        val javaBin = findJavaBin(spec.javaMajor)
        if (javaBin == null) {
            val expectedDir = File(paths.runtime, "java${spec.javaMajor}").absolutePath
            return Result.failure(
                IllegalStateException(
                    "Java ${spec.javaMajor} (arm64) not found in ${paths.runtime.absolutePath}. " +
                    "Place your extracted Java ${spec.javaMajor} runtime inside $expectedDir " +
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
                env["LD_LIBRARY_PATH"] = soDirs.joinToString(":")
            }

            onLog("Starting ${spec.mainClass} (Java ${spec.javaMajor}) with ${javaBin.absolutePath}...")
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
