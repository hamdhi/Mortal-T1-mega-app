package lk.mortal.tv

import android.content.Context
import android.util.AtomicFile
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

class Epg(private val context: Context) {
    data class Programme(val channel: String, val start: Long, val stop: Long, val title: String)
    @Volatile private var programs: List<Programme> = runCatching {
        JSONArray(AtomicFile(File(context.filesDir, "epg.json")).openRead().bufferedReader().use { it.readText() }).objects().map { Programme(it.getString("channel"), it.getLong("start"), it.getLong("stop"), it.getString("title")) }
    }.getOrDefault(emptyList())
    @Volatile private var byChannel = programs.groupBy { it.channel }
    fun nowNext(id: String): String {
        if (id.isBlank()) return ""
        val now = System.currentTimeMillis(); val matches = byChannel[id].orEmpty()
        val current = matches.firstOrNull { it.start <= now && it.stop > now }
        val next = matches.filter { it.start > now }.minByOrNull { it.start }
        return listOfNotNull(current?.let { "Now: ${it.title}" }, next?.let { "Next: ${it.title}" }).joinToString(" · ")
    }
    fun refresh(url: String): Int {
        val text = Network.read(url)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "XML declarations with entities are not supported" }
        val parser = Xml.newPullParser(); parser.setInput(StringReader(text))
        val items = mutableListOf<Programme>(); var channel = ""; var start = 0L; var stop = 0L; var title = ""; var event = parser.eventType
        val now = System.currentTimeMillis()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (parser.name) {
                "programme" -> { channel = parser.getAttributeValue(null, "channel").orEmpty(); start = date(parser.getAttributeValue(null, "start").orEmpty()); stop = date(parser.getAttributeValue(null, "stop").orEmpty()); title = "" }
                "title" -> if (title.isEmpty()) title = parser.nextText().take(250)
            }
            if (event == XmlPullParser.END_TAG && parser.name == "programme" && channel.isNotEmpty() && title.isNotEmpty() && stop > now && start < now + 48 * 3600000L && start < stop) items.add(Programme(channel, start, stop, title))
            require(items.size <= 30000) { "Guide too large" }; event = parser.next()
        }
        require(items.isNotEmpty()) { "No current/upcoming programmes found; previous guide kept" }
        val array = JSONArray(items.map { JSONObject().put("channel", it.channel).put("start", it.start).put("stop", it.stop).put("title", it.title) })
        val file = AtomicFile(File(context.filesDir, "epg.json")); val out = file.startWrite()
        try { out.write(array.toString().toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        programs = items; byChannel = items.groupBy { it.channel }; return items.size
    }
    private fun date(value: String): Long = runCatching {
        val normalized = value.trim(); require(normalized.matches(Regex("\\d{14} [+-]\\d{4}")))
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { isLenient = false }.parse(normalized)!!.time
    }.getOrDefault(0)
}
