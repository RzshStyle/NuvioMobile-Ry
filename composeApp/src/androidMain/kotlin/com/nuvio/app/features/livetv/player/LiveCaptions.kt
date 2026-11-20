package com.nuvio.app.features.livetv.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor
import com.nuvio.app.features.livetv.player.LiveTvCaptions.CC1
import com.nuvio.app.features.livetv.player.LiveTvCaptions.CC2
import com.nuvio.app.features.livetv.player.LiveTvCaptions.CC3
import com.nuvio.app.features.livetv.player.LiveTvCaptions.CC4
import com.nuvio.app.features.livetv.player.LiveTvCaptions.DIGITAL

/**
 * Closed captions inside live video (see [LiveTvCaptions]): tells the player to look for every
 * caption type and reads the caption data on the way to learn which ones really carry text.
 */
@OptIn(UnstableApi::class)
internal object LiveCaptions {
    /** Every caption type to look for, in a stream that doesn't list its own. */
    val formats: List<Format> = listOf(
        cea608(1), cea608(2), cea608(3), cea608(4),
        Format.Builder().setId(DIGITAL).setSampleMimeType(MimeTypes.APPLICATION_CEA708).setAccessibilityChannel(1).build()
    )

    private fun cea608(channel: Int) = Format.Builder()
        .setId("cc$channel")
        .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
        .setAccessibilityChannel(channel)
        .build()

    /** Bumped on every channel change, so data still arriving from the last one is ignored. */
    @Volatile var generation = 0
        private set

    fun reset() {
        generation++
        LiveTvCaptions.reset()
    }

    /** The caption type of a text track, or null for ordinary subtitles. */
    fun kindOf(f: Format): String? = when (f.sampleMimeType) {
        MimeTypes.APPLICATION_CEA608 -> when (f.accessibilityChannel) {
            2 -> CC2
            3 -> CC3
            4 -> CC4
            else -> CC1
        }
        MimeTypes.APPLICATION_CEA708 -> DIGITAL
        else -> null
    }

    private fun mark(gen: Int, kind: String) {
        if (gen == generation) LiveTvCaptions.mark(kind)
    }

    // ------------------------------------------------------------------ reading the caption data

    /**
     * One extractor's caption data: cc_data triplets (marker bits, cc_valid, cc_type, then two
     * bytes). cc_type 0 is the 608 first field (CC1/CC2), 1 the second field (CC3/CC4, plus XDS
     * program data, which isn't captions), 2 and 3 are 708 packets.
     */
    @OptIn(UnstableApi::class)
    private class Scanner(val gen: Int) {
        private var field1Second = false // CC2 rather than CC1
        private var field2Second = false // CC4 rather than CC3
        private var xds = false

        fun scan(d: ByteArray, off: Int, len: Int) {
            val end = minOf(off + len, d.size)
            var i = off
            while (i + 3 <= end) {
                val head = d[i].toInt() and 0xFF
                val raw1 = d[i + 1].toInt() and 0xFF
                val raw2 = d[i + 2].toInt() and 0xFF
                i += 3
                if ((head and 0x04) == 0) continue // not valid: padding
                when (head and 0x03) {
                    0 -> field(first = true, b1 = raw1 and 0x7F, b2 = raw2 and 0x7F)
                    1 -> field(first = false, b1 = raw1 and 0x7F, b2 = raw2 and 0x7F)
                    // 708: a packet start whose first service block isn't empty, or packet data.
                    3 -> if (raw2 != 0) mark(gen, DIGITAL)
                    else -> if (raw1 != 0 || raw2 != 0) mark(gen, DIGITAL)
                }
            }
        }

        private fun field(first: Boolean, b1: Int, b2: Int) {
            if (b1 == 0 && b2 == 0) return
            if (!first && b1 in 0x01..0x0E) { xds = true; return }
            if (!first && b1 == 0x0F) { xds = false; return }
            if (b1 in 0x10..0x1F) {
                // A control code: says which of the field's two tracks the text that follows is for.
                val second = (b1 and 0x08) != 0
                if (first) field1Second = second else { field2Second = second; xds = false }
                return
            }
            if (!first && xds) return
            if (b1 >= 0x20) {
                mark(gen, if (first) (if (field1Second) CC2 else CC1) else (if (field2Second) CC4 else CC3))
            }
        }
    }

    /** Passes everything through untouched; caption data is read on the way. */
    @OptIn(UnstableApi::class)
    private class SpyOutput(private val inner: ExtractorOutput, gen: Int) : ExtractorOutput {
        val scanner = Scanner(gen)
        /** The caption data reaches every caption track the same: read it from one only. */
        @Volatile var reader: TrackOutput? = null

