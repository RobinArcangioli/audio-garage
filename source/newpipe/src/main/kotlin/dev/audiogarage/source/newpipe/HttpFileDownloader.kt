package dev.audiogarage.source.newpipe

import dev.audiogarage.core.source.AudioSourceException
import dev.audiogarage.core.source.AudioSourceException.Kind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Downloads to `<destination>.part` with sequential HTTP Range requests of [chunkSize] bytes
 * (RFC 9110 §14), then renames the part file into place. A single long request to the stream
 * server was measured at ~30 KiB/s and was cut before the end on a 74 MB file (step 0.2);
 * the NewPipe app downloads in 512 KiB blocks for the same reason.
 * If the server ignores Range (200 instead of 206), the whole body is read from that response.
 * Step 0.4 adds resume across failures, retries and integrity checks on top of this.
 */
internal class HttpFileDownloader(
    private val client: OkHttpClient,
    private val nanoTime: () -> Long = System::nanoTime,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
    }

    sealed interface Event {
        data class Progress(val downloadedBytes: Long, val totalBytes: Long?) : Event
        data class Done(
            val bytes: Long,
            val timeToFirstByteMillis: Long,
            val downloadMillis: Long,
            val requests: Int,
        ) : Event
    }

    fun download(url: String, headers: Map<String, String>, destination: File): Flow<Event> = flow {
        val part = File(destination.path + PART_SUFFIX)
        destination.parentFile?.mkdirs()
        var completed = false
        try {
            val state = State(start = nanoTime())
            emit(Event.Progress(0, null))
            part.outputStream().use { output ->
                do {
                    val end = state.total?.let { minOf(state.downloaded + chunkSize, it) - 1 }
                        ?: (state.downloaded + chunkSize - 1)
                    val request = Request.Builder().url(url)
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .header("Range", "bytes=${state.downloaded}-$end")
                        .build()
                    execute(request).use { response -> readChunk(response, output, state, end) }
                } while (state.total == null || state.downloaded < state.total!!)
            }
            emit(Event.Progress(state.downloaded, state.total))
            moveIntoPlace(part, destination)
            completed = true
            emit(Event.Done(state.downloaded, state.firstByteMillis.coerceAtLeast(0), millisSince(state.start), state.requests))
        } finally {
            if (!completed) part.delete()
        }
    }

    private class State(val start: Long) {
        var total: Long? = null
        var downloaded = 0L
        var firstByteMillis = -1L
        var lastReported = 0L
        var requests = 0
    }

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        throw AudioSourceException(Kind.NETWORK, "Download request failed: ${e.message}", e)
    }

    private suspend fun FlowCollector<Event>.readChunk(response: Response, output: OutputStream, state: State, requestedEnd: Long) {
        state.requests++
        val expected: Long? = when (response.code) {
            206 -> {
                val range = parseContentRange(response.header("Content-Range"))
                    ?: throw AudioSourceException(Kind.NETWORK, "Missing or invalid Content-Range: ${response.header("Content-Range")}")
                if (range.first != state.downloaded) {
                    throw AudioSourceException(Kind.NETWORK, "Content-Range starts at ${range.first}, expected ${state.downloaded}")
                }
                if (range.total != null) state.total = range.total
                range.last - range.first + 1
            }
            // Server ignored Range: the body is the whole file.
            200 -> {
                if (state.downloaded != 0L) throw AudioSourceException(Kind.NETWORK, "HTTP 200 to a Range request at offset ${state.downloaded}")
                response.body.contentLength().takeIf { it >= 0 }.also { state.total = it }
            }
            else -> if (response.isSuccessful) {
                throw AudioSourceException(Kind.NETWORK, "Unexpected HTTP ${response.code} from stream server", httpStatus = response.code)
            } else {
                throw httpFailure(response.code)
            }
        }
        val chunkStart = state.downloaded
        try {
            response.body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (state.firstByteMillis < 0) state.firstByteMillis = millisSince(state.start)
                    output.write(buffer, 0, read)
                    state.downloaded += read
                    if (state.downloaded - state.lastReported >= PROGRESS_STEP_BYTES) {
                        state.lastReported = state.downloaded
                        emit(Event.Progress(state.downloaded, state.total))
                    }
                }
            }
        } catch (e: IOException) {
            throw AudioSourceException(Kind.NETWORK, "Download interrupted after ${state.downloaded} bytes", e)
        }
        val received = state.downloaded - chunkStart
        if (expected != null && received != expected) {
            throw AudioSourceException(Kind.NETWORK, "Truncated chunk at $chunkStart: $received of $expected bytes")
        }
        if (response.code == 200) {
            state.total = state.downloaded
        } else if (received == 0L) {
            throw AudioSourceException(Kind.NETWORK, "Empty chunk at $chunkStart")
        } else if (state.total == null && chunkStart + received - 1 < requestedEnd) {
            // Unknown total (`/*`) and a short chunk: that was the end of the file.
            state.total = state.downloaded
        }
    }

    private fun millisSince(start: Long) = (nanoTime() - start) / 1_000_000

    private fun moveIntoPlace(part: File, destination: File) {
        try {
            Files.move(part.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal data class ContentRange(val first: Long, val last: Long, val total: Long?)

    companion object {
        const val PART_SUFFIX = ".part"
        const val DEFAULT_CHUNK_SIZE = 512L * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256L * 1024
        private val CONTENT_RANGE = Regex("""^bytes (\d+)-(\d+)/(\d+|\*)$""")

        /** Parses `bytes first-last/total` (total may be `*`); null if absent or malformed. */
        internal fun parseContentRange(value: String?): ContentRange? {
            val match = CONTENT_RANGE.matchEntire(value?.trim() ?: return null) ?: return null
            val (first, last, total) = match.destructured
            val range = ContentRange(first.toLong(), last.toLong(), total.toLongOrNull())
            return range.takeIf { it.last >= it.first && (it.total == null || it.last < it.total) }
        }

        /** 403/429 from the CDN are treated as blocking until step 0.11 measures their real meaning. */
        fun httpFailure(code: Int): AudioSourceException = when (code) {
            403, 429 -> AudioSourceException(Kind.BLOCKED, "HTTP $code from stream server", httpStatus = code)
            404, 410 -> AudioSourceException(Kind.UNAVAILABLE, "HTTP $code from stream server", httpStatus = code)
            else -> AudioSourceException(Kind.NETWORK, "HTTP $code from stream server", httpStatus = code)
        }
    }
}
