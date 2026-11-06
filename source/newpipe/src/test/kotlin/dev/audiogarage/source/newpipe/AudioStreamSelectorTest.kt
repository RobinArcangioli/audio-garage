package dev.audiogarage.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

class AudioStreamSelectorTest {

    private fun stream(
        id: String,
        format: MediaFormat = MediaFormat.M4A,
        bitrate: Int = 128,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        track: AudioTrackType? = null,
    ): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(format)
        .setDeliveryMethod(delivery)
        .setAverageBitrate(bitrate)
        .apply { if (track != null) setAudioTrackType(track) }
        .build()

    @Test
    fun `picks the highest bitrate progressive M4A stream`() {
        val chosen = AudioStreamSelector.select(
            listOf(
                stream("opus", format = MediaFormat.WEBMA_OPUS, bitrate = 160),
                stream("m4a-low", bitrate = 48),
                stream("m4a-high", bitrate = 128),
                stream("m4a-dash", bitrate = 256, delivery = DeliveryMethod.DASH),
            ),
        )
        assertEquals("m4a-high", chosen?.id)
    }

    @Test
    fun `ignores dubbed and descriptive tracks`() {
        val chosen = AudioStreamSelector.select(
            listOf(
                stream("dubbed", bitrate = 256, track = AudioTrackType.DUBBED),
                stream("descriptive", bitrate = 256, track = AudioTrackType.DESCRIPTIVE),
                stream("original", bitrate = 128, track = AudioTrackType.ORIGINAL),
            ),
        )
        assertEquals("original", chosen?.id)
    }

    @Test
    fun `returns null when no M4A stream exists`() {
        assertNull(AudioStreamSelector.select(listOf(stream("opus", format = MediaFormat.WEBMA_OPUS))))
    }
}
