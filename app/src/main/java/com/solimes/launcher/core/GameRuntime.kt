package com.solimes.launcher.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.solimes.launcher.nativebridge.NativeBridge
import com.solimes.launcher.runtime.Java8Downloader
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

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

object ActiveGameSession {
    @Volatile var process: Process? = null
    @Volatile var lastAction: String = "Idle"

    fun sendKey(label: String, payload: String) {
        lastAction = "Pressed: $label"
        AppLog.add(0, "[INPUT] $label")
        NativeBridge.log(0, "Input pressed: $label")
        val out: OutputStream? = process?.outputStream
        if (out != null) {
            try {
                out.write(payload.toByteArray())
                out.flush()
            } catch (t: Throwable) {
                lastAction = "Input error: ${t.message}"
                AppLog.add(2, "[INPUT ERR] ${t.message}")
            }
        } else {
            lastAction = "No running Java process (UI test mode: $label)"
        }
    }
}

class GameWindowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeBridge.ensureLoaded()
        val cmd = intent.getStringArrayListExtra("mc_command") ?: arrayListOf()
        val workDir = intent.getStringExtra("mc_work_dir") ?: filesDir.absolutePath

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1A10"))
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Minecraft Interactive Window"
            textSize = 19f
            setTextColor(Color.parseColor("#9BFF7A"))
        }

        val actionStatus = TextView(this).apply {
            text = "Status: ${ActiveGameSession.lastAction} | WorkDir: $workDir"
            textSize = 12f
            setTextColor(Color.WHITE)
        }

        val logView = TextView(this).apply {
            text = (AppLog.snapshot().reversed() + listOf("--- Command ---") + cmd).joinToString("\n")
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }

        fun refreshView() {
            actionStatus.text = "Status: ${ActiveGameSession.lastAction} | WorkDir: $workDir"
            logView.text = (AppLog.snapshot().reversed() + listOf("--- Command ---") + cmd).joinToString("\n")
        }

        fun makeCtrlButton(label: String, keyPayload: String): Button = Button(this).apply {
            text = label
            textSize = 12f
            setOnClickListener {
                ActiveGameSession.sendKey(label, keyPayload)
                refreshView()
            }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeCtrlButton("W", "w\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(makeCtrlButton("JUMP", " \n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(makeCtrlButton("ATTACK", "attack\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeCtrlButton("A", "a\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(makeCtrlButton("S", "s\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(makeCtrlButton("D", "d\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val topActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topActions.addView(makeCtrlButton("ESC", "\u001b\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topActions.addView(Button(this).apply {
            text = "Refresh"
            textSize = 12f
            setOnClickListener { refreshView() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topActions.addView(Button(this).apply {
            text = "Back"
            textSize = 12f
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(title)
        root.addView(actionStatus)
        root.addView(topActions)
        root.addView(row1)
        root.addView(row2)
        root.addView(
            ScrollView(this).apply { addView(logView) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                gravity = Gravity.FILL
            }
        )
        setContentView(root)
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
                val appContext = currentAppContext()
                    ?: return Result.failure(IllegalStateException("App context is unavailable"))

                val javaBin = GameRuntime(appContext).getJavaExecutable()
                val rawArgs: List<String>
                val workingDir: File

                if (spec is LaunchSpec) {
                    rawArgs = spec.commandLine(javaBin.absolutePath)
                    workingDir = spec.workDir
                } else {
                    val extracted = extractCommand(spec).toMutableList()
                    if (extracted.isEmpty()) {
                        return Result.failure(IllegalStateException("Launch command is empty"))
                    }
                    if (extracted.first() == "java" || extracted.first().endsWith("/java")) {
                        extracted[0] = javaBin.absolutePath
                    } else {
                        extracted.add(0, javaBin.absolutePath)
                    }
                    rawArgs = extracted
                    workingDir = extractWorkingDir(spec) ?: appContext.filesDir
                }

                if (!workingDir.exists()) {
                    workingDir.mkdirs()
                }

                val process = ProcessBuilder(rawArgs)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()

                ActiveGameSession.process = process
                ActiveGameSession.lastAction = "Java process started"

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
                    ActiveGameSession.lastAction = "Process exited ($exitCode)"
                    AppLog.add(0, "[MC] Process exited with code $exitCode")
                }.start()

                openGameWindow(appContext, rawArgs, workingDir.absolutePath)
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

        private fun currentAppContext(): Context? {
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

        private fun openGameWindow(context: Context, command: List<String>, workDir: String) {
            val intent = Intent(context, GameWindowActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putStringArrayListExtra("mc_command", ArrayList(command))
                putExtra("mc_work_dir", workDir)
            }
            context.startActivity(intent)
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
