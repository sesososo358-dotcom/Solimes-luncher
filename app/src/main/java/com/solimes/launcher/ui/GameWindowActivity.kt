package com.solimes.launcher.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
        view.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        setContentView(view)
        hideSystemUi()
    }

    private fun handleTouch(event: MotionEvent) {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 1
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> 0
            MotionEvent.ACTION_MOVE -> 2
            else -> return
        }
        val idx = event.actionIndex.takeIf { it < event.pointerCount } ?: 0
        runCatching {
            LwjglBridge.nativeCursorPos(event.getX(idx).toDouble(), event.getY(idx).toDouble())
            if (action == 0 || action == 1) {
                LwjglBridge.nativeMouseButton(0, action, 0)
            }
        }.onFailure {
            AppLog.w(TAG, "تعذر تمرير اللمس إلى LwjglBridge: ${it.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (started.compareAndSet(false, true)) {
            runCatching {
                LwjglBridge.nativeSetSurface(holder.surface)
            }.onFailure {
                AppLog.w(TAG, "تعذر ربط Surface بـ LwjglBridge: ${it.message}")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        runCatching {
            LwjglBridge.nativeSetSurface(holder.surface)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runCatching {
            LwjglBridge.nativeSetSurface(null)
        }
    }
}
