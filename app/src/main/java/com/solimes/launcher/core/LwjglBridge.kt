package com.solimes.launcher.core

import android.view.Surface

object LwjglBridge {
    init {
        System.loadLibrary("lwjgl_bridge")
    }

    // action للمفاتيح: 0 = release, 1 = press, 2 = repeat
    // action للمؤشر: 0 = release, 1 = press, 2 = move | button: 0 = يسار, 1 = يمين, 2 = وسط, -1 = بدون
    @JvmStatic external fun nativeSetSurface(surface: Surface)
    @JvmStatic external fun nativeSurfaceDestroyed()
    @JvmStatic external fun nativeSetSize(width: Int, height: Int)
    @JvmStatic external fun nativeKeyEvent(keyCode: Int, action: Int)
    @JvmStatic external fun nativePointerEvent(x: Float, y: Float, button: Int, action: Int)
    @JvmStatic external fun nativeBindLibrary(libPath: String?): Boolean
    @JvmStatic external fun nativeUnbindLibrary()
}
