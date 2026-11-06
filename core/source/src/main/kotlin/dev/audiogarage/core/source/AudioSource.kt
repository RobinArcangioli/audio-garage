package dev.audiogarage.core.source

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Delivers a complete audio file for a YouTube video.
 *
 * Implementations either resolve a stream URL and download it themselves (option A,
 * NewPipeExtractor + OkHttp) or delegate the whole job to an embedded tool (option B,
 * yt-dlp). That is why the contract is "fetch a file", not "resolve a URL".
 *
 * The returned flow is cold: collecting it starts the work, cancelling the collector
 * cancels it. It completes after emitting [FetchProgress.Completed], or fails with
 * an [AudioSourceException].
 */
interface AudioSource {
    /** Stable identifier used in logs and benchmark reports (e.g. "newpipe"). */
    val id: String

    /**
     * Fetches [videoId] into [destination]. The file only exists once the flow
     * completes successfully; on failure or cancellation no partial file is left there.
     */
    fun fetch(videoId: String, destination: File): Flow<FetchProgress>
}

sealed interface FetchProgress {
    /** Extraction started (metadata and stream URL resolution). */
    data object Resolving : FetchProgress

    /** A stream was chosen; [resolveMillis] measures the resolution alone. */
    data class Resolved(val stream: AudioStreamInfo, val resolveMillis: Long) : FetchProgress

    /** [totalBytes] is null when the server does not announce a length. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : FetchProgress

    data class Completed(val file: File, val stream: AudioStreamInfo, val timings: FetchTimings) : FetchProgress
}

/** What the source picked, as reported by the extractor (not yet verified on the file). */
data class AudioStreamInfo(
    val container: String,
    val codec: String?,
    /** Nominal bitrate from the extractor (itag table), not measured on the file. */
    val averageBitrateKbps: Int?,
    val contentLength: Long?,
    val itag: Int?,
    val durationSeconds: Long?,
)

data class FetchTimings(
    val resolveMillis: Long,
    /** From the download request to the first body byte. */
    val timeToFirstByteMillis: Long,
    val downloadMillis: Long,
    val totalMillis: Long,
    val bytes: Long,
)
