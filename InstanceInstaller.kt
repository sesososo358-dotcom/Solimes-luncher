package com.solimes.launcher.core

import com.solimes.launcher.performance.PerformancePresetManager
import com.solimes.launcher.skins.SkinManager
import org.json.JSONObject
import java.io.File
import java.util.Properties

data class InstallRequest(
    val mc: String,
    val client: Boolean,
    val allowModMenu: Boolean,
    val windowTitle: String,
    val releaseRepo: String,
    val applyPerformancePreset: Boolean,
    val skin: File?
)

/** What was installed for an instance (read again at launch time). */
data class InstanceMeta(val mc: String, val client: Boolean, val fabricProfile: String) {
    fun save(dir: File) {
        dir.mkdirs()
        File(dir, "solimes-instance.json").writeText(
            JSONObject().put("mc", mc).put("client", client).put("fabricProfile", fabricProfile).toString()
        )
    }

    companion object {
        fun load(dir: File): InstanceMeta? {
            val f = File(dir, "solimes-instance.json")
            if (!f.isFile) return null
            val j = JSONObject(f.readText())
            return InstanceMeta(j.getString("mc"), j.getBoolean("client"), j.getString("fabricProfile"))
        }
    }
}

class InstanceInstaller(
    private val paths: GamePaths,
    private val skins: SkinManager,
    private val say: (String) -> Unit
) {
    fun install(req: InstallRequest) {
        val dir = paths.instanceDir(req.mc, req.client)
        dir.mkdirs()

        VersionInstaller(paths, say).install(req.mc)
        val profile = FabricInstaller(paths, say).install(req.mc)

        val mods = File(dir, "mods")
        ModDownloader.fabricApi(req.mc, mods, say)
        if (req.client) ModDownloader.solimesClient(req.mc, req.releaseRepo, mods, say)
        else File(mods, "solimes-client.jar").delete()

        // Settings the Solimes Client mod reads (same keys as client-mod ConfigStore).
        val cfg = File(dir, "config/solimes-client.properties")
        cfg.parentFile?.mkdirs()
        val props = Properties()
        if (cfg.isFile) cfg.inputStream().use { props.load(it) }
        props.setProperty("allowModMenu", req.allowModMenu.toString())
        props.setProperty("windowTitle", req.windowTitle)
        cfg.outputStream().use { props.store(it, "Written by Solimes Launcher") }

        if (req.applyPerformancePreset) PerformancePresetManager.apply(dir, req.mc)
        if (req.skin != null) skins.applyToInstance(req.skin, dir, req.mc)

        InstanceMeta(req.mc, req.client, profile.relativeTo(paths.versions).path).save(dir)
        say("Installed ${if (req.client) "Solimes Client" else "Fabric"} for Minecraft ${req.mc}.")
    }
}
