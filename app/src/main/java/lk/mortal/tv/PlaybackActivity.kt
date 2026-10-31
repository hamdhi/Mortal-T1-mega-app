package lk.mortal.tv

import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private lateinit var view: PlayerView
    private lateinit var status: TextView
    private lateinit var rewind: Button
    private lateinit var forward: Button
    private lateinit var rewindThirty: Button
    private lateinit var seekClock: Button
    private lateinit var chrome: LinearLayout
    private var controlsVisible = false
    private var changingControls = false
    private var consumeOkUp = false
    private var trackDialogOpen = false
    private val hideControlsTask = Runnable { hideControls() }
    private var position = 0L
    private var playWhenReady = true
    private var restored = false
    private var retryCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val statusTick = object : Runnable { override fun run() { if (controlsVisible) updateStatus(); handler.postDelayed(this, 1000) } }
    private val id get() = intent.getStringExtra("id").orEmpty()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                val ok = event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
                if (ok && !controlsVisible) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { consumeOkUp = true; showControls() }
                    return true
                }
                if (ok && consumeOkUp) {
                    if (event.action == KeyEvent.ACTION_UP) consumeOkUp = false
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply { setBackgroundColor(Ui.bg) }
        view = PlayerView(this).apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); setShowSubtitleButton(true)
            setShowRewindButton(true); setShowFastForwardButton(true)
            controllerShowTimeoutMs = 0; controllerAutoShow = false; controllerHideOnTouch = false
            setControllerAnimationEnabled(false)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val bar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val controls = LinearLayout(this).apply { setPadding(16, 4, 16, 4) }
        controls.addView(Ui.button(this, "‹ Back") { finish() })
        rewind = Ui.button(this, "−10 sec") { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekBack() }; controls.addView(rewind)
        controls.addView(Ui.button(this, "Play / pause") { player?.let { if (it.isPlaying) it.pause() else it.play() } })
        forward = Ui.button(this, "+10 sec") { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekForward() }; controls.addView(forward)
        controls.addView(Ui.button(this, "Go live") { player?.takeIf { it.isCurrentMediaItemLive }?.seekToDefaultPosition() })
        rewindThirty = Ui.button(this, "−30 min") { rewindThirtyMinutes() }; controls.addView(rewindThirty)
        seekClock = Ui.button(this, "Go to time (AM/PM)") { seekByClock() }; controls.addView(seekClock)
        controls.addView(Ui.button(this, "Tracks / quality") { tracksDialog() })
        lateinit var screenMode: Button
        screenMode = Ui.button(this, "Screen: Stretch") {
            view.resizeMode = when (view.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            screenMode.text = "Screen: " + when (view.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                else -> "Stretch"
            }
        }
        controls.addView(screenMode)
        controls.addView(Ui.button(this, "Mute") { player?.let { it.volume = if (it.volume > 0) 0f else 1f } })
        controls.addView(Ui.button(this, "Retry") { retryCount = 0; player?.prepare(); player?.play() })
        chrome = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.argb(235, 11, 16, 25)); addView(bar) }
        bar.addView(controls)
        status = Ui.label(this, intent.getStringExtra("name").orEmpty(), 12f, Ui.muted).apply { setPadding(24, 4, 24, 8) }; chrome.addView(status)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val controller = view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
            (controller?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (params.bottomMargin != chrome.height) { params.bottomMargin = chrome.height; controller.layoutParams = params }
            }
        }
        setContentView(root)
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!changingControls) {
                if (visibility == View.VISIBLE && !controlsVisible) showControls()
                else if (visibility != View.VISIBLE && controlsVisible) hideControls()
            }
        })
        onBackPressedDispatcher.addCallback(this) {
            if (controlsVisible) hideControls()
            else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
        }
        position = savedInstanceState?.getLong("position") ?: Store.get(this).position(id)
        playWhenReady = savedInstanceState?.getBoolean("play", true) ?: true
        Network.io.execute { Store.get(this).watched(id) }
    }
    override fun onStart() { super.onStart(); startPlayer(); showControls(); handler.post(statusTick) }
    private fun showControls() {
        controlsVisible = true; chrome.visibility = View.VISIBLE
        changingControls = true; view.showController(); changingControls = false
        scheduleControlsHide()
    }
    private fun hideControls() {
        handler.removeCallbacks(hideControlsTask)
        controlsVisible = false; chrome.visibility = View.GONE
        changingControls = true; view.hideController(); changingControls = false
        view.requestFocus()
    }
    private fun scheduleControlsHide() {
        handler.removeCallbacks(hideControlsTask)
        if (!trackDialogOpen) handler.postDelayed(hideControlsTask, 10_000)
    }
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (controlsVisible) scheduleControlsHide()
    }
    private fun startPlayer() {
        if (player != null) return
        val url = intent.getStringExtra("url").orEmpty()
        if (!Playlist.safeUrl(url)) { status.text = "Invalid stream address"; return }
        val p = ExoPlayer.Builder(this).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build()
        player = p; view.player = p
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        p.setHandleAudioBecomingNoisy(true)
        session = MediaSession.Builder(this, p).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updateStatus()
                if (state == Player.STATE_READY && !restored) { restored = true; if (!p.isCurrentMediaItemLive && p.isCurrentMediaItemSeekable && position > 0) p.seekTo(position) }
                if (state == Player.STATE_READY) retryCount = 0
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { updateStatus() }
            override fun onPlayerError(error: PlaybackException) {
                status.text = "Playback failed (${error.errorCodeName}). Check the source/network or try an external player."
                if (error.errorCode in 2000..2999 && retryCount < 3) {
                    retryCount++; status.append(" Retry $retryCount/3…")
                    handler.postDelayed({ if (player === p) { p.prepare(); p.playWhenReady = playWhenReady } }, retryCount * 2000L)
                }
            }
        })
        p.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(intent.getStringExtra("name")).build()).build())
        p.prepare(); p.playWhenReady = playWhenReady
        view.requestFocus()
    }
    private fun updateStatus() {
        val p = player ?: return
        rewind.isEnabled = p.isCurrentMediaItemSeekable; forward.isEnabled = p.isCurrentMediaItemSeekable
        rewindThirty.visibility = if (p.isCurrentMediaItemLive) View.VISIBLE else View.GONE
        seekClock.visibility = if (p.isCurrentMediaItemLive) View.VISIBLE else View.GONE
        p.playerError?.let { status.text = "Playback failed (${it.errorCodeName}). Check the source/network or try an external player."; return }
        val type = if (p.isCurrentMediaItemLive) { if (p.isCurrentMediaItemSeekable) "LIVE · DVR seeking available" else "LIVE · this source does not support seeking" } else "On demand"
        status.text = "${intent.getStringExtra("name")}  •  $type" + if (p.playbackState == Player.STATE_BUFFERING) "  •  Buffering…" else ""
        liveWindow()?.let { w ->
            if (w.windowStartTimeMs != C.TIME_UNSET && w.durationMs > 0) {
                val format = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                status.append("  •  TV time ${format.format(java.util.Date(w.windowStartTimeMs + p.currentPosition))}  •  Available ${format.format(java.util.Date(w.windowStartTimeMs))}–${format.format(java.util.Date(w.windowStartTimeMs + w.durationMs))}")
            }
        }
    }
    private fun liveWindow(): Timeline.Window? {
        val p = player ?: return null
        if (!p.isCurrentMediaItemLive || p.currentTimeline.isEmpty) return null
        return p.currentTimeline.getWindow(p.currentMediaItemIndex, Timeline.Window())
    }
    private fun rewindThirtyMinutes() {
        val p = player ?: return
        val target = DvrClock.rewindPosition(p.currentPosition)
        if (!p.isCurrentMediaItemSeekable || target == null) {
            val minutes = if (p.isCurrentMediaItemSeekable) p.currentPosition.coerceAtLeast(0) / 60000 else 0
            showPlaybackMessage("30-minute rewind unavailable", "This source currently provides about $minutes minutes behind the playback position. Thirty minutes of past footage must be available from the channel/provider. Playback has not moved.")
        } else { p.seekTo(target); showControls() }
    }
    private fun seekByClock() {
        val p = player ?: return
        val w = liveWindow()
        if (w == null || !p.isCurrentMediaItemSeekable || w.windowStartTimeMs == C.TIME_UNSET || w.durationMs <= 0) {
            showPlaybackMessage("TV clock seek unavailable", "This channel must provide a seekable live/DVR window and broadcast timestamps. You can still use the seek bar for any history the stream exposes."); return
        }
        val format = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
        val start = w.windowStartTimeMs; val end = start + w.durationMs
        val initial = java.util.Calendar.getInstance().apply { timeInMillis = (start + p.currentPosition - DvrClock.THIRTY_MINUTES_MS).coerceIn(start, end) }
        trackDialogOpen = true; handler.removeCallbacks(hideControlsTask)
        val dialog = TimePickerDialog(this, { _, hour, minute ->
            val nowWindow = liveWindow()
            if (nowWindow == null || nowWindow.windowStartTimeMs == C.TIME_UNSET || nowWindow.durationMs <= 0) {
                Toast.makeText(this, "The live window changed. Try again.", Toast.LENGTH_LONG).show()
            } else {
                val nowStart = nowWindow.windowStartTimeMs; val nowEnd = nowStart + nowWindow.durationMs
                val requested = DvrClock.mostRecentTime(if (hour % 12 == 0) 12 else hour % 12, minute, hour >= 12, nowEnd)
                val target = DvrClock.positionAtTime(nowStart, nowWindow.durationMs, requested)
                if (target == null) Toast.makeText(this, "That time is outside the available history. Playback has not moved.", Toast.LENGTH_LONG).show()
                else p.seekTo(target)
            }
        }, initial.get(java.util.Calendar.HOUR_OF_DAY), initial.get(java.util.Calendar.MINUTE), false)
        dialog.setTitle("Available: ${format.format(java.util.Date(start))} – ${format.format(java.util.Date(end))}")
        dialog.setOnDismissListener { trackDialogOpen = false; showControls() }; dialog.show()
    }
    private fun showPlaybackMessage(title: String, message: String) {
        trackDialogOpen = true; handler.removeCallbacks(hideControlsTask)
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null)
            .setOnDismissListener { trackDialogOpen = false; showControls() }.show()
    }
    private fun tracksDialog() {
        val p = player ?: return
        val labels = mutableListOf("Video quality: Auto", "Subtitles: Off", "Audio: Auto")
        val actions = mutableListOf<() -> Unit>(
            { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build() },
            { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() },
            { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build() }
        )
        p.currentTracks.groups.forEach { group ->
            if (group.type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT, C.TRACK_TYPE_VIDEO)) {
                for (i in 0 until group.length) if (group.isTrackSupported(i)) {
                    val f = group.getTrackFormat(i)
                    val name = when(group.type) { C.TRACK_TYPE_VIDEO -> "Video: ${f.height.takeIf { it > 0 } ?: "?"}p · ${if (f.bitrate > 0) f.bitrate / 1000 else 0} kbps"; C.TRACK_TYPE_AUDIO -> "Audio: ${f.label ?: f.language ?: "Track ${i + 1}"}"; else -> "Subtitle: ${f.label ?: f.language ?: "Track ${i + 1}"}" }
                    labels.add(name + if (group.isTrackSelected(i)) " ✓" else "")
                    actions.add { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build() }
                }
            }
        }
        trackDialogOpen = true; handler.removeCallbacks(hideControlsTask)
        AlertDialog.Builder(this).setTitle("Tracks & quality").setItems(labels.toTypedArray()) { _, i -> actions[i]() }
            .setOnDismissListener { trackDialogOpen = false; showControls() }.show()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            when(keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND -> { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekForward(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { player?.let { if (it.isPlaying) it.pause() else it.play() }; return true }
            }
        return super.onKeyDown(keyCode, event)
    }
    override fun onSaveInstanceState(outState: Bundle) { player?.let { outState.putLong("position", it.currentPosition); outState.putBoolean("play", it.playWhenReady) }; super.onSaveInstanceState(outState) }
    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { p -> position = if (!p.isCurrentMediaItemLive) p.currentPosition else 0L; playWhenReady = p.playWhenReady; Network.io.execute { Store.get(this).savePosition(id, position) } }
        session?.release(); session = null; view.player = null; player?.release(); player = null; restored = false
        super.onStop()
    }
}
