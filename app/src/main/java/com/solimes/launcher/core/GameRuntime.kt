class DefaultRuntimeRunner : RuntimeRunner {
    override fun launch(spec: Any): Result<Unit> {
        return try {
            val appContext = currentAppContext()
                ?: return Result.failure(IllegalStateException("App context is unavailable"))

            val rawArgs: List<String> = when (spec) {
                is LaunchSpec -> spec.commandLine("java")
                else -> extractCommand(spec).ifEmpty { listOf("minecraft-ui-session") }
            }

            val workingDir = extractWorkingDir(spec) ?: appContext.filesDir
            val safeDir = if (workingDir.absolutePath.startsWith("/storage/")) {
                appContext.filesDir
            } else {
                workingDir
            }
            if (!safeDir.exists()) {
                safeDir.mkdirs()
            }

            ActiveGameSession.process = null
            ActiveGameSession.lastAction = "Window Ready (UI Mode)"
            AppLog.add(0, "[LAUNCH] Opening GameWindowActivity directly.")
            NativeBridge.log(0, "Opening GameWindowActivity directly")

            openGameWindow(appContext, rawArgs, safeDir.absolutePath)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
