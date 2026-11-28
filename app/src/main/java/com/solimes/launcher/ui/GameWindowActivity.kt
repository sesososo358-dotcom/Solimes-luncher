package com.solimes.launcher.ui

import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.solimes.launcher.core.AppLog
import com.solimes.launcher.core.LwjglBridge

class GameWindowActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@GameWindowActivity)
            setOnTouchListener { _, event ->
                val action = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 1
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> 0
                    MotionEvent.ACTION_MOVE -> 2
                    else -> return@setOnTouchListener false
                }
                val button = if (action == 2) -1 else 0
                LwjglBridge.nativePointerEvent(event.x, event.y, button, action)
                true
            }
        }

        setContentView(surfaceView)
        AppLog.add(0, "GameWindowActivity created")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        LwjglBridge.nativeSetSurface(holder.surface)
        AppLog.add(0, "Surface attached to LwjglBridge")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        LwjglBridge.nativeSetSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LwjglBridge.nativeSurfaceDestroyed()
        AppLog.add(0, "Surface destroyed")
    }
}
