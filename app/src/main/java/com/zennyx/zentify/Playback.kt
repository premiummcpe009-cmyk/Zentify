package com.zennyx.zentify

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource

/** App-wide playback settings shared by the UI process and PlaybackService (same process). */
object Playback {
    const val EXTRA_VIDEO_URL = "videoUrl"
    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    val http: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent(DEFAULT_UA)

    @Volatile
    var chunkBytes: Long = 0L

    val dataSourceFactory = DataSource.Factory {
        val src: DataSource = if (chunkBytes > 0) ChunkedDataSource(http, chunkBytes) else http.createDataSource()
        src
    }

    /** Apply the headers the extractor returned for the stream about to be played. */
    fun configure(headers: Map<String, String>, chunk: Long) {
        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        http.setUserAgent(ua ?: DEFAULT_UA)
        http.setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
        chunkBytes = chunk
    }
}

/** Plays one URL normally, or merges audio + video when the item carries a video URL. */
class ZentifyMediaSourceFactory(private val base: DefaultMediaSourceFactory) : MediaSource.Factory by base {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audio = base.createMediaSource(mediaItem)
        val videoUrl = mediaItem.requestMetadata.extras?.getString(Playback.EXTRA_VIDEO_URL) ?: return audio
        return MergingMediaSource(audio, base.createMediaSource(MediaItem.fromUri(videoUrl)))
    }
}
