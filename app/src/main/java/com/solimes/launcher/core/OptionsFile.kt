package com.solimes.launcher.core

import java.io.File

/** Edits Minecraft's options.txt in place (keeps every key we do not touch). */
object OptionsFile {
    fun update(instanceDir: File, changes: Map<String, String>) {
        instanceDir.mkdirs()
        val f = File(instanceDir, "options.txt")
        val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
        val remaining = LinkedHashMap(changes)
        for (i in lines.indices) {
            val key = lines[i].substringBefore(':')
            if (remaining.containsKey(key)) lines[i] = "$key:${remaining.remove(key)}"
        }
        remaining.forEach { (k, v) -> lines.add("$k:$v") }
        f.writeText(lines.joinToString("\n") + "\n")
    }
}
