package com.solimes.launcher.nativebridge

/**
 * Kotlin side of libsolimes_native.so.
 * Library name  == add_library(solimes_native ...) in app/src/main/cpp/CMakeLists.txt
 * JNI package   == Java_com_solimes_launcher_nativebridge_NativeBridge_* in NativeBridge.cpp
 */
object NativeBridge {

    private const val LIB_NAME = "solimes_native"

    @Volatile
    private var loaded = false

    /** Loads the library once. Returns false instead of crashing if loading fails. */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary(LIB_NAME)
            nativeInit()
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    fun supportedVersions(): List<String> =
        if (!ensureLoaded()) emptyList() else nativeSupportedVersions().split(',').filter { it.isNotBlank() }

    /** JSON string describing the version, or null if the version is not supported. */
    fun versionProfileJson(versionId: String): String? =
        if (!ensureLoaded()) null else nativeVersionProfileJson(versionId)

    fun jvmArgs(versionId: String, heapMb: Int, lowEnd: Boolean): List<String> =
        if (!ensureLoaded()) emptyList()
        else nativeBuildJvmArgs(versionId, heapMb, lowEnd)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun recommendedHeapMb(versionId: String): Int =
        if (!ensureLoaded()) 0 else nativeRecommendedHeapMb(versionId)

    fun deviceInfoJson(): String =
        if (!ensureLoaded()) "{}" else nativeDeviceInfoJson()

    /** level: 0 = info, 1 = warning, 2 = error */
    fun log(level: Int, message: String) {
        if (ensureLoaded()) nativeLog(level, message)
    }

    fun drainLogs(): List<String> =
        if (!ensureLoaded()) emptyList() else nativeDrainLogs().split('\n').filter { it.isNotBlank() }

    private external fun nativeInit(): Boolean
    private external fun nativeSupportedVersions(): String
    private external fun nativeVersionProfileJson(versionId: String): String?
    private external fun nativeBuildJvmArgs(versionId: String, heapMb: Int, lowEnd: Boolean): String?
    private external fun nativeRecommendedHeapMb(versionId: String): Int
    private external fun nativeDeviceInfoJson(): String
    private external fun nativeLog(level: Int, message: String)
    private external fun nativeDrainLogs(): String
}
