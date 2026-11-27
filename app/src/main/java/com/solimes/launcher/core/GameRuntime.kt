package com.solimes.launcher.core

import android.content.Context
import android.content.Intent
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
                val rawArgs = extractCommand(spec).toMutableList()
                if (rawArgs.isEmpty()) {
                    return Result.failure(IllegalStateException("Launch command is empty"))
                }

                val appContext = currentActivityContext()
                if (appContext != null) {
                    val javaBin = GameRuntime(appContext).getJavaExecutable()
                    if (rawArgs.first() == "java" || rawArgs.first().endsWith("/java")) {
                        rawArgs[0] = javaBin.absolutePath
                    } else {
                        rawArgs.add(0, javaBin.absolutePath)
                    }

                    val workingDir = extractWorkingDir(spec) ?: appContext.filesDir
                    val process = ProcessBuilder(rawArgs)
                        .directory(workingDir)
                        .redirectErrorStream(true)
                        .start()

                    Thread {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                AppLog.add(0, "[MC] $line")
                            }
                        }
                        val exitCode = try {
                            process.waitFor()
                        } catch (_: InterruptedException) {
                            -1
                        }
                        AppLog.add(0, "[MC] Process exited with code $exitCode")
                    }.start()

                    tryOpenGameWindow(appContext, rawArgs)
                }

                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

        private fun currentActivityContext(): Context? {
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplication = activityThreadClass.getMethod("currentApplication").invoke(null)
                currentApplication as? Context
            } catch (_: Throwable) {
                null
            }
        }

        private fun extractWorkingDir(spec: Any): File? {
            return try {
                val field = spec.javaClass.declaredFields.firstOrNull {
                    it.name.contains("dir", ignoreCase = true) || it.name.contains("gameDir", ignoreCase = true)
                }
                field?.isAccessible = true
                val value = field?.get(spec)
                when (value) {
                    is File -> value
                    is String -> File(value)
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun tryOpenGameWindow(context: Context, command: List<String>) {
            val candidateClasses = listOf(
                "com.solimes.launcher.GameActivity",
                "com.solimes.launcher.MinecraftActivity",
                "com.solimes.launcher.ui.GameActivity"
            )
            for (className in candidateClasses) {
                try {
                    val clazz = Class.forName(className)
                    val intent = Intent(context, clazz).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putStringArrayListExtra("mc_command", ArrayList(command))
                    }
                    context.startActivity(intent)
                    return
                } catch (_: ClassNotFoundException) {
                    // جرّب الاسم التالي
                } catch (_: Throwable) {
                    // تجاهل إذا لم يكن متاحاً
                }
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
                        is String -> raw.lines()
                            .map { it.trim().removeSuffix("\\").trim() }
                            .filter { it.isNotEmpty() }
                        else -> emptyList()
                    }
                }
            }
        }
    }
}
