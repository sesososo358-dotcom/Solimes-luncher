package com.solimes.launcher.skins

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.solimes.launcher.core.GamePaths
import com.solimes.launcher.core.OptionsFile
import com.solimes.launcher.core.Versions
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Imports local skin PNGs and applies one to an instance through a generated resource pack that
 * replaces the default Steve/Alex textures. Works for offline profiles; the skin is only visible locally.
 */
class SkinManager(private val ctx: Context, private val paths: GamePaths) {

    fun list(): List<File> = (paths.skins.listFiles { f -> f.extension == "png" } ?: emptyArray()).sortedBy { it.name }

    fun import(uri: Uri): File {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot read the selected file")
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outMimeType != "image/png") throw IllegalArgumentException("Skin must be a PNG")
        if (opts.outWidth != 64 || (opts.outHeight != 64 && opts.outHeight != 32)) {
            throw IllegalArgumentException("Skin must be 64x64 (or legacy 64x32), got ${opts.outWidth}x${opts.outHeight}")
        }
        val dest = File(paths.skins, "skin-${System.currentTimeMillis()}.png")
        dest.writeBytes(bytes)
        return dest
    }

    fun applyToInstance(skin: File, instanceDir: File, mc: String) {
        val legacy = Versions.isLegacy(mc)
        val png = skin.readBytes()
        val packName = "SolimesSkin.zip"
        val packDir = File(instanceDir, "resourcepacks").also { it.mkdirs() }
        val format = if (legacy) 1 else 34      // wrong format only shows an "incompatible" label; the pack still loads
        val mcmeta = """{"pack":{"pack_format":$format,"description":"Solimes local skin"}}"""
        val targets = if (legacy) {
            listOf("assets/minecraft/textures/entity/steve.png", "assets/minecraft/textures/entity/alex.png")
        } else {
            listOf(
                "assets/minecraft/textures/entity/player/wide/steve.png",
                "assets/minecraft/textures/entity/player/slim/alex.png"
            )
        }
        ZipOutputStream(File(packDir, packName).outputStream()).use { z ->
            z.putNextEntry(ZipEntry("pack.mcmeta")); z.write(mcmeta.toByteArray()); z.closeEntry()
            targets.forEach { t -> z.putNextEntry(ZipEntry(t)); z.write(png); z.closeEntry() }
        }
        val value = if (legacy) "[\"$packName\"]" else "[\"vanilla\",\"file/$packName\"]"
        OptionsFile.update(instanceDir, mapOf("resourcePacks" to value))
    }
}
