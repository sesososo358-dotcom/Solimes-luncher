package com.solimes.launcher.runtime

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
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

        if (archiveFile.exists()) {
            archiveFile.delete()
        }

        val extractedJava = findJavaBinary(runtimeDir)
            ?: throw IllegalStateException("Java binary not found after extracting archive")
        extractedJava.setExecutable(true, false)
        return extractedJava
    }

    private fun downloadFile(urlString: String, outputFile: File, onProgress: (String) -> Unit) {
        onProgress("Downloading Java 8 from GitHub...")
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 5) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SolimesLauncher")
            }

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect missing Location header (HTTP $code)")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Download failed: HTTP $code")
                }

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L
                var lastReportedPercent = -1

                outputFile.parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (percent != lastReportedPercent && percent % 10 == 0) {
                                    lastReportedPercent = percent
                                    onProgress("Downloading Java 8... $percent%")
                                }
                            }
                        }
                        output.flush()
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }

        throw IllegalStateException("Too many HTTP redirects while downloading Java 8")
    }

    private fun extractTarXz(archiveFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                XZCompressorInputStream(bis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        while (true) {
                            val entry = tarIn.nextTarEntry ?: break
                            val outFile = File(targetDir, entry.name)
                            val canonicalOut = outFile.canonicalPath
                            if (!canonicalOut.startsWith(canonicalTarget + File.separator) &&
                                canonicalOut != canonicalTarget
                            ) {
                                throw SecurityException("Blocked zip-slip entry: ${entry.name}")
                            }

                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(outFile)).use { out ->
                                    val buffer = ByteArray(16 * 1024)
                                    while (true) {
                                        val read = tarIn.read(buffer)
                                        if (read == -1) break
                                        out.write(buffer, 0, read)
                                    }
                                    out.flush()
                                }
                                if (outFile.parentFile?.name == "bin" || entry.mode and 0b001_000_000 != 0) {
                                    outFile.setExecutable(true, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findJavaBinary(root: File): File? {
        if (!root.exists()) return null
        val direct = File(root, "bin/java")
        if (direct.exists()) return direct
        return root.walkTopDown().firstOrNull { it.isFile && it.name == "java" && it.parentFile?.name == "bin" }
    }
}
