package dev.audiogarage.source.newpipe

import dev.audiogarage.core.source.AudioSource
import dev.audiogarage.core.source.AudioSourceException
import dev.audiogarage.core.source.AudioSourceException.Kind
import dev.audiogarage.core.source.AudioStreamInfo
import dev.audiogarage.core.source.FetchProgress
import dev.audiogarage.core.source.FetchTimings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException

/** Option A: NewPipeExtractor resolves, [HttpFileDownloader] downloads. */
class NewPipeAudioSource(
    private val client: OkHttpClient,
    private val nanoTime: () -> Long = System::nanoTime,
) : AudioSource {

    override val id: String = "newpipe"

    private val fileDownloader = HttpFileDownloader(client, nanoTime)

    override fun fetch(videoId: String, destination: File): Flow<FetchProgress> = flow {
        val start = nanoTime()
        emit(FetchProgress.Resolving)
        NewPipeRuntime.ensureInitialized(client)

        val info = resolve(videoId)
        val stream = AudioStreamSelector.select(info.audioStreams)
            ?: throw AudioSourceException(Kind.NO_SUITABLE_STREAM, "No progressive M4A stream for $videoId")
        val streamInfo = stream.toInfo(info.duration)
        val resolveMillis = millisSince(start)
        emit(FetchProgress.Resolved(streamInfo, resolveMillis))

        fileDownloader.download(stream.content, streamHeaders(stream.content), destination).collect { event ->
            when (event) {
                is HttpFileDownloader.Event.Progress ->
                    emit(FetchProgress.Downloading(event.downloadedBytes, event.totalBytes))
                is HttpFileDownloader.Event.Done -> emit(
                    FetchProgress.Completed(
                        file = destination,
                        stream = streamInfo,
                        timings = FetchTimings(
                            resolveMillis = resolveMillis,
                            timeToFirstByteMillis = event.timeToFirstByteMillis,
                            downloadMillis = event.downloadMillis,
                            totalMillis = millisSince(start),
                            bytes = event.bytes,
                        ),
                    ),
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun resolve(videoId: String): StreamInfo = try {
        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw mapExtractorFailure(videoId, e)
    }

    private fun millisSince(start: Long) = (nanoTime() - start) / 1_000_000

    companion object {
        /**
         * Headers for a videoplayback URL, mirroring the NewPipe app (YoutubeHttpDataSource,
         * 2026-11): URLs produced by the visionOS InnerTube client require that client's
         * user agent, every other URL gets the desktop user agent. Nothing else is spoofed.
         */
        internal fun streamHeaders(url: String): Map<String, String> = mapOf(
            "User-Agent" to if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                YoutubeParsingHelper.getVisionOsUserAgent(null)
            } else {
                OkHttpNewPipeDownloader.USER_AGENT
            },
        )

        internal fun mapExtractorFailure(videoId: String, e: Exception): AudioSourceException = when (e) {
            is ReCaptchaException, is SignInConfirmNotBotException ->
                AudioSourceException(Kind.BLOCKED, "YouTube anti-bot check for $videoId: ${e.message}", e)
            is ContentNotAvailableException ->
                AudioSourceException(Kind.UNAVAILABLE, "$videoId unavailable (${e.javaClass.simpleName}): ${e.message}", e)
            is ExtractionException ->
                AudioSourceException(Kind.EXTRACTION, "Extraction failed for $videoId: ${e.message}", e)
            is IOException ->
                AudioSourceException(Kind.NETWORK, "Network error while resolving $videoId: ${e.message}", e)
            else -> AudioSourceException(Kind.EXTRACTION, "Unexpected failure for $videoId: $e", e)
        }

        private fun AudioStream.toInfo(durationSeconds: Long) = AudioStreamInfo(
            container = format?.suffix ?: "unknown",
            codec = codec,
            averageBitrateKbps = averageBitrate.takeIf { it > 0 },
            contentLength = itagItem?.contentLength?.takeIf { it > 0 },
            itag = itag.takeIf { it > 0 },
            durationSeconds = durationSeconds.takeIf { it > 0 },
        )
    }
}
