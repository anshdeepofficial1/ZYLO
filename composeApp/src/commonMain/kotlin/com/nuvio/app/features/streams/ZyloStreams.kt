package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.tmdb.TmdbService
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ZYLO's first-party discovery entry point.
 *
 * The provider response contains player-ready media URLs. Embed pages must never be copied into
 * [StreamItem.url]: ExoPlayer then receives HTML and reports an unrecognised file format. When no
 * direct result is available we return an empty group and let installed addons/plugins continue,
 * instead of advertising an HTML page as a video.
 */
object ZyloStreams {
    private const val addonName = "ZYLO Direct"
    private const val addonId = "zylo_servers"
    private const val requestTimeoutMs = 8_000L
    private const val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun resolveDirectStreams(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): AddonStreamGroup {
        val meta = MetaDetailsRepository.peek(
            type = type,
            id = parentMetaId ?: videoId.substringBefore(":"),
        )
        val rawId = videoId
            .removePrefix("tmdb:")
            .removePrefix("movie:")
            .removePrefix("series:")
            .substringBefore(":")

        var imdbId = rawId.takeIf { it.startsWith("tt", ignoreCase = true) } ?: meta?.imdbId
        var tmdbId = rawId.takeIf { it.all(Char::isDigit) }

        if (tmdbId == null && !imdbId.isNullOrBlank()) {
            tmdbId = TmdbService.ensureTmdbId(imdbId, type)
        }
        if (imdbId == null && !tmdbId.isNullOrBlank()) {
            imdbId = tmdbId.toIntOrNull()?.let { TmdbService.tmdbToImdb(it, type) }
        }

        val resolvedSeason = season ?: 1
        val resolvedEpisode = episode ?: 1
        val isSeries = type.equals("series", ignoreCase = true) ||
            type.equals("tv", ignoreCase = true) || season != null
        val streamType = if (isSeries) "series" else "movie"

        val queryIds = buildQueryIds(
            isSeries = isSeries,
            rawId = rawId,
            imdbId = imdbId,
            tmdbId = tmdbId,
            season = resolvedSeason,
            episode = resolvedEpisode,
        )

        var directStreams = emptyList<StreamItem>()
        for (queryId in queryIds) {
            val endpoint = "https://hd.plavio.site/stream/$streamType/$queryId.json"
            val response = withTimeoutOrNull(requestTimeoutMs) {
                runCatching {
                    httpRequestRaw(
                        method = "GET",
                        url = endpoint,
                        headers = mapOf(
                            "Accept" to "application/json",
                            "User-Agent" to defaultUserAgent,
                        ),
                        body = "",
                    )
                }.getOrNull()
            }
            if (response == null || response.status !in 200..299 || response.body.isBlank()) continue

            val parsed = runCatching {
                StreamParser.parse(response.body, addonName, addonId)
            }.getOrDefault(emptyList())
            directStreams = prepareDirectStreams(
                streams = parsed,
                expectedTitle = meta?.name,
                verifyTitle = !isSeries,
            )
            if (directStreams.isNotEmpty()) break
        }

        return AddonStreamGroup(
            addonName = addonName,
            addonId = addonId,
            streams = directStreams,
            isLoading = false,
        )
    }

    internal fun buildQueryIds(
        isSeries: Boolean,
        rawId: String,
        imdbId: String?,
        tmdbId: String?,
        season: Int,
        episode: Int,
    ): List<String> = buildList {
        fun addUnique(value: String?) {
            value?.takeIf { it.isNotBlank() && it !in this@buildList }?.let(::add)
        }
        if (isSeries) {
            addUnique(imdbId?.let { "$it:$season:$episode" })
            addUnique(tmdbId?.let { "tmdb:$it:$season:$episode" })
            addUnique(rawId.takeIf { it != imdbId && it != tmdbId }?.let { "$it:$season:$episode" })
        } else {
            addUnique(imdbId)
            addUnique(tmdbId?.let { "tmdb:$it" })
            addUnique(rawId.takeIf { it != imdbId && it != tmdbId })
        }
    }

