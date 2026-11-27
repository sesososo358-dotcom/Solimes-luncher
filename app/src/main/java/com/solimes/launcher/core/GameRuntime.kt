package com.solimes.launcher.core

import android.content.Context
import com.solimes.launcher.runtime.Java8Downloader
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GameRuntime(private val context: Context) {

    fun prepareJava8(onProgress: (String) -> Unit = {}): File {
        val downloader = Java8Downloader(context)
        return downloader.ensureJava8 { status: String ->
            onProgress(status)
        }
    }

    fun getJavaExecutable(): File {
        val runtimeDir = File(context.filesDir, "runtimes/jre8-arm64")
        val binJava = File(runtimeDir, "bin/java")
        return if (binJava.exists()) binJava else prepareJava8()
    }
}

object RuntimeProvider {
    @Volatile
    var current: RuntimeRunner = DefaultRuntimeRunner()

    interface RuntimeRunner {
        fun launch(spec: Any): Result<Unit>
    }

    class DefaultRuntimeRunner : RuntimeRunner {
        override fun launch(spec: Any): Result<Unit> {
            return try {
                val cmdList = extractCommand(spec)
                if (cmdList.isEmpty()) {
                    return Result.failure(IllegalStateException("Launch command is empty"))
                }

                val processBuilder = ProcessBuilder(cmdList).apply {
                    redirectErrorStream(true)
                }
                val process = processBuilder.start()
                Thread {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            AppLog.add(0, "[MC] $line")
                        }
                    }
                }.start()

                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

        private fun extractCommand(spec: Any): List<String> {
            return when (spec) {
                is List<*> -> spec.mapNotNull { it?.toString() }
                else -> {
                    val method = spec.javaClass.methods.firstOrNull {
                        it.name == "commandLine" && it.parameterCount == 0
                    }
                    val raw = method?.invoke(spec)
                    when (raw) {
                        is List<*> -> raw.mapNotNull { it?.toString() }
                        is Array<*> -> raw.mapNotNull { it?.toString() }
                        is String -> raw.lines().map { it.trim().removeSuffix("\\").trim() }.filter { it.isNotEmpty() }
                        else -> emptyList()
                    }
                }
            }
        }
    }
}
