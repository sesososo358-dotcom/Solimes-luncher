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

/**
 * The piece that actually starts the JVM on Android.
 *
 * Starting desktop Minecraft on a phone needs (1) an arm64 Android-compatible Java runtime for
 * LaunchSpec.javaMajor, (2) LWJGL/GLFW natives for Android and (3) an OpenGL translation layer
 * (e.g. gl4es / ANGLE / Zink). Those are large third-party binaries that are NOT part of this repo.
 * Implement this interface on top of them and set RuntimeProvider.current.
 */
interface GameRuntime {
    val name: String
    fun isAvailable(): Boolean
    fun launch(spec: LaunchSpec): Result<Unit>
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

object ActiveGameSession {
    @Volatile var lastAction: String = "Idle"

    fun sendKey(label: String) {
        lastAction = "Pressed: $label"
        NativeBridge.log(0, "Input pressed: $label")
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
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Minecraft Interactive Window"
            textSize = 19f
            setTextColor(Color.GREEN)
        }

        val statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
        }

        val infoView = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }

        fun formatDrainedLogs(): String {
            return try {
                val raw: Any? = NativeBridge.drainLogs()
                when (raw) {
                    null -> ""
                    is String -> raw.trim()
                    is List<*> -> raw.mapNotNull { it?.toString() }.joinToString("\n").trim()
                    is Array<*> -> raw.mapNotNull { it?.toString() }.joinToString("\n").trim()
                    else -> raw.toString().trim()
                }
            } catch (_: Throwable) {
                ""
            }
        }

        fun refreshUi() {
            statusView.text = "Status: ${ActiveGameSession.lastAction} | WorkDir: $workDir"
            val logs = formatDrainedLogs()
            infoView.text = buildString {
                if (logs.isNotEmpty()) {
                    appendLine("--- Native Logs ---")
                    appendLine(logs)
                }
                appendLine("--- Command ---")
                append(cmd.joinToString("\n"))
            }
        }

        fun makeCtrlButton(label: String): Button {
            return Button(this).apply {
                text = label
                textSize = 12f
                setOnClickListener {
                    ActiveGameSession.sendKey(label)
                    refreshUi()
                }
            }
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(makeCtrlButton("W"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(makeCtrlButton("JUMP"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(makeCtrlButton("ATTACK"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(makeCtrlButton("A"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(makeCtrlButton("S"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(makeCtrlButton("D"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(makeCtrlButton("ESC"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@GameWindowActivity).apply {
                text = "Refresh"
                textSize = 12f
                setOnClickListener { refreshUi() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@GameWindowActivity).apply {
                text = "Back"
                textSize = 12f
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val scroll = ScrollView(this).apply {
            addView(infoView)
        }
        val scrollParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            gravity = Gravity.FILL
        }

        refreshUi()

        root.addView(title)
        root.addView(statusView)
        root.addView(topRow)
        root.addView(row1)
        root.addView(row2)
        root.addView(scroll, scrollParams)
        setContentView(root)
    }
}

class UiTestRuntime(private val context: Context) : GameRuntime {
    override val name = "UI Test Window Runtime"
    override fun isAvailable() = true

    override fun launch(spec: LaunchSpec): Result<Unit> {
        return try {
            val safeDir = if (spec.workDir.absolutePath.startsWith("/storage/")) {
                context.filesDir
            } else {
                spec.workDir
            }
            if (!safeDir.exists()) {
                safeDir.mkdirs()
            }

            val cmd = spec.commandLine("java")
            ActiveGameSession.lastAction = "Window Ready (UI Test Mode)"
            NativeBridge.log(0, "Launching GameWindowActivity directly for UI testing")

            val intent = Intent(context, GameWindowActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putStringArrayListExtra("mc_command", ArrayList(cmd))
                putExtra("mc_work_dir", safeDir.absolutePath)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

object RuntimeProvider {
    @Volatile
    var current: GameRuntime = MissingRuntime
}
