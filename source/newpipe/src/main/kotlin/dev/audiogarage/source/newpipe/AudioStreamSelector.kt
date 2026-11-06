package dev.audiogarage.source.newpipe

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * Picks the stream to download: M4A (AAC, played natively by Media3, no transcoding),
 * served as a plain HTTP file (not a DASH manifest), original audio track, best bitrate.
 */
internal object AudioStreamSelector {

    fun select(streams: List<AudioStream>): AudioStream? = streams
        .asSequence()
        .filter { it.format == MediaFormat.M4A }
        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        .maxByOrNull { it.averageBitrate }
}
