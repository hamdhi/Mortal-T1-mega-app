package lk.mortal.tv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackActivity : Activity() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private lateinit var view: PlayerView
    private lateinit var status: TextView
    private lateinit var rewind: Button
    private lateinit var forward: Button
    private var position = 0L
    private var playWhenReady = true
    private var restored = false
    private var retryCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val id get() = intent.getStringExtra("id").orEmpty()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.bg) }
        view = PlayerView(this).apply { setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); setShowSubtitleButton(true); setShowRewindButton(true); setShowFastForwardButton(true); controllerShowTimeoutMs = 5000 }
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val controls = LinearLayout(this).apply { setPadding(16, 4, 16, 4) }
        controls.addView(Ui.button(this, "‹ Back") { finish() })
        rewind = Ui.button(this, "−10 sec") { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekBack() }; controls.addView(rewind)
        controls.addView(Ui.button(this, "Play / pause") { player?.let { if (it.isPlaying) it.pause() else it.play() } })
        forward = Ui.button(this, "+10 sec") { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekForward() }; controls.addView(forward)
        controls.addView(Ui.button(this, "Go live") { player?.takeIf { it.isCurrentMediaItemLive }?.seekToDefaultPosition() })
        controls.addView(Ui.button(this, "Tracks / quality") { tracksDialog() })
        controls.addView(Ui.button(this, "Fit / fill") { view.resizeMode = if (view.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT })
        controls.addView(Ui.button(this, "Mute") { player?.let { it.volume = if (it.volume > 0) 0f else 1f } })
        controls.addView(Ui.button(this, "Retry") { retryCount = 0; player?.prepare(); player?.play() })
        bar.addView(controls); root.addView(bar)
        status = Ui.label(this, intent.getStringExtra("name").orEmpty(), 12f, Ui.muted).apply { setPadding(24, 4, 24, 8) }; root.addView(status)
        setContentView(root)
        position = savedInstanceState?.getLong("position") ?: Store.get(this).position(id)
        playWhenReady = savedInstanceState?.getBoolean("play", true) ?: true
        Network.io.execute { Store.get(this).watched(id) }
    }
    override fun onStart() { super.onStart(); startPlayer() }
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
        val type = if (p.isCurrentMediaItemLive) { if (p.isCurrentMediaItemSeekable) "LIVE · DVR seeking available" else "LIVE · this source does not support seeking" } else "On demand"
        status.text = "${intent.getStringExtra("name")}  •  $type" + if (p.playbackState == Player.STATE_BUFFERING) "  •  Buffering…" else ""
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
        AlertDialog.Builder(this).setTitle("Tracks & quality").setItems(labels.toTypedArray()) { _, i -> actions[i]() }.show()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when(event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND -> { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekForward(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { player?.let { if (it.isPlaying) it.pause() else it.play() }; return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    @Deprecated("Remote Back compatibility")
    override fun onBackPressed() { if (view.isControllerFullyVisible) view.hideController() else super.onBackPressed() }
    override fun onSaveInstanceState(outState: Bundle) { player?.let { outState.putLong("position", it.currentPosition); outState.putBoolean("play", it.playWhenReady) }; super.onSaveInstanceState(outState) }
    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { p -> position = if (!p.isCurrentMediaItemLive) p.currentPosition else 0L; playWhenReady = p.playWhenReady; Network.io.execute { Store.get(this).savePosition(id, position) } }
        session?.release(); session = null; view.player = null; player?.release(); player = null; restored = false
        super.onStop()
    }
}