    internal fun prepareDirectStreams(
        streams: List<StreamItem>,
        expectedTitle: String? = null,
        verifyTitle: Boolean = true,
    ): List<StreamItem> =
        streams
            .asSequence()
            .filter { stream -> stream.url.isDirectHttpUrl() }
            .filterNot(StreamContentFilter::isBlockedPrint)
            .filter { stream ->
                !verifyTitle || StreamContentFilter.matchesExpectedTitle(stream, expectedTitle)
            }
            .distinctBy { stream -> stream.url?.trim() }
            .sortedBy(::reliabilityScore)
            .mapIndexed { index, stream ->
                val providerKey = directProviderKey(stream, index)
                stream.copy(
                    externalUrl = null,
                    sourceName = StreamContentFilter.sourceLabel(stream),
                    addonName = addonName,
                    addonId = addonId,
                    streamType = stream.streamType ?: inferStreamType(stream),
                    behaviorHints = stream.behaviorHints.copy(
                        bingeGroup = stream.behaviorHints.bingeGroup
                            ?.takeIf { it.isNotBlank() }
                            ?: "zylo_direct|$providerKey",
                    ),
                )
            }
            .toList()

    private fun String?.isDirectHttpUrl(): Boolean {
        val value = this?.trim().orEmpty()
        return value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
    }

    private fun inferStreamType(stream: StreamItem): String? {
        val text = streamSearchText(stream)
        return when {
            ".m3u8" in text -> "application/x-mpegURL"
            ".mpd" in text -> "application/dash+xml"
            ".mp4" in text -> "video/mp4"
            ".mkv" in text -> "video/x-matroska"
            else -> null
        }
    }

    /** Lower is more likely to start quickly on both ExoPlayer and browser remux paths. */
    internal fun reliabilityScore(stream: StreamItem): Int {
        val text = streamSearchText(stream)
        var score = 100
        if ("1080p" in text) score -= 18
        if ("720p" in text) score -= 12
        if ("480p" in text) score -= 4
        if ("2160p" in text || "4k" in text) score += 28
        if (".mp4" in text) score -= 18
        if ("x264" in text || "h264" in text || "avc" in text) score -= 14
        if ("aac" in text) score -= 8
        if (".mkv" in text) score += 5
        if ("x265" in text || "h265" in text || "hevc" in text) score += 10
        if ("truehd" in text || " dts" in text || "atmos" in text) score += 8
        if (" hdr" in text || "dolby vision" in text || " dv " in text) score += 5
        score += parsedSizeGigabytes(text)?.let { size ->
            when {
                size <= 2.5 -> -8
                size <= 5.0 -> -4
                size >= 25.0 -> 18
                size >= 12.0 -> 10
                else -> 0
            }
        } ?: 0
        return score
    }

    private fun parsedSizeGigabytes(text: String): Double? =
        Regex("""(\d+(?:\.\d+)?)\s*gb\b""")
            .findAll(text)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .minOrNull()

    private fun streamSearchText(stream: StreamItem): String = listOfNotNull(
        stream.name,
        stream.title,
        stream.description,
        stream.behaviorHints.filename,
        stream.url,
    ).joinToString(" ").lowercase()

    private fun directProviderKey(stream: StreamItem, index: Int): String {
        val text = streamSearchText(stream)
        val knownProvider = listOf(
            "movieshunt",
            "movies4u",
            "4khdhub",
            "hdhub4u",
            "moviebox",
            "netmirror",
            "spacedom",
            "vidrock",
            "cinejoy",
            "vegamovies",
            "dexter",
        ).firstOrNull(text::contains)
        if (knownProvider != null) return knownProvider

        val nestedUrl = stream.url
            ?.substringAfter("url=", missingDelimiterValue = "")
            ?.substringBefore('&')
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded -> runCatching { encoded.decodeURLQueryComponent() }.getOrNull() }
        val host = hostOf(nestedUrl).ifBlank { hostOf(stream.url) }
        return host
            .removePrefix("www.")
            .substringBefore(':')
            .takeIf { it.isNotBlank() }
            ?: "source_$index"
    }

    private fun hostOf(url: String?): String = url
        ?.substringAfter("://", missingDelimiterValue = "")
        ?.substringBefore('/')
        ?.substringBefore('?')
        ?.lowercase()
        .orEmpty()
}
