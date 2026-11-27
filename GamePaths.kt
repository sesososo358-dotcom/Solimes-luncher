package com.solimes.launcher.core

import android.content.Context
import java.io.File

class GamePaths(ctx: Context) {
    val root = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "solimes")
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val instances = File(root, "instances")
    val runtime = File(root, "runtime")
    val skins = File(root, "skins")
    val accountsFile = File(root, "accounts.json")

    init {
        listOf(versions, libraries, assets, instances, runtime, skins).forEach { it.mkdirs() }
    }

    fun instanceDir(mc: String, client: Boolean) =
        File(instances, if (client) "solimes-$mc" else "fabric-$mc")
}
