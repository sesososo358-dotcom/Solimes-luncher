package com.solimes.launcher.accounts

import com.solimes.launcher.core.GamePaths
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(val name: String, val uuid: String, val type: String)

/**
 * Local (offline) profiles. An offline profile can play singleplayer, LAN and servers that run in
 * offline mode. Servers that verify accounts (online-mode) require a real Microsoft account;
 * that sign-in is not implemented here.
 */
class AccountManager(private val paths: GamePaths) {

    private val lock = Any()

    private fun load(): JSONObject {
        val f = paths.accountsFile
        return if (f.isFile) JSONObject(f.readText()) else JSONObject().put("profiles", JSONArray())
    }

    private fun save(j: JSONObject) {
        paths.accountsFile.parentFile?.mkdirs()
        paths.accountsFile.writeText(j.toString())
    }

    fun offlineUuid(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString().replace("-", "")

    fun list(): List<Profile> = synchronized(lock) {
        val arr = load().getJSONArray("profiles")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Profile(o.getString("name"), o.getString("uuid"), o.getString("type"))
        }
    }

    fun selected(): Profile? = synchronized(lock) {
        val sel = load().optString("selected")
        list().firstOrNull { it.uuid == sel } ?: list().firstOrNull()
    }

    fun createOffline(name: String): Profile = synchronized(lock) {
        require(Regex("[A-Za-z0-9_]{3,16}").matches(name)) { "Name must be 3-16 letters, digits or _" }
        val j = load()
        val arr = j.getJSONArray("profiles")
        val uuid = offlineUuid(name)
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("uuid") == uuid) throw IllegalArgumentException("Profile $name already exists")
        }
        arr.put(JSONObject().put("name", name).put("uuid", uuid).put("type", "offline"))
        j.put("selected", uuid)
        save(j)
        Profile(name, uuid, "offline")
    }

    fun select(uuid: String) = synchronized(lock) {
        val j = load()
        j.put("selected", uuid)
        save(j)
    }

    fun remove(uuid: String) = synchronized(lock) {
        val j = load()
        val old = j.getJSONArray("profiles")
        val kept = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getString("uuid") != uuid) kept.put(old.getJSONObject(i))
        j.put("profiles", kept)
        if (j.optString("selected") == uuid) j.remove("selected")
        save(j)
    }
}
