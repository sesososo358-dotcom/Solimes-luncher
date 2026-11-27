package com.solimes.launcher.core

/**
 * The piece that actually starts the JVM on Android.
 *
 * Starting desktop Minecraft on a phone needs (1) an arm64 Android-compatible Java runtime for
 * LaunchSpec.javaMajor, (2) LWJGL/GLFW natives for Android and (3) an OpenGL translation layer
 * (e.g. gl4es / ANGLE / Zink). Those are large third-party binaries that are NOT part of this repo.
 * Implement this interface on top of them and set RuntimeProvider.current.
 */
interface GameRuntime {
    val name: String
    fun isAvailable(): Boolean
    fun launch(spec: LaunchSpec): Result<Unit>
}

object MissingRuntime : GameRuntime {
    override val name = "No runtime installed"
    override fun isAvailable() = false
    override fun launch(spec: LaunchSpec): Result<Unit> = Result.failure(
        IllegalStateException(
            "Game files are ready, but no Java/OpenGL runtime is installed. " +
            "Java ${spec.javaMajor} (arm64) + LWJGL + a GL layer are required. The full launch command was saved to " +
            "${spec.workDir.path}/launch-command.txt"
        )
    )
}

object RuntimeProvider {
    @Volatile
    var current: GameRuntime = MissingRuntime
}
