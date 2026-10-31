package lk.mortal.tv

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class WebLink(val id: String, val name: String, val url: String, val home: Boolean = false)
data class Source(val name: String, val url: String, val refreshed: Long, val kind: String = "live")

class Store(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library.json"))
    private var data = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())
    val listeners = CopyOnWriteArrayList<() -> Unit>()
    @Synchronized fun channels(): List<Channel> = data.optJSONArray("channels").objects().map { Channel(it.getString("id"), it.getString("name"), it.getString("url"), it.optString("group", "Ungrouped"), it.optString("logo"), it.optString("source"), it.optString("tvgId"), it.optString("kind", "live")) }
    @Synchronized fun links(): List<WebLink> = data.optJSONArray("links").objects().map { WebLink(it.getString("id"), it.getString("name"), it.getString("url"), it.optBoolean("home")) }
    @Synchronized fun sources(): List<Source> = data.optJSONArray("sources").objects().map { Source(it.getString("name"), it.optString("url"), it.optLong("refreshed"), it.optString("kind", "live")) }
    @Synchronized fun favorites(): Set<String> = data.optJSONArray("favorites").strings().toSet()
    @Synchronized fun history(): List<String> = data.optJSONArray("history").strings()
    @Synchronized fun position(id: String): Long = data.optJSONObject("positions")?.optLong(id) ?: 0
    @Synchronized fun savePosition(id: String, position: Long) { val p = data.optJSONObject("positions") ?: JSONObject(); p.put(id, position); data.put("positions", p); save() }
    @Synchronized fun favorite(id: String) { val f = favorites().toMutableSet(); if (!f.add(id)) f.remove(id); data.put("favorites", JSONArray(f.toList())); save() }
    @Synchronized fun watched(id: String) { data.put("history", JSONArray((listOf(id) + history()).distinct().take(100))); save() }
    @Synchronized fun importChannels(name: String, url: String, items: List<Channel>, kind: String = "live") {
        require(items.isNotEmpty()) { "No playable channels found; previous source kept" }
        require(name.isNotBlank() && name.length <= 100) { "Enter a source name (up to 100 characters)" }
        require(kind in setOf("live", "vod")) { "Invalid library destination" }
        data.put("channels", JSONArray((channels().filterNot { it.source == name } + items.map { it.copy(kind = kind) }).map { channelJson(it) }))
        data.put("sources", JSONArray((sources().filterNot { it.name == name } + Source(name, url, System.currentTimeMillis(), kind)).map { sourceJson(it) }))
        save()
    }
    @Synchronized fun deleteSource(name: String) { data.put("channels", JSONArray(channels().filterNot { it.source == name }.map { channelJson(it) })); data.put("sources", JSONArray(sources().filterNot { it.name == name }.map { sourceJson(it) })); save() }
    @Synchronized fun putLink(name: String, url: String, id: String = java.util.UUID.randomUUID().toString(), home: Boolean = false) {
        require(name.isNotBlank() && name.length <= 150 && Playlist.safeUrl(url)) { "A name and valid HTTP/HTTPS link are required" }
        val all = links().toMutableList(); val index = all.indexOfFirst { it.id == id }; val link = WebLink(id, name, url, home)
        if (index >= 0) all[index] = link else all.add(link)
        data.put("links", JSONArray(all.map { linkJson(it) })); save()
    }
    @Synchronized fun deleteLink(id: String) { data.put("links", JSONArray(links().filterNot { it.id == id }.map { linkJson(it) })); save() }
    @Synchronized fun moveLink(id: String, delta: Int) { val all = links().toMutableList(); val i = all.indexOfFirst { it.id == id }; if (i < 0) return; val j = (i + delta).coerceIn(0, all.lastIndex); java.util.Collections.swap(all, i, j); data.put("links", JSONArray(all.map { linkJson(it) })); save() }
    @Synchronized fun export(): String = data.toString(2)
    @Synchronized fun restore(text: String) {
        require(text.length <= 8 * 1024 * 1024) { "Backup exceeds 8 MB" }
        val candidate = JSONObject(text)
        require(candidate.has("channels") || candidate.has("links")) { "Not a Mortal TV backup" }
        listOf("channels", "links", "sources", "favorites", "history").forEach { require(!candidate.has(it) || candidate.get(it) is JSONArray) { "Invalid backup structure" } }
        require(!candidate.has("positions") || candidate.get("positions") is JSONObject) { "Invalid resume data" }
        listOf("favorites", "history").forEach { key -> candidate.optJSONArray(key).strings().forEach { require(it.length <= 100) } }
        candidate.optJSONObject("positions")?.let { p -> p.keys().forEach { require(p.getLong(it) >= 0) } }
        listOf("channels", "links").forEach { key -> candidate.optJSONArray(key).objects().forEach { require(Playlist.safeUrl(it.getString("url")) && it.getString("id").isNotEmpty() && it.getString("name").isNotBlank()) { "Invalid backup entry" } } }
        candidate.optJSONArray("sources").objects().forEach { require(it.getString("name").isNotBlank()); require(it.optString("url").isEmpty() || Playlist.safeUrl(it.getString("url"))) }
        data = candidate; save()
    }
    private fun save() {
        val out = file.startWrite()
        try { out.write(data.toString().toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        listeners.forEach { it() }
    }
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
        private fun channelJson(c: Channel) = JSONObject().put("id", c.id).put("name", c.name).put("url", c.url).put("group", c.group).put("logo", c.logo).put("source", c.source).put("tvgId", c.tvgId).put("kind", c.kind)
        private fun sourceJson(s: Source) = JSONObject().put("name", s.name).put("url", s.url).put("refreshed", s.refreshed).put("kind", s.kind)
        private fun linkJson(l: WebLink) = JSONObject().put("id", l.id).put("name", l.name).put("url", l.url).put("home", l.home)
    }
}
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
