package com.solimes.launcher.core

/** The only versions this launcher supports. All of them run through Fabric. */
object Versions {
    val SUPPORTED = listOf("26.3", "26.1", "1.8.8")

    /** Fabric itself does not support 1.8.8; Legacy Fabric is the Fabric-based loader for it. */
    fun isLegacy(mc: String) = mc == "1.8.8"
}
