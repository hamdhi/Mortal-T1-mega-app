package lk.mortal.tv

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Network {
    val io = Executors.newFixedThreadPool(3)
    const val LIMIT = 8 * 1024 * 1024
    fun read(url: String, limit: Int = LIMIT): String = open(url).let { c ->
        try { c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Download too large" }; out.write(buffer, 0, n) }
            out.toString("UTF-8")
        } } finally { c.disconnect() }
    }
    fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            require(Playlist.safeUrl(current)) { "Invalid HTTP/HTTPS address" }
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 12000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "MortalTV/1.0")
            val status = c.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val next = c.getHeaderField("Location"); c.disconnect()
                require(!next.isNullOrBlank()) { "Invalid redirect" }
                val target = URL(URL(current), next).toString()
                require(!current.startsWith("https:") || target.startsWith("https:")) { "Insecure redirect blocked" }
                current = target
            } else { if (status !in 200..299) { c.disconnect(); error("Server returned HTTP $status") }; return c }
        }
        error("Too many redirects")
    }
    fun import(store: Store, name: String, value: String, kind: String): String {
        val source = name.trim().ifBlank { "My playlist" }
        val url = value.trim()
        val result = when (kind) {
            "stream" -> { require(Playlist.safeUrl(url)); ParseResult(listOf(Channel(Playlist.identity("$source||$url"), source, url, "Direct streams", source = source)), 0, false) }
            "text" -> Playlist.parse(value, source = source)
            else -> { require(Playlist.safeUrl(url)); Playlist.parse(read(url), url, source) }
        }
        store.importChannels(source, if (kind == "playlist") url else "", result.channels)
        return "Imported ${result.channels.size} channels • skipped ${result.skipped} invalid entries"
    }
}
