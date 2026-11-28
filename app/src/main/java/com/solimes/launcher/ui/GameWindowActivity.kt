package com.solimes.launcher.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import com.solimes.launcher.core.AppLog
import com.solimes.launcher.core.JvmLauncher
import com.solimes.launcher.core.LwjglBridge
import com.solimes.launcher.core.RealGameRuntime
import java.io.File
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
        view.setOnTouchListener { _, e ->
            handleTouch(e)
            true
        }
        setContentView(view)
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
