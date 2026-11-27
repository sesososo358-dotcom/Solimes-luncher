package com.solimes.launcher.core

import org.json.JSONArray
import java.io.File
import java.net.URLEncoder

/** Downloads Fabric API (from Modrinth) and the Solimes Client jar (from YOUR GitHub release). */
object ModDownloader {

    fun fabricApi(mc: String, modsDir: File, say: (String) -> Unit) {
        val slug = if (Versions.isLegacy(mc)) "legacy-fabric-api" else "fabric-api"
        val gv = URLEncoder.encode("[\"$mc\"]", "UTF-8")
        val ld = URLEncoder.encode("[\"fabric\"]", "UTF-8")
        say("Looking up $slug for $mc...")

        var versions = JSONArray(
            Net.getText("https://api.modrinth.com/v2/project/$slug/version?game_versions=$gv&loaders=$ld")
        )

        if (versions.length() == 0 && Versions.isLegacy(mc)) {
            versions = JSONArray(
                Net.getText("https://api.modrinth.com/v2/project/$slug/version?game_versions=$gv")
            )
        }

        if (versions.length() == 0) {
            say("Warning: $slug has no release for Minecraft $mc on Modrinth; skipping.")
            return
        }

        val files = versions.getJSONObject(0).getJSONArray("files")
        var chosen = files.getJSONObject(0)
        for (i in 0 until files.length()) {
            if (files.getJSONObject(i).optBoolean("primary")) {
                chosen = files.getJSONObject(i)
                break
            }
        }
        val name = chosen.getString("filename")
        modsDir.mkdirs()
        modsDir.listFiles { f -> f.name.startsWith(slug) && f.name.endsWith(".jar") && f.name != name }
            ?.forEach { it.delete() }
        val sha1 = chosen.optJSONObject("hashes")?.optString("sha1")?.ifEmpty { null }
        say("Downloading $name...")
        Net.download(DlTask(chosen.getString("url"), File(modsDir, name), sha1))
    }

    /** Expects a release asset named solimes-client-<mc>.jar in <repo>'s latest GitHub release. */
    fun solimesClient(mc: String, repo: String, modsDir: File, say: (String) -> Unit) {
        if (!Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo) || repo.startsWith("YOUR_")) {
            throw IllegalStateException("Enter your GitHub repo as user/repo on the Play tab (it must contain a release with solimes-client-$mc.jar)")
        }
        modsDir.mkdirs()
        say("Downloading Solimes Client for $mc...")
        Net.download(DlTask("https://github.com/$repo/releases/latest/download/solimes-client-$mc.jar", File(modsDir, "solimes-client.jar")))
    }
}
