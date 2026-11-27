package com.solimes.launcher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Installs the Fabric loader profile (Legacy Fabric for 1.8.8) and downloads its libraries. */
class FabricInstaller(private val paths: GamePaths, private val say: (String) -> Unit) {

    companion object {
        const val FABRIC_META = "https://meta.fabricmc.net/v2"
        const val LEGACY_META = "https://meta.legacyfabric.net/v2"
    }

    /** @return the saved Fabric profile JSON file. */
    fun install(mc: String): File {
        val meta = if (Versions.isLegacy(mc)) LEGACY_META else FABRIC_META
        val enc = URLEncoder.encode(mc, "UTF-8")
        say("Looking up Fabric loader for $mc...")
        val loaders = JSONArray(Net.getText("$meta/versions/loader/$enc"))
        if (loaders.length() == 0) {
            throw IllegalStateException("No Fabric loader is published for Minecraft $mc at $meta")
        }
        var stable: String? = null
        for (i in 0 until loaders.length()) {
            val l = loaders.getJSONObject(i).getJSONObject("loader")
            if (l.optBoolean("stable", true)) { stable = l.getString("version"); break }
        }
        val loader = stable ?: loaders.getJSONObject(0).getJSONObject("loader").getString("version")

        val profileText = Net.getText("$meta/versions/loader/$enc/$loader/profile/json")
        val profile = JSONObject(profileText)
        val id = profile.optString("id", "fabric-loader-$loader-$mc")
        val file = File(paths.versions, "$id/$id.json")
        file.parentFile?.mkdirs()
        file.writeText(profileText)

        val tasks = fabricLibraryTasks(profile)
        say("Downloading Fabric loader $loader (${tasks.size} libraries)...")
        Net.downloadAll(tasks, 3) { d, t -> say("Fabric: $d / $t") }
        return file
    }

    fun fabricLibraryTasks(profile: JSONObject): List<DlTask> {
        val libs = profile.optJSONArray("libraries") ?: return emptyList()
        val out = ArrayList<DlTask>()
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            val name = lib.getString("name")
            val path = Libraries.mavenPath(name)
            val base = lib.optString("url", "https://maven.fabricmc.net/").trimEnd('/')
            val url = "$base/$path"
            val dest = File(paths.libraries, path)
            val sha1 = lib.optString("sha1").ifEmpty { null }
            val optional404 = name.contains("lwjgl-platform") || url.contains("lwjgl-platform")
            out += DlTask(url, dest, sha1, optional404)
        }
        return out
    }
}
