package lk.mortal.tv

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

data class Movie(val id: String, val title: String, val url: String, val year: Int, val minutes: Int, val category: String, val description: String, val credit: String, val license: String, val sourceUrl: String, val verified: String) {
    fun channel() = Channel("vod:$id", title, url, category, source = "Free films", kind = "vod")
}

class VodCatalog(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "vod-catalog.json"))
    @Volatile private var movies = runCatching { parse(file.openRead().bufferedReader().use { it.readText() }) }
        .getOrElse { parse(context.assets.open("vod.json").bufferedReader().use { it.readText() }) }
    fun entries(): List<Movie> = movies
    fun refresh(url: String): Int {
        require(url.startsWith("https://")) { "Use an HTTPS catalog URL" }
        val text = Network.read(url, 1024 * 1024); val next = parse(text)
        val out = file.startWrite()
        try { out.write(text.toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        movies = next; return next.size
    }
    companion object {
        const val DEFAULT_URL = "https://raw.githubusercontent.com/hamdhi/Mortal-T1-mega-app/main/catalog/vod.json"
        fun parse(text: String): List<Movie> {
            val array = JSONArray(text); require(array.length() in 1..2000) { "Catalog must contain 1–2000 films" }
            val ids = mutableSetOf<String>()
            return array.objects().map { j ->
                val id = j.getString("id"); require(id.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && ids.add(id)) { "Duplicate or invalid movie ID" }
                val title = j.getString("title"); val url = j.getString("url"); val source = j.getString("sourceUrl")
                require(title.isNotBlank() && title.length <= 200 && Playlist.safeUrl(url) && Playlist.safeUrl(source)) { "Invalid movie address or title" }
                val credit = j.getString("credit"); val license = j.getString("license")
                require(credit.isNotBlank() && license.isNotBlank()) { "Movie attribution and authorization information are required" }
                Movie(id, title, url, j.getInt("year"), j.getInt("minutes"), j.getString("category"), j.getString("description"), credit, license, source, j.getString("verified"))
            }
        }
    }
}
