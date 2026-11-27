package com.solimes.launcher.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class DlTask(val url: String, val dest: File, val sha1: String? = null)

object Net {
    private const val UA = "SolimesLauncher/1.0 (Android)"

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without Location: $current")
                current = URL(URL(current), loc).toString()
                c.disconnect()
            } else {
                if (code !in 200..299) {
                    c.disconnect()
                    throw IOException("HTTP $code for $current")
                }
                return c
            }
        }
        throw IOException("Too many redirects: $url")
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun sha1Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun download(task: DlTask) {
        val dest = task.dest
        if (dest.isFile && dest.length() > 0 && (task.sha1 == null || sha1Of(dest).equals(task.sha1, true))) return
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        val c = open(task.url)
        try {
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (task.sha1 != null && !sha1Of(part).equals(task.sha1, true)) {
            part.delete()
            throw IOException("SHA-1 mismatch for ${task.url}")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("Cannot write ${dest.path}")
    }

    /** Downloads in parallel (retrying each file twice). Throws the first failure. */
    fun downloadAll(tasks: List<DlTask>, threads: Int = 6, progress: (Int, Int) -> Unit) {
        if (tasks.isEmpty()) return
        val pool = Executors.newFixedThreadPool(threads)
        val done = AtomicInteger(0)
        try {
            val futures = tasks.map { t ->
                pool.submit(Callable {
                    var last: Exception? = null
                    for (attempt in 0..2) {
                        try {
                            download(t)
                            last = null
                            break
                        } catch (e: Exception) {
                            last = e
                        }
                    }
                    if (last != null) throw last
                    val d = done.incrementAndGet()
                    if (d % 25 == 0 || d == tasks.size) progress(d, tasks.size)
                    Unit
                })
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }
}
