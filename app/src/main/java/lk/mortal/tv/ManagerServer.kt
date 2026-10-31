package lk.mortal.tv

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class ManagerServer(private val context: Context, private val store: Store) : NanoHTTPD(8765) {
    val pairingCode: String = (100000 + SecureRandom().nextInt(900000)).toString()
    private val sessions = ConcurrentHashMap<String, Long>()
    private val attempts = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val started = System.currentTimeMillis()
    fun address(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress?.let { "http://$it:8765" }
    }.getOrNull() ?: "No LAN address — connect TV to Wi-Fi/Ethernet"
    override fun serve(s: IHTTPSession): Response {
        fun reply(status: Response.Status, body: String, mime: String = "application/json"): Response = newFixedLengthResponse(status, mime, body).apply {
            closeConnection(true)
            addHeader("Cache-Control", "no-store"); addHeader("X-Content-Type-Options", "nosniff"); addHeader("X-Frame-Options", "DENY")
            addHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'")
        }
        fun error(status: Response.Status, message: String) = reply(status, JSONObject().put("error", message).toString())
        try {
            if (s.uri == "/" && s.method == Method.GET) return reply(Response.Status.OK, context.assets.open("manager.html").bufferedReader().use { it.readText() }, "text/html; charset=utf-8")
            if (s.headers["transfer-encoding"] != null) return error(Response.Status.BAD_REQUEST, "Chunked uploads are not supported")
            val length = s.headers["content-length"]?.toLongOrNull() ?: 0
            if (length < 0 || length > Network.LIMIT) return error(Response.Status.BAD_REQUEST, "Maximum request size is 8 MB")
            val origin = s.headers["origin"]
            if (origin != null && origin != "http://${s.headers["host"]}") return error(Response.Status.FORBIDDEN, "Cross-origin request blocked")
            if (s.method == Method.POST && s.headers["content-type"]?.startsWith("application/json") != true) return error(Response.Status.BAD_REQUEST, "JSON required")
            fun body(): JSONObject {
                val bytes = ByteArray(length.toInt()); var n = 0
                while (n < bytes.size) { val count = s.inputStream.read(bytes, n, bytes.size - n); require(count > 0); n += count }
                return JSONObject(String(bytes, Charsets.UTF_8))
            }
            if (s.uri == "/pair" && s.method == Method.POST) {
                val now = System.currentTimeMillis(); val ip = s.remoteIpAddress ?: "unknown"
                val old = attempts[ip]; val attempt = if (old == null || now - old.first > 60000) now to 0 else old
                if (attempt.second >= 5) return error(Response.Status.FORBIDDEN, "Wait one minute before pairing again")
                attempts[ip] = attempt.first to attempt.second + 1
                if (now - started > 15 * 60000 || body().optString("code") != pairingCode) return error(Response.Status.FORBIDDEN, "Invalid or expired code. Restart PC management on TV.")
                val token = UUID.randomUUID().toString() + UUID.randomUUID(); sessions[token] = now + 60 * 60000
                return reply(Response.Status.OK, JSONObject().put("token", token).toString())
            }
            val token = s.headers["authorization"]?.removePrefix("Bearer ")
            if (token == null || (sessions[token] ?: 0) < System.currentTimeMillis()) return error(Response.Status.UNAUTHORIZED, "Pair with the TV first")
            if (s.method == Method.GET && s.uri == "/library") return reply(Response.Status.OK, store.export())
            if (s.method != Method.POST) return error(Response.Status.NOT_FOUND, "Not found")
            val b = body()
            val message = when (s.uri) {
                "/import" -> Network.import(store, b.getString("name"), b.getString("value"), b.getString("kind"), b.optString("target", "live"))
                "/link" -> { store.putLink(b.getString("name"), b.getString("url"), b.optString("id").ifBlank { UUID.randomUUID().toString() }, b.optBoolean("home")); "Link saved" }
                "/delete-link" -> { store.deleteLink(b.getString("id")); "Link removed" }
                "/move-link" -> { store.moveLink(b.getString("id"), b.getInt("delta").coerceIn(-1, 1)); "Link moved" }
                "/delete-source" -> { store.deleteSource(b.getString("name")); "Source removed" }
                "/refresh" -> { val source = store.sources().first { it.name == b.getString("name") }; require(source.url.isNotBlank()) { "Re-import pasted playlists to refresh" }; Network.import(store, source.name, source.url, "playlist", source.kind) }
                "/restore" -> { store.restore(b.getString("backup")); "Backup restored" }
                else -> return error(Response.Status.NOT_FOUND, "Not found")
            }
            return reply(Response.Status.OK, JSONObject().put("message", message).toString())
        } catch (_: Exception) { return error(Response.Status.BAD_REQUEST, "Request failed. Check the input, source availability, and file size. Your previous library is retained when an import fails.") }
    }
}
