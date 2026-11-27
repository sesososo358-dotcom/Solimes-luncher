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
        AppLog.add(0, "[INPUT] $label
