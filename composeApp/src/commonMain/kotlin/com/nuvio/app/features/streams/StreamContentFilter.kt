package com.nuvio.app.features.streams

import io.ktor.http.decodeURLQueryComponent

/**
 * Content-level checks shared by direct stream discovery, addons, servers and downloads.
 *
 * The upstream direct API sometimes returns a different film under a requested id (for example a
 * spoofed unreleased title pointing at an older release). Labels are generated from the requested
 * metadata, so verification must use the release file identity instead of the display name.
 */
internal object StreamContentFilter {
    private val camPrintPattern = Regex(
        pattern = """(?<![a-z0-9])(cam|camrip|hdcam|hqcam|hdtc|hdts|ts|tc|telesync|telecine|predvd|pre-dvd|pdvd|dvdscr|screener|scr|r5)(?![a-z0-9])""",
        option = RegexOption.IGNORE_CASE,
    )

    /**
     * Known fake releases must never be offered, even when the requested title matches.
     * "Avengers Endgame Encore 2026" is a fabricated print served for multiple ids.
     */
    private val spoofedReleasePatterns = listOf(
        Regex(
            pattern = """(?<![a-z0-9])avengers[\s._-]*endgame[\s._-]*encore(?![a-z0-9])""",
            option = RegexOption.IGNORE_CASE,
        ),
        Regex(
            pattern = """(?<![a-z0-9])endgame[\s._-]*encore[\s._-]*(?:19|20)\d{2}(?![a-z0-9])""",
            option = RegexOption.IGNORE_CASE,
        ),
    )

    private val stopWords = setOf("the", "a", "an", "of", "and", "or", "to", "in", "on", "for")

    private val numberWords = mapOf(
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "nine" to "9",
        "ten" to "10",
    )

    private val knownLanguages = linkedMapOf(
        "hindi" to "Hindi",
        "english" to "English",
        "tamil" to "Tamil",
        "telugu" to "Telugu",
        "malayalam" to "Malayalam",
        "kannada" to "Kannada",
        "bengali" to "Bengali",
        "marathi" to "Marathi",
        "punjabi" to "Punjabi",
        "gujarati" to "Gujarati",
        "urdu" to "Urdu",
        "korean" to "Korean",
        "japanese" to "Japanese",
        "chinese" to "Chinese",
        "spanish" to "Spanish",
        "french" to "French",
        "german" to "German",
        "russian" to "Russian",
        "portuguese" to "Portuguese",
        "italian" to "Italian",
    )

    /**
     * CAM / telesync / screener prints are never offered anywhere in the app, including
     * downloads and auto-play.
     */
    fun isCamPrint(stream: StreamItem): Boolean {
        val text = streamPrintText(stream)
        if (text.isBlank()) return false
        return camPrintPattern.containsMatchIn(text)
    }

    /** CAM/screener prints and known fake releases are hidden everywhere. */
    fun isBlockedPrint(stream: StreamItem): Boolean = isCamPrint(stream) || isSpoofedRelease(stream)

    fun isSpoofedRelease(stream: StreamItem): Boolean {
        val text = streamPrintText(stream)
        if (text.isBlank()) return false
        return spoofedReleasePatterns.any { pattern -> pattern.containsMatchIn(text) }
    }

    /**
     * Checks that the actual release file mentions every meaningful word of the requested title.
     *
     * Spoofed API entries carry the requested title in `name`/`description` but the real filename
     * in `behaviorHints.filename`, `title` or the URL. Only release identity fields are trusted
     * here; display labels are ignored. Any identity field matching the requested title is enough,
     * so vague filenames ("BLURAY X264 ...") do not hide a real source whose URL carries the name.
     */
    fun matchesExpectedTitle(stream: StreamItem, expectedTitle: String?): Boolean {
        val requested = expectedTitle?.trim().orEmpty()
        if (requested.isBlank()) return true
        // Localized/transliterated titles cannot be compared with a Latin release filename.
        if (requested.none { it.lowercaseChar() in 'a'..'z' }) return true

        val expectedTokens = titleTokens(requested)
        if (expectedTokens.isEmpty()) return true

        val actualTokens = releaseIdentityTexts(stream)
            .flatMap(::titleTokens)
            .toSet()
        // No usable release identity: keep the source instead of hiding a real stream.
        if (actualTokens.isEmpty()) return true
        return expectedTokens.all(actualTokens::contains)
    }

