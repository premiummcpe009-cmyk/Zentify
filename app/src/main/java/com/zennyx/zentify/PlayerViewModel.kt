package com.zennyx.zentify

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zennyx.zentify.data.Track
import com.zennyx.zentify.data.YtEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    var query by mutableStateOf("")
    var videoMode by mutableStateOf(false)
    var results by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var current by mutableStateOf<Track?>(null)
    var currentIsVideo by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var controller by mutableStateOf<MediaController?>(null)
        private set

    private var index = -1
    private var playJob: Job? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@PlayerViewModel.isPlaying = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_ENDED) next()
                }
            })
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let {
                    position = it.currentPosition.coerceAtLeast(0)
                    duration = it.duration.coerceAtLeast(0)
                }
            }
        }
    }

    fun setMode(video: Boolean) {
        if (video == videoMode) return
        videoMode = video
        results = emptyList()
        if (query.isNotBlank()) search()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true
        error = null
        viewModelScope.launch {
            runCatching { YtEngine.search(q, videoMode) }
                .onSuccess {
                    results = it
                    if (it.isEmpty()) error = "No results"
                }
                .onFailure { if (it !is CancellationException) error = it.message ?: "Search failed" }
            loading = false
        }
    }

    fun play(i: Int) {
        val t = results.getOrNull(i) ?: return
        index = i
        current = t
        currentIsVideo = videoMode
        buffering = true
        error = null
        playJob?.cancel()
        playJob = viewModelScope.launch {
            runCatching { YtEngine.resolve(t.id, videoMode) }
                .onSuccess { r ->
                    val c = controller ?: return@onSuccess
                    Playback.configure(r.headers, r.chunkBytes)
                    currentIsVideo = videoMode && r.videoUrl != null
                    val extras = r.videoUrl?.let { Bundle().apply { putString(Playback.EXTRA_VIDEO_URL, it) } }
                    val meta = MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setArtworkUri(t.thumb.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                    c.setMediaItem(
                        MediaItem.Builder()
                            .setUri(r.audioUrl)
                            .setMediaId(t.id)
                            .setMediaMetadata(meta)
                            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
                            .build(),
                    )
                    c.prepare()
                    c.play()
                }
                .onFailure {
                    if (it !is CancellationException) {
                        error = it.message ?: "Couldn't load this track"
                        buffering = false
                    }
                }
        }
    }

    fun next() { if (index + 1 in results.indices) play(index + 1) }
    fun previous() { if (index - 1 in results.indices) play(index - 1) }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekFraction(f: Float) {
        val c = controller ?: return
        if (duration > 0) c.seekTo((duration * f).toLong())
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
