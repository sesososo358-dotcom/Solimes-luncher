    private fun launch() {
        if (busy) { say("Another task is running..."); return }
        busy = true
        val mc = prefs.getString("version", Versions.SUPPORTED.first())!!
        val client = prefs.getBoolean("client", true)
        io.execute {
            try {
                val dir = paths.instanceDir(mc, client)
                val meta = InstanceMeta.load(dir) ?: throw IllegalStateException("Not installed yet - press Download / Install first")
                val account = accounts.selected() ?: throw IllegalStateException("Create a profile on the Accounts tab first")
                val spec = LaunchBuilder.build(paths, meta, account, PerformancePresetManager.jvmArgs(mc))
                File(dir, "launch-command.txt").writeText(spec.commandLine().joinToString(" \\\n  "))
                RuntimeProvider.current = UiTestRuntime(this)
                RuntimeProvider.current.launch(spec).getOrThrow()
                say("Launched Minecraft $mc as ${account.name}")
            } catch (t: Throwable) {
                AppLog.add(2, "Launch: ${t.message}")
                say(t.message ?: "Launch failed")
            } finally {
                busy = false
            }
        }
    }
