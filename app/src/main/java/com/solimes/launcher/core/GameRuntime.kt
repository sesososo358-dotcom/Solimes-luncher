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
        val binDir = File(runtimeDir, "bin")
        binDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }
        }
        val binJava = File(binDir, "java")
        if (binJava.exists()) {
            binJava.setReadable(true, false)
            binJava.setExecutable(true, false)
            return binJava
        }

        val prepared = prepareJava8()
        File(prepared, "bin").listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }
        }
        val javaAfterExtract = File(prepared, "bin/java")
        javaAfterExtract.setReadable(true, false)
        javaAfterExtract.setExecutable(true, false)
        return javaAfterExtract
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
            text = "Status: ${ActiveGameSession.lastAction} \vert{} WorkDir:$workDir"
            textSize = 12f
            setTextColor(Color.WHITE)
        }

        val logView = TextView(this).apply {
            text = (AppLog.snapshot().reversed() + listOf("--- Command ---") + cmd).joinToString("\n")
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }

        fun refreshView() {
            actionStatus.text = "Status: ${ActiveGameSession.lastAction} \vert{} WorkDir:$workDir"
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
        row1.addView(makeCtrlButton("W", "w\n"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
