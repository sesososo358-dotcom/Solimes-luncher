package com.solimes.launcher.core

import android.content.Context
import com.solimes.launcher.runtime.Java8Downloader
import java.io.File

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
            return Result.success(Unit)
        }
    }
}