        override fun track(id: Int, type: Int): TrackOutput {
            val t = inner.track(id, type)
            return if (type == C.TRACK_TYPE_TEXT) SpyTrack(t, this) else t
        }

        override fun endTracks() = inner.endTracks()
        override fun seekMap(seekMap: SeekMap) = inner.seekMap(seekMap)
    }

    @OptIn(UnstableApi::class)
    private class SpyTrack(private val inner: TrackOutput, private val owner: SpyOutput) : TrackOutput {
        override fun format(format: Format) {
            if (owner.reader == null && kindOf(format) != null) owner.reader = this
            inner.format(format)
        }

        override fun durationUs(durationUs: Long) = inner.durationUs(durationUs)

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            inner.sampleData(input, length, allowEndOfInput, sampleDataPart)

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (owner.reader === this) runCatching { owner.scanner.scan(data.data, data.position, length) }
            inner.sampleData(data, length, sampleDataPart)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) =
            inner.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }

    /** A TS extractor whose caption data is read on the way (everything else as before). */
    @OptIn(UnstableApi::class)
    private class SpyExtractor(private val inner: Extractor) : Extractor {
        override fun sniff(input: ExtractorInput): Boolean = inner.sniff(input)
        override fun getSniffFailureDetails(): List<SniffFailure> = inner.getSniffFailureDetails()
        override fun init(output: ExtractorOutput) = inner.init(SpyOutput(output, generation))
        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = inner.read(input, seekPosition)
        override fun seek(position: Long, timeUs: Long) = inner.seek(position, timeUs)
        override fun release() = inner.release()
        override fun getUnderlyingImplementation(): Extractor = inner.getUnderlyingImplementation()
    }

    /** Plain TS streams (Xtream ".ts" links): the usual extractors, with the TS one watched. */
    @OptIn(UnstableApi::class)
    class Extractors(private val inner: DefaultExtractorsFactory) : ExtractorsFactory {
        private fun watch(e: Extractor): Extractor =
            if (e.getUnderlyingImplementation() is TsExtractor) SpyExtractor(e) else e

        override fun createExtractors(): Array<Extractor> =
            inner.createExtractors().map { watch(it) }.toTypedArray()

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            inner.createExtractors(uri, responseHeaders).map { watch(it) }.toTypedArray()

        @Suppress("DEPRECATION")
        override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
            inner.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
            return this
        }

        override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
            inner.setSubtitleParserFactory(subtitleParserFactory)
            return this
        }

        override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory {
            inner.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
            return this
        }
    }

    @OptIn(UnstableApi::class)
    private class SpyChunk(private val inner: HlsMediaChunkExtractor) : HlsMediaChunkExtractor {
        override fun init(extractorOutput: ExtractorOutput) = inner.init(SpyOutput(extractorOutput, generation))
        override fun read(extractorInput: ExtractorInput): Boolean = inner.read(extractorInput)
        override fun isPackedAudioExtractor(): Boolean = inner.isPackedAudioExtractor()
        override fun isReusable(): Boolean = inner.isReusable()
        override fun recreate(): HlsMediaChunkExtractor = SpyChunk(inner.recreate())
        override fun onTruncatedSegmentParsed() = inner.onTruncatedSegmentParsed()
    }

    /**
     * HLS (".m3u8") streams: a playlist that lists its captions keeps its own list; otherwise
     * every caption type is looked for, as with TS. Caption data is watched either way.
     */
    @OptIn(UnstableApi::class)
    class Hls(private val inner: DefaultHlsExtractorFactory) : HlsExtractorFactory {
        override fun createExtractor(
            uri: Uri,
            format: Format,
            muxedCaptionFormats: List<Format>?,
            timestampAdjuster: TimestampAdjuster,
            responseHeaders: Map<String, List<String>>,
            sniffingExtractorInput: ExtractorInput,
            playerId: PlayerId
        ): HlsMediaChunkExtractor = SpyChunk(
            inner.createExtractor(
                // A playlist saying "no captions" (CLOSED-CAPTIONS=NONE) is often wrong: many
                // providers write it on every channel and carry the captions anyway. Looked for
                // regardless, as TiviMate does; the menu still only offers ones with text.
                uri, format, muxedCaptionFormats?.takeIf { it.isNotEmpty() } ?: formats, timestampAdjuster,
                responseHeaders, sniffingExtractorInput, playerId
            )
        )

        override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): HlsExtractorFactory {
            inner.setSubtitleParserFactory(subtitleParserFactory)
            return this
        }

        override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): HlsExtractorFactory {
            inner.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
            return this
        }

        override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): HlsExtractorFactory {
            inner.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
            return this
        }

        override fun getOutputTextFormat(sourceFormat: Format): Format = inner.getOutputTextFormat(sourceFormat)
    }
}