    /**
     * Release identity fields only. `name` is a generated display label and `description` can be
     * copied from the requested metadata, so neither can prove which film a file contains.
     */
    fun releaseIdentityTexts(stream: StreamItem): List<String> = buildList {
        fun addIdentity(value: String?) {
            value?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        addIdentity(stream.behaviorHints.filename)
        addIdentity(stream.clientResolve?.stream?.raw?.filename)
        addIdentity(stream.clientResolve?.stream?.raw?.torrentName)
        addIdentity(stream.title)
        stream.url?.takeIf { it.isNotBlank() }?.let { url ->
            addIdentity(url)
            addIdentity(url.fileNameFromUrl())
            // Provider links are often encoded twice (`%2520`), so decode until it settles.
            var decoded = url
            var pass = 0
            while (pass < 3) {
                val next = runCatching { decoded.decodeURLQueryComponent() }.getOrNull() ?: break
                if (next == decoded) break
                addIdentity(next)
                decoded = next
                pass++
            }
        }
    }

    /** Release year advertised by the file, when one is present. */
    fun releaseYearOf(stream: StreamItem): Int? =
        Regex("""(?<!\d)((?:19|20)\d{2})(?!\d)""")
            .findAll(streamPrintText(stream))
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()

    /** The actual file/release name, preferring the most trustworthy field. */
    fun contentIdentityText(stream: StreamItem): String? =
        releaseIdentityTexts(stream).firstOrNull()

    /** All text used to recognise a release, including file names embedded inside URLs. */
    fun streamPrintText(stream: StreamItem): String = buildString {
        releaseIdentityTexts(stream).forEach { identity -> append(identity).append(' ') }
        stream.description?.let { append(it).append(' ') }
        stream.url?.substringBefore('?')?.let { append(it).append(' ') }
        stream.name?.let { append(it).append(' ') }
    }.lowercase()

    fun detectQuality(stream: StreamItem): StreamPrintQuality? {
        val primary = listOfNotNull(
            stream.behaviorHints.filename,
            stream.clientResolve?.stream?.raw?.filename,
            stream.title,
            stream.url?.fileNameFromUrl(),
        ).joinToString(" ")
        val secondary = listOfNotNull(stream.name, stream.description).joinToString(" ")
        return detectQualityInText(primary) ?: detectQualityInText(secondary)
    }

    fun availableQualities(streams: List<StreamItem>): List<StreamPrintQuality> =
        streams.asSequence()
            .filterNot(::isBlockedPrint)
            .mapNotNull(::detectQuality)
            .distinct()
            .sortedByDescending { it.rank }
            .toList()

    fun parsedSizeBytes(stream: StreamItem): Long? =
        stream.behaviorHints.videoSize?.takeIf { it > 0L }
            ?: parsedSizeBytes(streamPrintText(stream))

    fun parsedSizeBytes(text: String): Long? {
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(gb|gib|mb|mib)\b""", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val value = match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues.getOrNull(2)?.lowercase()) {
            "mb", "mib" -> 1024L * 1024L
            else -> 1024L * 1024L * 1024L
        }
        return (value * multiplier).toLong().takeIf { it > 0L }
    }

    fun languageLabel(stream: StreamItem): String? {
        val text = streamPrintText(stream)
        val found = knownLanguages.entries
            .filter { (token, _) -> text.contains(token) }
            .map { it.value }
            .distinct()
        val isMulti = "multi" in text || "dual audio" in text || "dual-audio" in text
        return when {
            isMulti && found.isNotEmpty() -> "Multi (${found.joinToString(", ")})"
            isMulti -> "Multi-audio"
            found.isNotEmpty() -> found.joinToString(", ")
            else -> null
        }
    }

    /** Human-readable real source name, e.g. HDHub4u or Dexter. */
    fun sourceLabel(stream: StreamItem): String {
        val text = streamPrintText(stream)
        knownProviders.firstOrNull { (token, _) -> text.contains(token) }?.let { return it.second }
        val host = stream.url?.hostFromUrl().orEmpty()
        val providerHost = knownProviderHosts[host]
        return providerHost
            ?: stream.sourceName?.trim()?.takeIf { it.isNotBlank() && !it.equals("ZYLO Direct", ignoreCase = true) }
            ?: host.takeIf { it.isNotBlank() }
            ?: stream.addonName
    }

    private fun detectQualityInText(text: String): StreamPrintQuality? {
        if (text.isBlank()) return null
        val normalized = text.lowercase()
        return when {
            Regex("""\b(2160p|4k|uhd)\b""").containsMatchIn(normalized) -> StreamPrintQuality.UHD
            Regex("""\b(1440p|2k|qhd)\b""").containsMatchIn(normalized) -> StreamPrintQuality.QHD
            Regex("""\b(1080p|1080i|fhd)\b""").containsMatchIn(normalized) -> StreamPrintQuality.FHD
            Regex("""\b720p\b""").containsMatchIn(normalized) -> StreamPrintQuality.HD
            Regex("""\b(480p|360p|240p|sd)\b""").containsMatchIn(normalized) -> StreamPrintQuality.SD
            else -> null
        }
    }

    private fun titleTokens(value: String): Set<String> =
        value
            .lowercase()
            .replace('&', ' ')
            .split(Regex("[^a-z0-9]+"))
            .mapNotNull { token ->
                token.takeIf { it.isNotBlank() }
                    ?.takeUnless { it in stopWords }
                    ?.let { numberWords[it] ?: it }
            }
            .toSet()

    private val knownProviders = listOf(
        "hdhub4u" to "HDHub4u",
        "4khdhub" to "4KHDHub",
        "movieshunt" to "MoviesHunt",
        "movies4u" to "Movies4u",
        "moviebox" to "MovieBox",
        "netmirror" to "NetMirror",
        "spacedom" to "SpaceDom",
        "vidrock" to "VidRock",
        "cinejoy" to "CineJoy",
        "vegamovies" to "VegaMovies",
        "dexter" to "Dexter",
        "pixeldrain" to "PixelDrain",
        "sorastream" to "SoraStream",
        "hubcloud" to "HubCloud",
    )

    private val knownProviderHosts = mapOf(
        "pixeldrain.com" to "PixelDrain",
        "lumenbox.top" to "LumenBox",
        "r2.cloudflarestorage.com" to "Cloudflare R2",
        "hubcloud.xyz" to "HubCloud",
    )

    private fun String.fileNameFromUrl(): String =
        substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .takeIf { it.isNotBlank() }
            .orEmpty()

    private fun String.hostFromUrl(): String =
        substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('@')
            .substringBefore(':')
            .removePrefix("www.")
            .lowercase()
}

internal enum class StreamPrintQuality(val label: String, val rank: Int) {
    UHD(label = "4K", rank = 4),
    QHD(label = "2K", rank = 3),
    FHD(label = "1080p", rank = 2),
    HD(label = "720p", rank = 1),
    SD(label = "480p", rank = 0),
}

/** True for the player's own P2P loopback server; such URLs are session-local and never reusable. */
internal fun String.isLocalLoopbackPlaybackUrl(): Boolean {
    val authority = substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfter('@')
        .lowercase()
    if (authority.isBlank()) return false
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']') + "]"
    } else {
        authority.substringBefore(':')
    }
    return host in setOf("127.0.0.1", "localhost", "0.0.0.0", "[::1]", "::1")
}
