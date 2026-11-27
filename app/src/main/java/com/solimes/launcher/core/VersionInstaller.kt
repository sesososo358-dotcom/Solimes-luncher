package com.solimes.launcher.core

import org.json.JSONObject
import java.io.File

/** Downloads vanilla Minecraft: version JSON, client jar, libraries and assets (from Mojang's official servers). */
class VersionInstaller(private val paths: GamePaths, private val say: (String) -> Unit) {

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val RESOURCES_URL = "https://resources.download.minecraft.net/"
    }

    fun install(mc: String): JSONObject {
        say("Fetching Mojang version manifest...")
        val versions = JSONObject(Net.getText(MANIFEST_URL)).getJSONArray("versions")
        var url: String? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.getString("id") == mc) { url = v.getString("url"); break }
        }
        if (url == null) throw IllegalStateException("Minecraft $mc was not found in Mojang's version manifest")

        val text = Net.getText(url)
        val json = JSONObject(text)
        val vdir = File(paths.versions, mc).also { it.mkdirs() }
        File(vdir, "$mc.json").writeText(text)

        val tasks = ArrayList<DlTask>()
        val client = json.getJSONObject("downloads").getJSONObject("client")
        tasks += DlTask(client.getString("url"), File(vdir, "$mc.jar"), client.optString("sha1").ifEmpty { null })
        tasks += Libraries.resolve(paths, json)

        val ai = json.getJSONObject("assetIndex")
        val indexFile = File(paths.assets, "indexes/${ai.getString("id")}.json")
        say("Downloading asset index...")
        Net.download(DlTask(ai.getString("url"), indexFile, ai.optString("sha1").ifEmpty { null }))
        val objects = JSONObject(indexFile.readText()).getJSONObject("objects")
        val keys = objects.keys()
        while (keys.hasNext()) {
            val hash = objects.getJSONObject(keys.next()).getString("hash")
            val prefix = hash.substring(0, 2)
            tasks += DlTask("$RESOURCES_URL$prefix/$hash", File(paths.assets, "objects/$prefix/$hash"), hash)
        }

        say("Downloading Minecraft $mc (${tasks.size} files)...")
        Net.downloadAll(tasks, 8) { d, t -> say("Minecraft $mc: $d / $t files") }
        return json
    }
}
