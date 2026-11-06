package dev.audiogarage.tools.probe

import dev.audiogarage.core.source.AudioSource
import dev.audiogarage.core.source.AudioSourceException
import dev.audiogarage.core.source.FetchProgress
import dev.audiogarage.source.newpipe.NewPipeAudioSource
import dev.audiogarage.source.newpipe.NewPipeRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess

private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private const val USAGE = """Usage:
  sample <playlistUrl> [count=3] [scan=100]   pick tracks of varied duration from the first <scan> items
  streams <videoId> [urlFile]                  list audio streams and the InnerTube client that produced each URL (no download);
                                               optionally write "itag<TAB>url" lines to urlFile for manual diagnosis
  fetch <outDir> <videoId>...                  download each video with the NewPipe source, append to <outDir>/fetch.csv"""

fun main(args: Array<String>) = runBlocking {
    when (args.firstOrNull()) {
        "sample" -> sample(args.getOrNull(1) ?: usage(), args.getOrNull(2)?.toInt() ?: 3, args.getOrNull(3)?.toInt() ?: 100)
        "streams" -> streams(args.getOrNull(1) ?: usage(), args.getOrNull(2)?.let(::File))
        "fetch" -> fetch(File(args.getOrNull(1) ?: usage()), args.drop(2).ifEmpty { usage() })
        else -> usage()
    }
}

private fun usage(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}

/** Reads playlist pages until [scan] items, then picks shortest, longest and evenly spaced ones in between. */
private fun sample(playlistUrl: String, count: Int, scan: Int) {
    NewPipeRuntime.ensureInitialized(client)
    val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
    val items = info.relatedItems.toMutableList()
    var page = info.nextPage
    while (items.size < scan && page != null) {
        val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, playlistUrl, page)
        items += more.items
        page = more.nextPage
    }
    val playable = items.take(scan).filter { it.duration > 0 }.sortedBy(StreamInfoItem::getDuration)
    println("# playlist='${info.name}' declaredCount=${info.streamCount} scanned=${items.size.coerceAtMost(scan)} withDuration=${playable.size}")
    if (playable.isEmpty()) return
    val picks = (0 until count).map { i -> playable[(i * (playable.size - 1)) / (count - 1).coerceAtLeast(1)] }.distinct()
    picks.forEach { println("${videoId(it.url)}\t${it.duration}s\t${it.name}") }
}

private fun streams(videoId: String, urlFile: File?) {
    NewPipeRuntime.ensureInitialized(client)
    val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
    println("itag\tformat\tdelivery\tkbps\ttrack\tclient\tcontentLength")
    info.audioStreams.forEach { s ->
        val clientName = if (s.isUrl) s.content.substringAfter("&c=", "?").substringBefore('&') else "-"
        println("${s.itag}\t${s.format?.suffix}\t${s.deliveryMethod}\t${s.averageBitrate}\t${s.audioTrackType}\t$clientName\t${s.itagItem?.contentLength}")
    }
    urlFile?.writeText(info.audioStreams.filter { it.isUrl }.joinToString("") { "${it.itag}\t${it.content}\n" })
}

private suspend fun fetch(outDir: File, videoIds: List<String>) {
    outDir.mkdirs()
    val csv = File(outDir, "fetch.csv")
    if (!csv.exists()) csv.writeText(CSV_HEADER + "\n")
    val source: AudioSource = NewPipeAudioSource(client)
    videoIds.forEachIndexed { index, videoId ->
        if (index > 0) {
            // Polite spacing between tracks (final values come from step 0.11).
            val pause = 5_000L + Random.nextLong(0, 3_000)
            println("… pause ${pause}ms")
            delay(pause)
        }
        val destination = File(outDir, "$videoId.m4a")
        val startedAt = Instant.now()
        try {
            var completed: FetchProgress.Completed? = null
            source.fetch(videoId, destination).collect { progress ->
                when (progress) {
                    is FetchProgress.Resolved -> println("$videoId resolved in ${progress.resolveMillis}ms: ${progress.stream}")
                    is FetchProgress.Completed -> completed = progress
                    else -> Unit
                }
            }
            val done = completed!!
            val t = done.timings
            val kBps = if (t.downloadMillis > 0) t.bytes * 1000 / 1024 / t.downloadMillis else 0
            println("$videoId OK ${t.bytes} bytes, resolve=${t.resolveMillis}ms ttfb=${t.timeToFirstByteMillis}ms download=${t.downloadMillis}ms total=${t.totalMillis}ms (${kBps} KiB/s)")
            csv.appendText(
                listOf(
                    startedAt, source.id, videoId, "ok", "", "",
                    done.stream.durationSeconds, done.stream.container, done.stream.codec, done.stream.averageBitrateKbps,
                    done.stream.itag, done.stream.contentLength, t.resolveMillis, t.timeToFirstByteMillis,
                    t.downloadMillis, t.totalMillis, t.bytes, kBps,
                ).joinToString(",") { it?.toString() ?: "" } + "\n",
            )
        } catch (e: AudioSourceException) {
            println("$videoId FAILED ${e.kind} http=${e.httpStatus}: ${e.message}")
            csv.appendText(listOf(startedAt, source.id, videoId, "error", e.kind, e.httpStatus ?: "").joinToString(",") + ",,,,,,,,,,,,\n")
            if (e.kind == AudioSourceException.Kind.BLOCKED) {
                println("Blocking signal: stopping immediately.")
                return
            }
        }
    }
}

private const val CSV_HEADER = "started_at,source,video_id,status,error_kind,http_status,duration_s,container,codec," +
    "nominal_kbps,itag,content_length,resolve_ms,ttfb_ms,download_ms,total_ms,bytes,throughput_kib_s"

private fun videoId(url: String) = url.substringAfter("v=").substringBefore('&')
