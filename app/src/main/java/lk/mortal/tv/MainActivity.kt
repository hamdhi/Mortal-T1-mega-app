package lk.mortal.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var body: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var heading: TextView
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private var page = "Home"
    private var query = ""
    private var group = "All groups"
    private var sourceFilter = "All sources"
    private var server: ManagerServer? = null
    private val listener: () -> Unit = { runOnUiThread { if (::grid.isInitialized) render() } }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var savedState: android.os.Parcelable? = null
    private var focusId = ""
    private var refreshing = false
    private data class Card(val id: String, val title: String, val subtitle: String, val badge: String, val logo: String = "", val action: () -> Unit)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        store = Store.get(this); store.listeners.add(listener)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.bg); setPadding(d(24), d(24), d(24), d(20)) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, d(22), 0) }
        nav.addView(Ui.label(this, "M / MORTAL", 22f, Ui.accent)); nav.addView(Ui.label(this, "YOUR TV, SIMPLIFIED", 10f, Ui.muted))
        listOf("Home", "Channels", "Sri Lankan TV", "Favorites", "Recent", "Others", "Settings").forEach { name ->
            nav.addView(Ui.button(this, name) { page = name; query = ""; group = "All groups"; sourceFilter = "All sources"; savedState = null; focusId = ""; search.setText(""); render() }, LinearLayout.LayoutParams(d(168), d(46)).apply { topMargin = d(7) })
        }
        nav.addView(Ui.label(this, "LOCAL. PERSONAL. FAST.\nv${BuildConfig.VERSION_NAME}", 10f, Ui.muted))
        root.addView(nav, LinearLayout.LayoutParams(d(194), -1))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading = Ui.label(this, "", 32f)
        subtitle = Ui.label(this, "", 13f, Ui.muted)
        body.addView(heading); body.addView(subtitle)
        search = EditText(this).apply { hint = "Search your library"; setTextColor(Ui.text); setHintTextColor(Ui.muted); isSingleLine = true; textSize = 15f; background = Ui.background(Ui.panel); setPadding(d(14), d(7), d(14), d(7)) }
        search.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); renderCards() }; override fun afterTextChanged(s: Editable?) {} })
        body.addView(search, LinearLayout.LayoutParams(-1, d(46)).apply { topMargin = d(12); bottomMargin = d(12) })
        val actions = LinearLayout(this)
        actions.addView(Ui.button(this, "+ Add") { addDialog() })
        actions.addView(Ui.button(this, "Groups") { selectGroup() })
        actions.addView(Ui.button(this, "Sources") { sourceDialog() })
        actions.addView(Ui.button(this, "Refresh") { refreshSources() })
        body.addView(actions)
        grid = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@MainActivity, 3); clipToPadding = false; setPadding(0, d(16), 0, d(20)); itemAnimator = null }
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(body, LinearLayout.LayoutParams(0, -1, 1f)); setContentView(root)
        render()
        if (state != null) { page = state.getString("page", "Home"); render() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if (::grid.isInitialized) { render(); savedState?.let { grid.layoutManager?.onRestoreInstanceState(it) } } }
    private fun d(n: Int) = Ui.dp(this, n)
    private fun render() {
        heading.text = when (page) { "Home" -> "Make yourself at home."; "Channels" -> "Your channels"; "Recent" -> "Pick up where you left off"; "Settings" -> "Settle in. Set it up."; else -> page }
        subtitle.text = when (page) {
            "Home" -> "${store.channels().size} channels   /   ${store.sources().size} sources   /   One remote"
            "Settings" -> "Manage your TV, connect a computer, and keep everything up to date."
            "Sri Lankan TV" -> "Official broadcaster pages + your imported Sri Lankan channels. Availability varies."
            "Others" -> "Your web, one click away. Select a card to open it. Menu opens link actions."
            else -> "Select a channel for playback options. D-pad to browse · Back to return."
        }
        search.visibility = if (page == "Settings") View.GONE else View.VISIBLE
        renderCards()
    }
    private fun cards(): List<Card> {
        if (page == "Settings") return listOf(
            Card("pc", "Connect your PC", server?.address() ?: "Add links without typing on a TV", "PC") { pcDialog() },
            Card("updates", "App updates", "Installed v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}", "↑") { updateDialog() },
            Card("install", "Install downloaded update", "Continue after allowing installs from this source", "↑") { val apk = cacheDir.resolve("updates/mortal-tv.apk"); if (apk.exists()) Updater(this).install(apk) else toast("Download an update first") },
            Card("backup", "Export backup", "Save your library to a USB drive or document provider", "↓") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "mortal-tv-backup.json"), 102) },
            Card("restore", "Restore backup", "Replace library from a saved JSON file", "↺") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), 103) },
            Card("file", "Import playlist file", "Choose an M3U from USB or device storage", "+") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 101) },
            Card("external", "Default player", if (prefs.getBoolean("external", false)) "External player" else "Built-in Media3 player", "▶") { prefs.edit().putBoolean("external", !prefs.getBoolean("external", false)).apply(); render() },
            Card("catalog", "Sri Lankan catalog", "Refresh a versioned catalog URL", "LK") { catalogDialog() },
            Card("cache", "Clear image cache", "Keep playlists, favorites and links", "×") { Network.io.execute { Logos.clearMemory(); cacheDir.resolve("logos").deleteRecursively(); runOnUiThread { toast("Image cache cleared") } } },
            Card("about", "About Mortal TV", "Native Kotlin · Built for a remote", "M") { message("Mortal TV", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nNo accounts or subscription required.\n\nHTTP streams and the PC manager use unencrypted connections. Use trusted sources and a trusted LAN.\n\nCodec, stream availability, and launcher support depend on your device. Live streams without a DVR window cannot seek.") }
        )
        val all = store.channels()
        val selected = when (page) {
            "Favorites" -> all.filter { it.id in store.favorites() }
            "Recent" -> store.history().mapNotNull { id -> all.find { it.id == id } }
            "Home" -> (store.history().take(6).mapNotNull { id -> all.find { it.id == id } } + all.filter { it.id in store.favorites() }).distinctBy { it.id }.take(12)
            "Sri Lankan TV" -> all.filter { it.source == "Sri Lankan TV" || it.group.contains("Sri Lanka", true) }
            "Others" -> emptyList()
            else -> all
        }.filter { (sourceFilter == "All sources" || it.source == sourceFilter) && (group == "All groups" || group in it.group.split(';')) && (it.name.contains(query, true) || it.group.contains(query, true)) }.let { if (page == "Channels") it.sortedBy { c -> c.name.lowercase(Locale.ROOT) } else it }
        val results = selected.map { c -> Card(c.id, c.name, c.group + " · " + c.source, if (c.id in store.favorites()) "★" else "LIVE", c.logo) { channelDialog(c) } }.toMutableList()
        if (page == "Others" || page == "Home") results += store.links().filter { (page == "Others" || it.home) && it.name.contains(query, true) }.map { l -> Card(l.id, l.name, "Web shortcut" + if (l.home) " · Home" else "", "↗") { openLink(l.url, false) } }
        if (page == "Sri Lankan TV") results += listOf(
            Card("itn", "ITN", "Official live page · opens browser", "LK") { openLink("https://itn.lk/live/", false) },
            Card("vasantham", "Vasantham TV", "Official live page · opens browser", "LK") { openLink("https://vasantham.lk/", false) },
            Card("rupavahini", "Rupavahini", "Official live page · opens browser", "LK") { openLink("https://tv.rupavahini.lk/live-webcast/rupavahini-live-webcast2/", false) },
            Card("lkimport", "Add Sri Lankan streams", "Import your playlist for built-in playback", "+") { addDialog() }
        ).filter { it.title.contains(query, true) }
        if (results.isEmpty()) results += Card("empty", if (page == "Home") "Start with your channels" else "Nothing here yet", "Add a playlist or connect your PC to get started", "+") { addDialog() }
        return results
    }
    private fun renderCards() {
        if (!::grid.isInitialized) return
        val state = grid.layoutManager?.onSaveInstanceState()
        val data = cards()
        grid.adapter = object : RecyclerView.Adapter<CardHolder>() {
            init { setHasStableIds(true) }
            override fun getItemId(position: Int) = data[position].id.hashCode().toLong()
            override fun getItemCount() = data.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(17), d(16), d(17), d(15)); isClickable = true; Ui.focus(this) }
                box.layoutParams = RecyclerView.LayoutParams(-1, d(146)).apply { setMargins(d(5), d(5), d(5), d(5)) }
                val badge = Ui.label(this@MainActivity, "", 12f, Ui.accent)
                val logo = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                val title = Ui.label(this@MainActivity, "", 19f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                val sub = Ui.label(this@MainActivity, "", 11f, Ui.muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; addView(badge, LinearLayout.LayoutParams(0, -2, 1f)); addView(logo, LinearLayout.LayoutParams(d(44), d(30))) }
                box.addView(top); box.addView(title, LinearLayout.LayoutParams(-1, 0, 1f)); box.addView(sub)
                return CardHolder(box, badge, title, sub, logo)
            }
            override fun onBindViewHolder(holder: CardHolder, position: Int) {
                val c = data[position]; holder.badge.text = c.badge; holder.title.text = c.title; holder.sub.text = c.subtitle
                Logos.load(holder.logo, c.logo)
                holder.itemView.contentDescription = "${c.title}, ${c.subtitle}"
                holder.itemView.setOnClickListener { focusId = c.id; savedState = grid.layoutManager?.onSaveInstanceState(); c.action() }
                holder.itemView.setOnLongClickListener { linkActions(c.id); true }
                holder.itemView.setOnKeyListener { _, code, event -> if (code == android.view.KeyEvent.KEYCODE_MENU && event.action == android.view.KeyEvent.ACTION_UP) { linkActions(c.id); true } else false }
                if (focusId == c.id && !search.hasFocus()) holder.itemView.post { if (grid.hasFocus()) holder.itemView.requestFocus() }
            }
        }
        state?.let { grid.layoutManager?.onRestoreInstanceState(it) }
    }
    private class CardHolder(view: View, val badge: TextView, val title: TextView, val sub: TextView, val logo: ImageView) : RecyclerView.ViewHolder(view)
    private fun channelDialog(c: Channel) {
        val opts = arrayOf("Play", "Open with external player", if (c.id in store.favorites()) "Remove favorite" else "Add favorite", "Cancel")
        AlertDialog.Builder(this).setTitle(c.name).setItems(opts) { _, i -> when (i) { 0 -> play(c); 1 -> external(c); 2 -> Network.io.execute { store.favorite(c.id) } } }.show()
    }
    private fun play(c: Channel) { if (prefs.getBoolean("external", false)) external(c) else startActivity(Intent(this, PlaybackActivity::class.java).putExtra("id", c.id).putExtra("url", c.url).putExtra("name", c.name)) }
    private fun external(c: Channel) { try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)).setDataAndType(Uri.parse(c.url), if (c.url.substringBefore('?').endsWith(".m3u8")) "application/x-mpegURL" else "video/*"), "Play with")); Network.io.execute { store.watched(c.id) } } catch (_: Exception) { toast("No compatible external player installed") } }
    private fun openLink(url: String, chooser: Boolean) { if (!Playlist.safeUrl(url)) return; try { val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE); startActivity(if (chooser) Intent.createChooser(i, "Open with") else i) } catch (_: Exception) { toast("No browser installed. Install a TV-compatible browser.") } }
    private fun linkActions(id: String) {
        val l = store.links().find { it.id == id } ?: return
        AlertDialog.Builder(this).setTitle(l.name).setItems(arrayOf("Open", "Open with…", "Edit", if (l.home) "Remove from Home" else "Add to Home", "Move earlier", "Move later", "Delete…")) { _, i -> when(i) {
            0 -> openLink(l.url, false); 1 -> openLink(l.url, true); 2 -> linkForm(l)
            3 -> Network.io.execute { store.putLink(l.name, l.url, l.id, !l.home) }
            4, 5 -> Network.io.execute { store.moveLink(l.id, if (i == 4) -1 else 1) }
            6 -> AlertDialog.Builder(this).setTitle("Delete ${l.name}?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> Network.io.execute { store.deleteLink(l.id) } }.show()
        } }.show()
    }
    private fun addDialog() {
        AlertDialog.Builder(this).setTitle("Add to your library").setItems(arrayOf("Playlist URL", "Direct stream URL", "Paste M3U text", "Playlist file", "Web link (Others)", "Connect a PC", "Manage existing web links")) { _, i -> when(i) {
            0, 1, 2 -> sourceForm(listOf("playlist", "stream", "text")[i])
            3 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 101)
            4 -> linkForm(null); 5 -> pcDialog()
            6 -> { val links = store.links(); AlertDialog.Builder(this).setTitle("Manage Others").setItems(links.map { it.name }.toTypedArray()) { _, n -> linkActions(links[n].id) }.show() }
        } }.show()
    }
    private fun form(title: String, fields: List<Pair<String, String>>, action: (List<String>) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(24), d(10), d(24), d(10)) }
        val edits = fields.map { (hint, value) -> EditText(this).apply { this.hint = hint; setText(value); setTextColor(Ui.text); textSize = 16f; maxLines = 5 }.also { box.addView(it) } }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { action(edits.map { it.text.toString().trim() }); dialog.dismiss() } }; dialog.show()
    }
    private fun sourceForm(kind: String) = form("Add ${if (kind == "text") "M3U text" else kind}", listOf("Source name" to "", "URL or M3U text" to "")) { values -> background("Importing…") { Network.import(store, values[0], values[1], kind) } }
    private fun linkForm(l: WebLink?) = form("Web link", listOf("Name" to (l?.name ?: ""), "https://…" to (l?.url ?: ""))) { values -> background("Saving…") { store.putLink(values[0], values[1], l?.id ?: java.util.UUID.randomUUID().toString(), l?.home ?: false); "Link saved" } }
    private fun selectGroup() { val groups = listOf("All groups") + store.channels().flatMap { it.group.split(';') }.distinct().sorted(); AlertDialog.Builder(this).setTitle("Filter by group").setItems(groups.toTypedArray()) { _, i -> group = groups[i]; render() }.show() }
    private fun sourceDialog() {
        val sources = store.sources(); val options = listOf("All sources") + sources.map { it.name }
        AlertDialog.Builder(this).setTitle("Sources · select to filter/manage").setItems(options.toTypedArray()) { _, i ->
            if (i == 0) { sourceFilter = "All sources"; render() } else {
                val s = sources[i - 1]
                AlertDialog.Builder(this).setTitle(s.name).setItems(arrayOf("Show channels", "Refresh", "Edit URL", "Delete…")) { _, action -> when(action) {
                    0 -> { sourceFilter = s.name; page = "Channels"; render() }
                    1 -> background("Refreshing…") { require(s.url.isNotBlank()) { "Re-import pasted/direct sources to edit them" }; Network.import(store, s.name, s.url, "playlist") }
                    2 -> form("Edit source URL", listOf("Name" to s.name, "Playlist URL" to s.url)) { v -> background("Updating…") { require(v[0] == s.name) { "Keep the source name to retain identity" }; Network.import(store, v[0], v[1], "playlist") } }
                    3 -> AlertDialog.Builder(this).setTitle("Delete ${s.name} and its channels?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> background("Removing…") { store.deleteSource(s.name); "Source removed" } }.show()
                } }.show()
            }
        }.show()
    }
    private fun refreshSources() {
        if (refreshing) return; refreshing = true
        background("Refreshing sources…") { try { var ok = 0; var failed = 0; store.sources().filter { it.url.isNotBlank() }.forEach { s -> runCatching { Network.import(store, s.name, s.url, "playlist") }.onSuccess { ok++ }.onFailure { failed++ } }; "$ok sources refreshed · $failed failed (cached channels retained)" } finally { refreshing = false } }
    }
    private fun pcDialog() {
        if (server == null) { try { server = ManagerServer(this, store).also { it.start(15000, false) } } catch (_: Exception) { server = null; message("PC manager unavailable", "Could not open port 8765. Restart the app and try again."); return } }
        AlertDialog.Builder(this).setTitle("Connect your PC").setMessage("${server?.address()}\n\nPairing code: ${server?.pairingCode}\n\nOpen this address on a PC on the same trusted LAN. Code expires in 15 minutes; restart management for a new code. Keep the TV app running. HTTP is not encrypted.").setPositiveButton("Done", null).setNeutralButton("Stop server") { _, _ -> server?.stop(); server = null; render() }.show()
    }
    private fun catalogDialog() = form("Refresh Sri Lankan catalog", listOf("HTTPS M3U catalog URL" to prefs.getString("catalog", "").orEmpty())) { v -> background("Refreshing catalog…") { require(v[0].startsWith("https://")) { "Use an HTTPS M3U URL" }; val result = Network.import(store, "Sri Lankan TV", v[0], "playlist"); prefs.edit().putString("catalog", v[0]).apply(); result } }
    private fun updateDialog() {
        val repo = prefs.getString("repo", "").orEmpty()
        if (repo.isBlank()) { form("GitHub updates", listOf("Public repository: owner/repo" to "")) { v -> if (v[0].matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { prefs.edit().putString("repo", v[0]).apply(); updateDialog() } else toast("Enter owner/repo") }; return }
        background("Checking $repo…") { val release = Updater(this).check(repo); runOnUiThread {
            AlertDialog.Builder(this).setTitle(if (release.code > BuildConfig.VERSION_CODE) "Update ${release.name}" else "You're up to date")
                .setMessage("Installed: ${BuildConfig.VERSION_NAME}\nLatest: ${release.name}\n\n${release.notes.take(1800)}")
                .setNegativeButton("Close", null).setNeutralButton("Change repository") { _, _ -> prefs.edit().remove("repo").apply(); updateDialog() }
                .apply { if (release.code > BuildConfig.VERSION_CODE) setPositiveButton("Download & install") { _, _ -> downloadUpdate(release) } }.show()
        }; "Update check complete" }
    }
    private fun downloadUpdate(release: Updater.Release) {
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; setPadding(d(20), d(20), d(20), d(20)) }
        val dialog = AlertDialog.Builder(this).setTitle("Downloading update").setView(progress).setNegativeButton("Cancel") { _, _ -> cancel.set(true) }.setCancelable(false).show()
        Network.io.execute { try { val file = Updater(this).download(release, cancel) { n -> runOnUiThread { progress.progress = n } }; runOnUiThread { dialog.dismiss(); Updater(this).install(file) } } catch (e: Exception) { runOnUiThread { dialog.dismiss(); message("Update not installed", e.message ?: "Download failed") } } }
    }
    private fun background(start: String, action: () -> String) { toast(start); Network.io.execute { try { val result = action(); runOnUiThread { toast(result) } } catch (e: Exception) { runOnUiThread { message("Could not complete action", safeError(e)) } } } }
    private fun safeError(e: Exception): String { val value = e.message.orEmpty(); return if (value.contains("http", true) || value.length > 180) "Could not reach or read this source. Check its address and your network." else value.ifBlank { "Unexpected error. Try again." } }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun message(title: String, text: String) { if (!isFinishing) AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data); if (resultCode != RESULT_OK) return; val uri = data?.data ?: return
        when(requestCode) {
            101 -> form("Name this playlist", listOf("Source name" to "Imported playlist")) { v -> background("Importing file…") { val text = contentResolver.openInputStream(uri)!!.use { readLimited(it) }; Network.import(store, v[0], text, "text") } }
            102 -> background("Exporting…") { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(store.export()) }; "Backup exported" }
            103 -> AlertDialog.Builder(this).setTitle("Replace your library from backup?").setNegativeButton("Cancel", null).setPositiveButton("Restore") { _, _ -> background("Restoring…") { val text = contentResolver.openInputStream(uri)!!.use { readLimited(it) }; store.restore(text); "Backup restored" } }.show()
        }
    }
    private fun readLimited(input: java.io.InputStream): String { val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= Network.LIMIT) { "Maximum file size is 8 MB" }; output.write(buffer, 0, n) }; return output.toString("UTF-8") }
    @Deprecated("Remote Back compatibility")
    override fun onBackPressed() { if (page != "Home") { page = "Home"; query = ""; search.setText(""); render() } else super.onBackPressed() }
    override fun onDestroy() { store.listeners.remove(listener); server?.stop(); server = null; super.onDestroy() }
}
