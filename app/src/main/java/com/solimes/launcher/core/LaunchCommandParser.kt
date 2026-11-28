package com.solimes.launcher.core

import java.io.File

/** يحوّل launch-command.txt إلى jvmArgs / classpath / mainClass / gameArgs. */
object LaunchCommandParser {

    data class Parsed(
        val jvmArgs: List<String>,
        val classpath: List<String>,
        val mainClass: String,
        val gameArgs: List<String>
    )

    fun parse(text: String): Result<Parsed> = runCatching {
        val cleaned = text.lineSequence()
            .map { it.trim().removeSuffix("\\").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString(" ")
        val tokens = tokenize(cleaned).toMutableList()
        require(tokens.isNotEmpty()) { "launch-command.txt فارغ" }

        if (tokens.first() == "java" || tokens.first().endsWith("/java")) tokens.removeAt(0)

        val jvm = ArrayList<String>()
        val game = ArrayList<String>()
        var cp: List<String> = emptyList()
        var main: String? = null
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (main != null) {
                game.add(t)
                i++
                continue
            }
            when {
                t == "-cp" || t == "-classpath" || t == "--class-path" -> {
                    require(i + 1 < tokens.size) { "-cp بدون قيمة" }
                    cp = tokens[i + 1].split(':', ';').filter { it.isNotBlank() }
                    i += 2
                }
                t == "-jar" -> error("-jar غير مدعوم، استخدم mainClass مع -cp")
                t.startsWith("-") -> {
                    jvm.add(t)
                    i++
                }
                else -> {
                    main = t
                    i++
                }
            }
        }
        val mainClass = requireNotNull(main) { "لم يتم العثور على mainClass" }
        require(cp.isNotEmpty()) { "classpath فارغ" }
        Parsed(jvm, cp, mainClass, game)
    }

    fun toSpec(file: File, javaMajor: Int, gameDir: File): Result<LaunchSpec> = runCatching {
        val p = parse(file.readText()).getOrThrow()
        LaunchSpec(
            javaMajor = javaMajor,
            jvmArgs = p.jvmArgs,
            mainClass = p.mainClass,
            gameArgs = p.gameArgs,
            classpath = p.classpath.map { File(it) },
            workDir = gameDir
        )
    }

    private fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var hasToken = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                when {
                    c == quote -> quote = null
