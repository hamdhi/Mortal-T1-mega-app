package lk.mortal.tv

import java.net.URI
import java.security.MessageDigest

data class Channel(val id: String, val name: String, val url: String, val group: String = "Ungrouped", val logo: String = "", val source: String = "", val tvgId: String = "")
data class ParseResult(val channels: List<Channel>, val skipped: Int, val isHls: Boolean)

object Playlist {
    fun safeUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun identity(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    fun parse(text: String, base: String = "", source: String = "Imported"): ParseResult {
        require(text.length <= 8 * 1024 * 1024) { "Playlist exceeds 8 MB" }
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.toList()
        if (lines.any { it.startsWith("#EXT-X-") }) {
            require(safeUrl(base)) { "Import an HLS manifest using its URL" }
            return ParseResult(listOf(Channel(identity("$source|$base"), source, base, "Live", source = source)), 0, true)
        }
        var name = ""; var attrs = emptyMap<String, String>(); var skipped = 0
        val channels = linkedMapOf<String, Channel>()
        val seenUrls = mutableSetOf<String>()
        val occurrences = mutableMapOf<String, Int>()
        val attribute = Regex("([\\w-]+)\\s*=\\s*\"([^\"]*)\"")
        for (line in lines) {
            if (line.startsWith("#EXTINF:", true)) {
                attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                var quoted = false
                val comma = line.indices.firstOrNull { i ->
                    if (line[i] == '"') quoted = !quoted
                    line[i] == ',' && !quoted
                }
                name = comma?.let { line.substring(it + 1).trim() }.orEmpty()
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val url = runCatching { if (base.isNotEmpty()) URI(base).resolve(line).toString() else line }.getOrDefault("")
                if (safeUrl(url)) {
                    if (!seenUrls.add("${attrs["tvg-id"].orEmpty()}|$url")) { name = ""; attrs = emptyMap(); continue }
                    val tvgId = attrs["tvg-id"].orEmpty()
                    val occurrence = occurrences.getOrDefault(tvgId, 0)
                    if (tvgId.isNotEmpty()) occurrences[tvgId] = occurrence + 1
                    val id = identity(if (tvgId.isNotEmpty()) "$source|$tvgId|$occurrence" else "$source||$url")
                    val title = name.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { "Channel ${channels.size + 1}" }
                    val logo = attrs["tvg-logo"].orEmpty().let { runCatching { if (base.isNotEmpty()) URI(base).resolve(it).toString() else it }.getOrDefault("") }
                    channels[id] = Channel(id, title, url, attrs["group-title"].orEmpty().ifBlank { "Ungrouped" }, logo.takeIf { safeUrl(it) }.orEmpty(), source, attrs["tvg-id"].orEmpty())
                } else skipped++
                name = ""; attrs = emptyMap()
            }
        }
        return ParseResult(channels.values.toList(), skipped, false)
    }
}
