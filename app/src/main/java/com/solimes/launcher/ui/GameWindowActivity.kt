package com.solimes.launcher.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.solimes.launcher.core.AppLog
import com.solimes.launcher.core.LwjglBridge
import java.util.concurrent.atomic.AtomicBoolean

class GameWindowActivity : Activity(), SurfaceHolder.Callback {

    private companion object {
        const val TAG = "GameWindow"
    }

    private val started = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = SurfaceView(this)
        view.holder.addCallback(this)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        setContentView(view)
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (started.compareAndSet(false, true)) {
            runCatching {
                LwjglBridge.nativeSetSurface(holder.surface)
            }.onFailure { err ->
                AppLog.w(TAG, "تعذر ربط Surface: ${err.message}")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        runCatching {
            LwjglBridge.nativeSetSurface(holder.surface)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AppLog.i(TAG, "تم إتلاف Surface")
    }
}
