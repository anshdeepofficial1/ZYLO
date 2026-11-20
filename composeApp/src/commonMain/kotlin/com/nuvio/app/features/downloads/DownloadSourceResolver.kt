package com.nuvio.app.features.downloads

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.streams.StreamContentFilter
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.ZyloStreams
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * A real, downloadable direct-file source together with the server facts discovered by a
 * small HEAD/range probe. Nothing here is invented: quality, size, resume support and the
 * response-speed hint all come from the stream metadata or the server itself.
 */
data class DownloadSourceOption(
    val stream: StreamItem,
    val sourceLabel: String,
    val qualityLabel: String,
    val sizeBytes: Long?,
    val languageLabel: String?,
    val latencyMs: Long?,
    val resumable: Boolean?,
) {
    val sizeLabel: String?
        get() = sizeBytes?.takeIf { it > 0L }?.let(::formatDownloadSize)

    /** Response speed hint from the probe, not a fabricated benchmark. Null means hide it. */
    val speedLabel: String?
        get() = when {
            latencyMs == null -> null
            latencyMs <= 350L -> "Fast"
            latencyMs <= 900L -> "Good"
            else -> "Slow"
        }

    val resumeLabel: String?
        get() = when (resumable) {
            true -> "Resume supported"
            false -> "No resume"
            null -> null
        }

    internal val qualityRank: Int
        get() = StreamContentFilter.detectQuality(stream)?.rank ?: -1

    internal val selectionKey: String
        get() = listOf(
            sourceLabel.lowercase(),
            qualityLabel.lowercase(),
            languageLabel.orEmpty().lowercase(),
            stream.behaviorHints.filename.orEmpty().lowercase(),
        ).joinToString("|")
}

internal object DownloadSourceResolver {
    private const val probeTimeoutMs = 4_000L
    private const val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun resolve(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): List<DownloadSourceOption> {
        val group = ZyloStreams.resolveDirectStreams(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
        )
        val expectedMeta = MetaDetailsRepository.peek(
            type = type,
            id = parentMetaId ?: videoId.substringBefore(":"),
        )
        val expectedTitle = expectedMeta?.name
        val expectedYear = expectedMeta?.releaseDate
            ?.trim()
            ?.take(4)
            ?.toIntOrNull()
            ?: Regex("""(?<!\d)((?:19|20)\d{2})(?!\d)""")
                .find(expectedMeta?.releaseInfo.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        val expectedImdbId = expectedMeta?.imdbId
        // The details screen already starts stream discovery for playback. Reuse whatever real
        // addon/plugin sources are loaded for this exact content so downloads match playback.
        val loadedStreams = runCatching {
            val expectedToken = StreamsRepository.requestToken(
                type = type,
                videoId = videoId,
                season = season,
                episode = episode,
                manualSelection = false,
            )
            val state = StreamsRepository.uiState.value
            if (state.requestToken == expectedToken) {
                state.groups.flatMap { it.streams }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val candidates = (group.streams + loadedStreams)
            .filter { stream ->
                StreamContentFilter.isIdentityCompatible(
                    stream = stream,
                    expectedTitle = expectedTitle,
                    expectedYear = expectedYear,
                    expectedImdbId = expectedImdbId,
                )
            }
            .filter { stream ->
                stream.playableDirectUrl?.isSupportedDownloadUrl() == true
            }
            .distinctBy { stream -> stream.playableDirectUrl?.trim() }

        val probed = coroutineScope {
            candidates.map { stream ->
                async { stream to probe(stream) }
            }.awaitAll()
        }

        return probed
            .map { (stream, probe) ->
                DownloadSourceOption(
                    stream = stream,
                    sourceLabel = StreamContentFilter.sourceLabel(stream),
                    qualityLabel = StreamContentFilter.detectQuality(stream)?.label ?: "Auto",
                    sizeBytes = probe.totalBytes ?: StreamContentFilter.parsedSizeBytes(stream),
                    languageLabel = StreamContentFilter.languageLabel(stream),
                    latencyMs = probe.latencyMs,
                    resumable = probe.resumable,
                )
            }
            .distinctBy(DownloadSourceOption::selectionKey)
            .sortedWith(
                compareByDescending<DownloadSourceOption> { it.resumable == true }
                    .thenBy { it.latencyMs ?: Long.MAX_VALUE }
                    .thenByDescending(DownloadSourceOption::qualityRank),
            )
    }

    private suspend fun probe(stream: StreamItem): SourceProbe {
        val url = stream.playableDirectUrl?.trim()?.takeIf { it.isNotBlank() } ?: return SourceProbe()
        val headers = buildMap {
            put("User-Agent", defaultUserAgent)
            put("Accept", "*/*")
            stream.behaviorHints.proxyHeaders?.request?.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) put(key, value)
            }
        }

        val started = TimeSource.Monotonic.markNow()
        val head = withTimeoutOrNull(probeTimeoutMs) {
            runCatching {
                httpRequestRaw(
                    method = "HEAD",
                    url = url,
                    headers = headers,
                    body = "",
                    maxResponseBodyBytes = 1,
                )
            }.getOrNull()
        }
        val headLatency = started.elapsedNow().inWholeMilliseconds

        if (head != null && head.status in 200..399) {
            val contentLength = head.headers.firstHeaderValue("content-length")?.toLongOrNull()
            return SourceProbe(
                latencyMs = headLatency,
                resumable = head.headers.supportsByteRanges(),
                totalBytes = contentLength?.takeIf { it > 0L },
            )
        }

        // Some CDNs reject HEAD. A tiny ranged GET still tells us whether resume works and
        // gives a real response-time hint.
        val rangeStarted = TimeSource.Monotonic.markNow()
        val range = withTimeoutOrNull(probeTimeoutMs) {
            runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers + ("Range" to "bytes=0-1"),
                    body = "",
                    maxResponseBodyBytes = 4 * 1024,
                )
            }.getOrNull()
        }
        val rangeLatency = rangeStarted.elapsedNow().inWholeMilliseconds
        if (range == null) return SourceProbe()

        val contentRange = range.headers.firstHeaderValue("content-range")
        val totalFromRange = contentRange
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.trim()
            ?.toLongOrNull()
        val contentLength = range.headers.firstHeaderValue("content-length")?.toLongOrNull()
        return SourceProbe(
            latencyMs = rangeLatency,
            resumable = range.status == 206 ||
                (range.status in 200..299 && range.headers.supportsByteRanges()),
            totalBytes = totalFromRange?.takeIf { it > 0L }
                ?: contentLength?.takeIf { it > 0L && range.status != 206 },
        )
    }

    private fun Map<String, String>.firstHeaderValue(name: String): String? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

    private fun Map<String, String>.supportsByteRanges(): Boolean =
        firstHeaderValue("accept-ranges")?.contains("bytes", ignoreCase = true) == true

    private data class SourceProbe(
        val latencyMs: Long? = null,
        val resumable: Boolean? = null,
        val totalBytes: Long? = null,
    )
}

internal fun formatDownloadSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gib -> "${formatOneDecimal(value / gib)} GB"
        value >= mib -> "${formatOneDecimal(value / mib)} MB"
        value >= kib -> "${formatOneDecimal(value / kib)} KB"
        else -> "$bytes B"
    }
}

private fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    val whole = tenths / 10
    val tenth = tenths % 10
    return if (tenth == 0) whole.toString() else "$whole.$tenth"
}
