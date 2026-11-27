package com.solimes.launcher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Mojang "rules" evaluation for Linux/arm64 (what an Android runtime reports). */
object Rules {
    private fun matches(rule: JSONObject): Boolean {
        if (rule.has("features")) return false          // feature-gated (demo, quick play...) -> not applied
        val os = rule.optJSONObject("os") ?: return true
        val name = os.optString("name")
        val arch = os.optString("arch")
        if (name.isNotEmpty() && name != "linux") return false
        if (arch.isNotEmpty() && arch != "arm64" && arch != "aarch64") return false
        return true
    }

    fun allows(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (matches(r)) allowed = r.getString("action") == "allow"
        }
        return allowed
    }
}

object Libraries {
    /** Library jars (as download tasks) for a Mojang version JSON, in classpath order. */
    fun resolve(paths: GamePaths, version: JSONObject): List<DlTask> {
        val out = LinkedHashMap<String, DlTask>()
        val libs = version.optJSONArray("libraries") ?: return emptyList()
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            if (!Rules.allows(lib.optJSONArray("rules"))) continue
            val art = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val path = art.optString("path")
            val url = art.optString("url")
            if (path.isEmpty() || url.isEmpty()) continue
            out.getOrPut(path) { DlTask(url, File(paths.libraries, path), art.optString("sha1").ifEmpty { null }) }
        }
        return out.values.toList()
    }

    /** "group:artifact:version[:classifier]" -> maven relative path. */
    fun mavenPath(name: String): String {
        val p = name.split(':')
        require(p.size >= 3) { "Bad maven coordinate: $name" }
        val group = p[0].replace('.', '/')
        val file = if (p.size > 3) "${p[1]}-${p[2]}-${p[3]}.jar" else "${p[1]}-${p[2]}.jar"
        return "$group/${p[1]}/${p[2]}/$file"
    }
}
