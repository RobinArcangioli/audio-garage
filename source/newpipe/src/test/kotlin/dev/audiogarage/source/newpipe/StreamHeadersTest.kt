package dev.audiogarage.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

class StreamHeadersTest {

    private val base = "https://rr1---sn-example.googlevideo.com/videoplayback?expire=1&itag=140"

    @Test
    fun `visionOS URLs get the visionOS user agent`() {
        val ua = NewPipeAudioSource.streamHeaders("$base&c=VISIONOS&cver=1.02")["User-Agent"]
        assertEquals(YoutubeParsingHelper.getVisionOsUserAgent(null), ua)
    }

    @Test
    fun `other clients get the NewPipe desktop user agent`() {
        listOf("ANDROID", "IOS", "WEB").forEach { client ->
            val ua = NewPipeAudioSource.streamHeaders("$base&c=$client")["User-Agent"]
            assertEquals(OkHttpNewPipeDownloader.USER_AGENT, ua)
        }
        assertTrue(OkHttpNewPipeDownloader.USER_AGENT.startsWith("Mozilla/5.0"))
    }
}
