package com.zennyx.zentify

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

private val UNSET = C.LENGTH_UNSET.toLong()

/**
 * Some YouTube clients reject open-ended range requests. This reads the media in
 * bounded chunks (Range: bytes=a-b) and stitches them into one continuous stream.
 */
class ChunkedDataSource(
    private val factory: DataSource.Factory,
    private val chunk: Long,
) : DataSource {

    private val listeners = ArrayList<TransferListener>()
    private var current: DataSource? = null
    private var spec: DataSpec? = null
    private var position = 0L
    private var end = UNSET
    private var chunkLeft = 0L
    private var shortChunk = false

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        current?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != UNSET) position + dataSpec.length else UNSET
        shortChunk = false
        openChunk()
        return if (end != UNSET) end - position else UNSET
    }

    private fun openChunk() {
        val s = spec ?: throw IOException("ChunkedDataSource is not open")
        val want = if (end != UNSET) minOf(chunk, end - position) else chunk
        val src = factory.createDataSource()
        listeners.forEach { src.addTransferListener(it) }
        val got = src.open(s.buildUpon().setPosition(position).setLength(want).build())
        current = src
        chunkLeft = if (got != UNSET) got else want
        shortChunk = got != UNSET && got < want
        if (end == UNSET) {
            val total = src.responseHeaders.entries
                .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value?.firstOrNull()
                ?.substringAfterLast('/')?.toLongOrNull()
            if (total != null) end = total
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val src = current ?: return C.RESULT_END_OF_INPUT
            if (chunkLeft > 0) {
                val n = src.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
                if (n != C.RESULT_END_OF_INPUT) {
                    chunkLeft -= n
                    position += n
                    return n
                }
                chunkLeft = 0
                shortChunk = true
            }
            src.close()
            current = null
            if (shortChunk || (end != UNSET && position >= end)) return C.RESULT_END_OF_INPUT
            openChunk()
        }
    }

    override fun getUri(): Uri? = current?.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        current?.close()
        current = null
        spec = null
    }
}
