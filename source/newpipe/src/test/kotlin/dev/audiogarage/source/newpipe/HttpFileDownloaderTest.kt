package dev.audiogarage.source.newpipe

import dev.audiogarage.core.source.AudioSourceException
import dev.audiogarage.core.source.AudioSourceException.Kind
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class HttpFileDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val downloader = HttpFileDownloader(OkHttpClient(), chunkSize = CHUNK)
    private val payload = Random(42).nextBytes(1_000_000)
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var destination: File
    private val part get() = File(destination.path + HttpFileDownloader.PART_SUFFIX)

    @Before
    fun setUp() {
        server.start()
        destination = File(tmp.root, "out/track.m4a")
    }

    @After
    fun tearDown() = server.close()

    private fun url() = server.url("/audio").toString()

    /**
     * Serves [payload] honouring `Range: bytes=a-b`. [override] may replace the response
     * for a given request index (0-based); [ignoreRange] answers 200 with the whole body.
     */
    private fun serve(
        ignoreRange: Boolean = false,
        unknownTotal: Boolean = false,
        override: (index: Int, first: Long) -> MockResponse? = { _, _ -> null },
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val range = request.headers["Range"]
                if (ignoreRange || range == null) {
                    return MockResponse.Builder().body(Buffer().write(payload)).build()
                }
                val (a, b) = Regex("""bytes=(\d+)-(\d+)""").matchEntire(range)!!.destructured
                val first = a.toLong()
                override(requests.size - 1, first)?.let { return it }
                val last = minOf(b.toLong(), payload.size - 1L)
                val total = if (unknownTotal) "*" else payload.size.toString()
                return MockResponse.Builder()
                    .code(206)
                    .addHeader("Content-Range", "bytes $first-$last/$total")
                    .body(Buffer().write(payload, first.toInt(), (last - first + 1).toInt()))
                    .build()
            }
        }
    }

    private suspend fun downloadOrError(): Throwable? =
        runCatching { downloader.download(url(), emptyMap(), destination).toList() }.exceptionOrNull()

    @Test
    fun `downloads in sequential range chunks and renames the part file`() = runTest {
        serve()

        val events = downloader.download(url(), mapOf("User-Agent" to "test-ua"), destination).toList()

        assertArrayEquals(payload, destination.readBytes())
        assertFalse(part.exists())
        val expectedRequests = (payload.size + CHUNK - 1) / CHUNK
        assertEquals(expectedRequests.toInt(), requests.size)
        requests.forEachIndexed { i, r ->
            val first = i * CHUNK
            val last = minOf(first + CHUNK, payload.size.toLong()) - 1
            assertEquals("bytes=$first-$last", r.headers["Range"])
            assertEquals("test-ua", r.headers["User-Agent"])
        }
        val progress = events.filterIsInstance<HttpFileDownloader.Event.Progress>()
        assertEquals(progress.map { it.downloadedBytes }.sorted(), progress.map { it.downloadedBytes })
        assertEquals(payload.size.toLong(), progress.last().downloadedBytes)
        assertEquals(payload.size.toLong(), progress.last().totalBytes)
        val done = events.last() as HttpFileDownloader.Event.Done
        assertEquals(payload.size.toLong(), done.bytes)
        assertEquals(expectedRequests.toInt(), done.requests)
    }

    @Test
    fun `last request never asks past the known total`() = runTest {
        serve()
        downloader.download(url(), emptyMap(), destination).toList()
        assertEquals("bytes=917504-999999", requests.last().headers["Range"])
    }

    @Test
    fun `server ignoring Range is read in a single response`() = runTest {
        serve(ignoreRange = true)
        val done = downloader.download(url(), emptyMap(), destination).toList().last() as HttpFileDownloader.Event.Done
        assertArrayEquals(payload, destination.readBytes())
        assertEquals(1, done.requests)
    }

    @Test
    fun `unknown total ends on the first short chunk`() = runTest {
        serve(unknownTotal = true)
        downloader.download(url(), emptyMap(), destination).toList()
        assertArrayEquals(payload, destination.readBytes())
        assertEquals(8, requests.size)
    }

    @Test
    fun `429 is reported as blocking and leaves no file`() = runTest {
        serve { _, _ -> MockResponse.Builder().code(429).build() }
        assertKind(Kind.BLOCKED, downloadOrError(), httpStatus = 429)
        assertFalse(destination.exists() || part.exists())
    }

    @Test
    fun `403 on a later chunk stops at once and deletes the partial file`() = runTest {
        serve { index, _ -> if (index == 3) MockResponse.Builder().code(403).build() else null }
        assertKind(Kind.BLOCKED, downloadOrError(), httpStatus = 403)
        assertEquals(4, requests.size)
        assertFalse(destination.exists() || part.exists())
    }

    @Test
    fun `404 is reported as unavailable`() = runTest {
        serve { _, _ -> MockResponse.Builder().code(404).build() }
        assertKind(Kind.UNAVAILABLE, downloadOrError(), httpStatus = 404)
    }

    @Test
    fun `connection cut mid-chunk is a retryable network error and leaves no file`() = runTest {
        serve { index, first ->
            if (index != 2) return@serve null
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes $first-${first + CHUNK - 1}/${payload.size}")
                .body(Buffer().write(ByteArray(CHUNK.toInt())))
                .onResponseBody(SocketEffect.ShutdownConnection)
                .build()
        }
        val error = downloadOrError()
        assertKind(Kind.NETWORK, error)
        assertTrue((error as AudioSourceException).kind.retryable)
        assertFalse(destination.exists() || part.exists())
    }

    @Test
    fun `chunk starting at the wrong offset is rejected`() = runTest {
        serve { index, _ ->
            if (index != 1) return@serve null
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes 0-${CHUNK - 1}/${payload.size}")
                .body(Buffer().write(payload, 0, CHUNK.toInt()))
                .build()
        }
        assertKind(Kind.NETWORK, downloadOrError())
        assertFalse(destination.exists() || part.exists())
    }

    @Test
    fun `cancelling the collector deletes the part file`() = runTest {
        serve()
        // first {} cancels the flow once bytes were written to the part file.
        downloader.download(url(), emptyMap(), destination)
            .filterIsInstance<HttpFileDownloader.Event.Progress>()
            .first { it.downloadedBytes > 0 }
        assertFalse(destination.exists() || part.exists())
    }

    @Test
    fun `content range parsing`() {
        assertEquals(HttpFileDownloader.ContentRange(0, 524287, 74328447), HttpFileDownloader.parseContentRange("bytes 0-524287/74328447"))
        assertEquals(HttpFileDownloader.ContentRange(10, 19, null), HttpFileDownloader.parseContentRange("bytes 10-19/*"))
        assertNull(HttpFileDownloader.parseContentRange(null))
        assertNull(HttpFileDownloader.parseContentRange("bytes */100"))
        assertNull(HttpFileDownloader.parseContentRange("bytes 20-10/100"))
        assertNull(HttpFileDownloader.parseContentRange("bytes 0-100/100"))
    }

    private fun assertKind(kind: Kind, error: Throwable?, httpStatus: Int? = null) {
        if (error !is AudioSourceException) fail("Expected AudioSourceException, got $error")
        error as AudioSourceException
        assertEquals(kind, error.kind)
        if (httpStatus != null) assertEquals(httpStatus, error.httpStatus)
    }

    private companion object {
        const val CHUNK = 128L * 1024
    }
}
