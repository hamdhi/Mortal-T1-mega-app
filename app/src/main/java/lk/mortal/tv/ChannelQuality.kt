package lk.mortal.tv

/** Playlist labels, not a promise about the stream's current adaptive rendition. */
object ChannelQuality {
    const val ALL = "All qualities"
    const val UNKNOWN = "Unknown"
    private val resolution = Regex("(?i)(?<![a-z0-9])(\\d{3,4})\\s*[pi](?![a-z0-9])")
    private val dimensions = Regex("(?i)(?<!\\d)\\d{3,4}\\s*[x×]\\s*(\\d{3,4})(?!\\d)")
    private val aliases = listOf("8K" to "4320p", "4K" to "2160p", "UHD" to "2160p", "FHD" to "1080p", "FULL HD" to "1080p", "HD" to "720p", "SD" to "SD")
        .map { (word, quality) -> Regex("(?i)(?<![a-z0-9])${Regex.escape(word)}(?![a-z0-9])") to quality }
    fun label(name: String, group: String = ""): String = detect(name) ?: detect(group) ?: UNKNOWN
    private fun detect(text: String): String? {
        val heights = resolution.findAll(text).map { it.groupValues[1].toInt() }.toList() +
            dimensions.findAll(text).map { it.groupValues[1].toInt() }.toList()
        // Conflicting explicit labels are ambiguous, rather than silently choosing one.
        if (heights.isNotEmpty()) return heights.distinct().singleOrNull()?.let { "${it}p" } ?: UNKNOWN
        return aliases.firstOrNull { it.first.containsMatchIn(text) }?.second
    }
    fun matches(channel: Channel, selected: String) = selected == ALL || label(channel.name, channel.group) == selected
    fun options(channels: List<Channel>): List<String> {
        val labels = channels.map { label(it.name, it.group) } + listOf("4320p", "2160p", "1440p", "1080p", "720p", "576p", "512p", "480p", "360p", "240p", "144p")
        return listOf(ALL) + labels.filter { it.endsWith("p") }.distinct().sortedByDescending { it.dropLast(1).toInt() } +
            listOf("SD", UNKNOWN)
    }
}
