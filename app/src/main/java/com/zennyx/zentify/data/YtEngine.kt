package com.zennyx.zentify.data

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object YtEngine {

    private val client = InnerTube(httpClient = HttpClient(OkHttp))

    private val extractor: InnerTubeExtractor by lazy {
        val configStore = RemotePlayerConfigStore(
            httpClient = client.httpClient,
            repository = PlayerConfigRepository.disabled(),
        )
        val cipher = YouTubeCipherService(client.httpClient, configStore)
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(client.httpClient, client, configStore, cipherService = cipher),
            cipherService = cipher,
            innerTube = client,
        )
    }

    /** video = false -> YouTube Music (songs), video = true -> YouTube (videos). */
    suspend fun search(query: String, video: Boolean): List<Track> = withContext(Dispatchers.IO) {
        val yt = if (video) YouTubeClient.WEB else YouTubeClient.WEB_REMIX
        val raw = client.search(yt, query = query).bodyAsText()
        val root = Json.parseToJsonElement(raw)
        val out = mutableListOf<JsonObject>()
        collect(root, if (video) "videoRenderer" else "musicResponsiveListItemRenderer", out)
        out.mapNotNull { if (video) parseVideo(it) else parseMusic(it) }.distinctBy { it.id }
    }

    class Resolved(
        val audioUrl: String,
        val videoUrl: String?,
        val headers: Map<String, String>,
        /** > 0 when the server needs bounded range requests (read in chunks of this size). */
        val chunkBytes: Long,
    )

    /** Direct (non-SABR, non-HLS) URLs ExoPlayer can play. Video comes as a separate stream to merge. */
    suspend fun resolve(videoId: String, video: Boolean): Resolved = withContext(Dispatchers.IO) {
        val hints = ContentHints(wantVideo = video, maxVideoHeight = if (video) 720 else null)
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true, allowProgressive = false)
        val s = extractor.extract(videoId = videoId, hints = hints, audioQuality = AudioQuality.HIGH)
            ?: error("No playable stream for this track")
        Resolved(
            audioUrl = s.audioUrl,
            videoUrl = if (video) s.videoUrl else null,
            headers = s.headers,
            chunkBytes = if (s.requireBoundedRange || s.useRangeChunks) s.rangeChunkSizeBytes else 0L,
        )
    }

    // ----- JSON helpers (typed-model free, so they survive library model changes) -----

    private fun collect(el: JsonElement, key: String, out: MutableList<JsonObject>) {
        when (el) {
            is JsonObject -> el.forEach { (k, v) -> if (k == key && v is JsonObject) out += v else collect(v, key, out) }
            is JsonArray -> el.forEach { collect(it, key, out) }
            else -> Unit
        }
    }

    private fun JsonElement?.at(vararg path: Any): JsonElement? {
        var cur: JsonElement? = this
        for (p in path) {
            cur = when (p) {
                is String -> (cur as? JsonObject)?.get(p)
                is Int -> (cur as? JsonArray)?.let { if (p in it.indices) it[p] else null }
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun lastThumb(arr: JsonElement?): String =
        (arr as? JsonArray)?.lastOrNull().at("url").text().orEmpty()

    private fun parseVideo(r: JsonObject): Track? {
        val id = r.at("videoId").text() ?: return null
        val title = r.at("title", "runs", 0, "text").text() ?: return null
        val channel = r.at("ownerText", "runs", 0, "text").text()
            ?: r.at("longBylineText", "runs", 0, "text").text().orEmpty()
        return Track(
            id = id,
            title = title,
            artist = channel,
            thumb = lastThumb(r.at("thumbnail", "thumbnails")),
            duration = r.at("lengthText", "simpleText").text().orEmpty(),
        )
    }

    private fun parseMusic(r: JsonObject): Track? {
        val id = r.at("playlistItemData", "videoId").text() ?: return null
        val col = "musicResponsiveListItemFlexColumnRenderer"
        val title = r.at("flexColumns", 0, col, "text", "runs", 0, "text").text() ?: return null
        val sub = (r.at("flexColumns", 1, col, "text", "runs") as? JsonArray)
            ?.mapNotNull { it.at("text").text() }?.joinToString("").orEmpty()
        val parts = sub.split(" • ").map { it.trim() }.filter { it.isNotEmpty() && it != "Song" && it != "Video" }
        val duration = parts.lastOrNull()?.takeIf { Regex("""\d+:\d{2}(:\d{2})?""").matches(it) }.orEmpty()
        return Track(
            id = id,
            title = title,
            artist = parts.firstOrNull().orEmpty(),
            thumb = lastThumb(r.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            duration = duration,
        )
    }
}
