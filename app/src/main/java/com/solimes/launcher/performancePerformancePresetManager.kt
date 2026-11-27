package com.solimes.launcher.performance

import com.solimes.launcher.core.OptionsFile
import com.solimes.launcher.core.Versions
import com.solimes.launcher.nativebridge.NativeBridge
import org.json.JSONObject
import java.io.File

/** Generates the lowest-cost settings for a Galaxy A04s class device (arm64, 3-4 GB RAM). */
object PerformancePresetManager {

    fun totalRamMb(): Long =
        try { JSONObject(NativeBridge.deviceInfoJson()).optLong("totalRamMb", 0) } catch (e: Exception) { 0 }

    fun heapMb(mc: String): Int {
        val h = NativeBridge.recommendedHeapMb(mc)
        return if (h > 0) h else 1024
    }

    /** JVM flags: native layer computes them (heap clamp per version); fallback if the library failed to load. */
    fun jvmArgs(mc: String): List<String> {
        val a = NativeBridge.jvmArgs(mc, heapMb(mc), true)
        return if (a.isNotEmpty()) a else listOf("-Xmx1024M", "-Xms512M", "-XX:+UseSerialGC")
    }

    fun options(mc: String, maxFps: Int = 60): Map<String, String> {
        val common = linkedMapOf(
            "renderDistance" to "4",
            "particles" to "2",             // minimal
            "maxFps" to maxFps.toString(),
            "enableVsync" to "false",
            "entityShadows" to "false",
            "mipmapLevels" to "0",
            "fancyGraphics" to "false"
        )
        return if (Versions.isLegacy(mc)) {
            common + mapOf(
                "ao" to "0",
                "renderClouds" to "false",
                "useVbo" to "true",
                "fboEnable" to "true"
            )
        } else {
            common + mapOf(
                "simulationDistance" to "5",
                "graphicsMode" to "0",       // fast (newer versions)
                "ao" to "false",
                "cloudStatus" to "\"off\"",
                "biomeBlendRadius" to "0",
                "entityDistanceScaling" to "0.5"
            )
        }
    }

    fun apply(instanceDir: File, mc: String) = OptionsFile.update(instanceDir, options(mc))
}
