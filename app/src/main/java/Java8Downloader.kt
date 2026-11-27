package com.solimes.launcher.runtime

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Java8Downloader(private val context: Context) {

    companion object {
        const val JAVA8_URL =
            "https://github.com/sesososo358-dotcom/Solimes-luncher/releases/download/runtime-v1/jre8-arm64-20220811-release.tar.xz"
    }

    fun ensureJava8(onProgress: (String) -> Unit = {}): File {
        val runtimeDir = File(context.filesDir, "runtimes/jre8-arm64")
        val javaBin = findJavaBinary(runtimeDir)
        if (javaBin != null && javaBin.exists()) {
            javaBin.setExecutable(true, false)
            return javaBin
        }

        val archiveFile = File(context.cacheDir, "jre8-arm64-20220811-release.tar.xz")
        downloadFile(JAVA8_URL, archiveFile, onProgress)

        onProgress("Extracting Java 8 runtime...")
        runtimeDir.mkdirs()
        extractTarXz(archiveFile, runtimeDir)

        if
