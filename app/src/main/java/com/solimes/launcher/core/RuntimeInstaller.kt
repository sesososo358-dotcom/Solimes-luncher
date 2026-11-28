package com.solimes.launcher.core

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipInputStream

class RuntimeInstaller(private val context: Context) {

    companion object {
        private const val TAG = "RuntimeInstaller"
        const val ASSET_JRE = "runtime/jre8-arm64.zip"
        const val ASSET_NATIVES = "runtime/natives-arm64.zip"
        const val ASSET_SHIMS = "runtime/shims.zip"
    }

    private val root get() = File(context.filesDir, "runtime")
    fun javaHome8() = File(root, "java-8")
    fun nativesDir() = File(root, "natives/arm64-v8a")
    fun shimsDir() = File(root, "shims")

    fun ensureInstalled(): Result<Unit> = runCatching {
        val stamp = installStamp()
        installZip(ASSET_JRE, javaHome8(), stamp, makeExecutables = true)
        installZip(ASSET_NATIVES, nativesDir(), stamp, makeExecutables = false)
        installZip(ASSET_SHIMS, shimsDir(), stamp, makeExecutables = false)
    }.onFailure { err -> AppLog.e(TAG, "فشل تجهيز الـ Runtime: ${err.message}", err) }

    @Suppress("DEPRECATION")
    private fun installStamp(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()

    private fun installZip(
        asset: String,
        dest: File,
        stamp: String,
        makeExecutables: Boolean
    ) {
        val marker = File(dest, ".installed")
        if (marker.isFile && marker.readText() == stamp) {
            AppLog.i(TAG, "$asset محدّث، تخطي")
            return
        }
        AppLog.i(TAG, "فك $asset إلى ${dest.absolutePath}")
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val tmpPrefix = tmp.canonicalPath + File.separator
        try {
            context.assets.open(asset).use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val out = File(tmp, entry.name)
                        require(out.canonicalPath.startsWith(tmpPrefix)) { "مسار zip غير آمن: ${entry.name}" }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                            if (makeExecutables) {
                                out.setExecutable(true, false)
                            }
                        }
                        zin.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }
        } catch (ex: FileNotFoundException) {
            tmp.deleteRecursively()
            throw IllegalStateException("الملف $asset غير موجود داخل الـ APK", ex)
        }
        File(tmp, ".installed").writeText(stamp)
        dest.deleteRecursively()
        check(tmp.renameTo(dest)) { "فشل نقل ${tmp.absolutePath} إلى ${dest.absolutePath}" }
    }
}
