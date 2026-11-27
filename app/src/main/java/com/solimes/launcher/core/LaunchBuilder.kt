package com.solimes.launcher.core

import com.solimes.launcher.accounts.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LaunchSpec(
    val javaMajor: Int,
    val jvmArgs: List<String>,
    val mainClass: String,
    val gameArgs: List<String>,
    val classpath: List<File>,
    val workDir: File
) {
    fun commandLine(javaBin: String = "java"): List<String> = listOf(javaBin) + jvmArgs + mainClass + gameArgs
}

/** Merges the vanilla version JSON with the Fabric profile into one launch command. */
object LaunchBuilder {

    private val VAR = Regex("""\$\{([^}]+)\}""")

    private fun collect(arr: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val e = arr.get(i)
            if (e is String) {
                out += e
            } else if (e is JSONObject) {
                if (!Rules.allows(e.optJSONArray("rules"))) continue
                val v = e.get("value")
                if (v is String) out += v
                else if (v is JSONArray) for (k in 0 until v.length()) out += v.getString(k)
            }
        }
        return out
    }

    fun build(
        paths: GamePaths,
        meta: InstanceMeta,
        account: Profile,
        perfJvmArgs: List<String>
    ): LaunchSpec {
        val mc = meta.mc
        val dir = paths.instanceDir(mc, meta.client)
        val vanilla = JSONObject(File(paths.versions, "$mc/$mc.json").readText())
        val fabricFile = File(paths.versions, meta.fabricProfile)
        val fabric = JSONObject(fabricFile.readText())

        val cp = ArrayList<File>()
        Libraries.resolve(paths, vanilla).forEach { cp += it.dest }
        val fl = fabric.optJSONArray("libraries")
        if (fl != null) {
            for (i in 0 until fl.length()) cp += File(paths.libraries, Libraries.mavenPath(fl.getJSONObject(i).getString("name")))
        }
        cp += File(paths.versions, "$mc/$mc.jar")
        val classpath = cp.distinctBy { it.absolutePath }

        val jvm = ArrayList<String>()
        val game = ArrayList<String>()
        val va = vanilla.optJSONObject("arguments")
        if (va != null) {
            jvm += collect(va.optJSONArray("jvm"))
            game += collect(va.optJSONArray("game"))
        } else {
            game += vanilla.optString("minecraftArguments").split(' ').filter { it.isNotBlank() }
            jvm += "-Djava.library.path=\${natives_directory}"
        }
        val fa = fabric.optJSONObject("arguments")
        jvm += collect(fa?.optJSONArray("jvm"))
        game += collect(fa?.optJSONArray("game"))
        if (jvm.none { it == "-cp" || it == "-classpath" }) { jvm += "-cp"; jvm += "\${classpath}" }

        val vars = mapOf(
            "auth_player_name" to account.name,
            "auth_uuid" to account.uuid,
            "auth_access_token" to "0",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to "legacy",
            "user_properties" to "{}",
            "version_name" to mc,
            "version_type" to "release",
            "game_directory" to dir.absolutePath,
            "assets_root" to paths.assets.absolutePath,
            "game_assets" to paths.assets.absolutePath,
            "assets_index_name" to vanilla.getJSONObject("assetIndex").getString("id"),
            "natives_directory" to File(paths.runtime, "natives").absolutePath,
            "library_directory" to paths.libraries.absolutePath,
            "classpath_separator" to ":",
            "launcher_name" to "Solimes",
            "launcher_version" to "1.0",
            "classpath" to classpath.joinToString(":") { it.absolutePath }
        )
        fun sub(s: String) = VAR.replace(s) { vars[it.groupValues[1]] ?: "" }

        val javaMajor = vanilla.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8
        return LaunchSpec(
            javaMajor = javaMajor,
            jvmArgs = perfJvmArgs + jvm.map { sub(it) },
            mainClass = fabric.getString("mainClass"),
            gameArgs = game.map { sub(it) },
            classpath = classpath,
            workDir = dir
        )
    }
}
