package com.solimes.launcher.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Java8Downloader {

    private const val JAVA8_URL =
        "https://github.com/sesososo350-dotcom/Solimes-luncher/releases/download/runtime-v1/jre8-arm64-20220811-release.tar.xz"

    fun ensureJava8Installed(runtimeBaseDir: File, onProgress: (String) -> Unit = {}): File {
        val java8Dir = File(runtimeBaseDir, "java8")
        val javaBin = File(java8Dir, "bin/java")

        if (javaBin.exists()) {
            javaBin.setExecutable(true, false)
            return java8Dir
        }

        val tempArchive = File(runtimeBaseDir, "jre8-arm64.tar.xz")
        val tempExtractDir = File(runtimeBaseDir, "java8_tmp")

        if (tempExtractDir.exists()) tempExtractDir.deleteRecursively()
        tempExtractDir.mkdirs()

        onProgress("Downloading Java 8 from GitHub...")
        downloadFile(JAVA8_URL, tempArchive)

        onProgress("Extracting tar.xz archive...")
        extractTarXz(tempArchive, tempExtractDir)

        val children = tempExtractDir.listFiles()?.filter { it.name != "__MACOSX" } ?: emptyList()
        val sourceRoot = if (children.size == 1 && children[0].isDirectory && File(children[0], "bin").exists()) {
            children[0]
        } else {
            tempExtractDir
        }

        if (java8Dir.exists()) java8Dir.deleteRecursively()
        if (!sourceRoot.renameTo(java8Dir)) {
            sourceRoot.copyRecursively(java8Dir, overwrite = true)
            sourceRoot.deleteRecursively()
        }

        File(java8Dir, "bin").listFiles()?.forEach { file ->
            file.setExecutable(true, false)
        }

        tempArchive.delete()
        tempExtractDir.deleteRecursively()

        onProgress("Java 8 ready")
        return java8Dir
    }

    private fun downloadFile(urlString: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SolimesLauncher")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
            }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTarXz(archiveFile: File, destDir: File) {
        BufferedInputStream(archiveFile.inputStream()).use { fileIn ->
            XZCompressorInputStream(fileIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    while (true) {
                        val entry = tarIn.nextTarEntry ?: break
                        val outFile = File(destDir, entry.name)

                        val canonicalDest = destDir.canonicalPath
                        val canonicalOut = outFile.canonicalPath
                        if (!canonicalOut.startsWith(canonicalDest + File.separator) && canonicalOut != canonicalDest) {
                            throw SecurityException("Unsafe archive entry: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val read = tarIn.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                }
                                out.flush()
                            }
                            outFile.setLastModified(entry.modTime.time)
                        }
                    }
                }
            }
        }
    }
}
