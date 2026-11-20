package com.nuvio.app.features.streams

import com.nuvio.app.core.text.sanitizeBrandLabel
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

    /** Tokens that describe an encode/provider rather than the film or series title. */
    private val technicalTitleTokens = setOf(
        "web", "webdl", "webrip", "dl", "bluray", "brrip", "bdrip", "remux", "hdrip",
        "hdtv", "dvdrip", "uhd", "fhd", "qhd", "sd", "hdr", "hdr10", "dv", "dolby",
        "vision", "imax", "x264", "x265", "h264", "h265", "hevc", "avc", "av1",
        "aac", "ddp", "eac3", "ac3", "dts", "truehd", "atmos", "flac", "opus",
        "multi", "dual", "audio", "proper", "repack", "extended", "remastered", "unrated",
        "mkv", "mp4", "m4v", "avi", "ts", "nf", "netflix", "amzn", "amazon", "dsnp",
        "disney", "hmax", "max", "atvp", "pcok", "source", "direct", "stream",
        "http", "https", "www", "com", "net", "org", "xyz", "top", "cdn", "cloud",
        "video", "videos", "download", "extract", "url", "file", "files", "example",
        "master", "playlist", "manifest", "m3u8", "mpd",
    )

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

    /**
     * CAM/screener prints are always hidden. Edition-labelled files are allowed only when the
     * requested page is that exact edition; this keeps Encore off Endgame/Doomsday while still
     * allowing the dedicated Encore page to play its own files.
     */
    fun isBlockedPrint(stream: StreamItem, expectedTitle: String? = null): Boolean =
        isCamPrint(stream) || (isSpoofedRelease(stream) && !isEncoreEdition(expectedTitle))

    fun isSpoofedRelease(stream: StreamItem): Boolean {
        val text = streamPrintText(stream)
        if (text.isBlank()) return false
        return spoofedReleasePatterns.any { pattern -> pattern.containsMatchIn(text) }
    }

    /**
     * Full identity gate used before auto-play and direct-stream discovery. A stream must match
     * the requested title and must not advertise a different IMDb id or a conflicting year.
     */
    fun isIdentityCompatible(
        stream: StreamItem,
        expectedTitle: String?,
        expectedYear: Int? = null,
        expectedImdbId: String? = null,
    ): Boolean {
        if (isBlockedPrint(stream, expectedTitle)) return false
        if (!matchesExpectedTitle(stream, expectedTitle)) return false
        if (hasConflictingImdbId(stream, expectedImdbId)) return false
        if (expectedYear != null) {
            val actualYear = releaseYearOf(stream)
            if (actualYear != null && kotlin.math.abs(actualYear - expectedYear) > 1) return false
        }
        return true
    }

    private fun isEncoreEdition(title: String?): Boolean {
        val tokens = title?.let(::titleTokens).orEmpty()
        return "endgame" in tokens && "encore" in tokens
    }

    /** Rejects a stream whose URL/filename carries a different IMDb id than the requested title. */
    fun hasConflictingImdbId(stream: StreamItem, expectedImdbId: String?): Boolean {
        val expected = expectedImdbId?.trim()?.lowercase().orEmpty()
        if (expected.length < 9 || !expected.startsWith("tt")) return false
        val found = releaseIdentityTexts(stream)
            .joinToString(" ")
            .lowercase()
            .let { text -> Regex("""tt\d{6,}""").findAll(text).map { it.value }.toSet() }
        return found.isNotEmpty() && expected !in found
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

        val identities = releaseIdentityTexts(stream)
        val actualTokens = identities.flatMap(::titleTokens).toSet()
        // No usable release identity: keep the source instead of hiding a real stream.
        if (actualTokens.isEmpty()) return true
        if (expectedTokens.all(actualTokens::contains)) return true

        // Many Stremio-compatible addons expose only technical text (for example
        // "1080p WEB-DL x265") and an opaque URL. That is not evidence of a different title.
        // Reject only when a trusted file/title field carries a recognisable conflicting name.
        val hasConflictingTitle = releaseTitleCandidates(stream).any { candidate ->
            val semanticTokens = titleTokens(candidate).filterNot(::isTechnicalTitleToken).toSet()
            semanticTokens.isNotEmpty() && !expectedTokens.all(semanticTokens::contains)
        }
        return !hasConflictingTitle
    }

    private fun isTechnicalTitleToken(token: String): Boolean {
        if (token in technicalTitleTokens) return true
        if (token in knownLanguages || knownProviders.any { (providerToken, _) -> token == providerToken }) return true
        if (token.all(Char::isDigit)) return true
        if (token.matches(Regex("""(?:2160|1440|1080|720|576|480|360|240)[pi]?"""))) return true
        if (token.toIntOrNull()?.let { it in 1900..2099 } == true) return true
        if (token.matches(Regex("""\d+(?:bit|ch|gb|mb)"""))) return true
        // Signed CDN ids and hashes do not identify a title.
        if (token.length >= 10 && token.any(Char::isDigit) && token.any(Char::isLetter)) return true
        return false
    }

    private fun releaseTitleCandidates(stream: StreamItem): List<String> = buildList {
        fun addCandidate(value: String?) {
            value?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        addCandidate(stream.behaviorHints.filename)
        addCandidate(stream.clientResolve?.stream?.raw?.filename)
        addCandidate(stream.clientResolve?.stream?.raw?.torrentName)
        addCandidate(stream.clientResolve?.stream?.raw?.parsed?.rawTitle)
        addCandidate(stream.clientResolve?.stream?.raw?.parsed?.parsedTitle)
        addCandidate(stream.title)
        stream.url?.takeIf { it.isNotBlank() }?.let { rawUrl ->
            var decoded = rawUrl
            repeat(4) {
                addCandidate(decoded.fileNameFromUrl())
                val next = runCatching { decoded.decodeURLQueryComponent() }.getOrNull()
                    ?: return@repeat
                if (next == decoded) return@repeat
                decoded = next
            }
        }
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

    fun availableQualities(
        streams: List<StreamItem>,
        expectedTitle: String? = null,
    ): List<StreamPrintQuality> =
        streams.asSequence()
            .filterNot { stream -> isBlockedPrint(stream, expectedTitle) }
            .mapNotNull(::detectQuality)
            .distinct()
            .sortedByDescending { it.rank }
            .toList()

    /**
     * Lower is better when a user has an audio preference. 0 = explicitly carries the preferred
     * language, 1 = multi/unknown, 2 = explicitly a different language.
     */
    fun audioLanguageRank(stream: StreamItem, preferredAudioLanguage: String?): Int {
        val code = preferredAudioLanguage
            ?.trim()
            ?.lowercase()
            ?.substringBefore('-')
            .orEmpty()
        if (code.length != 2) return 0
        val preferredToken = languageCodeTokens[code] ?: return 0
        val text = streamPrintText(stream)
        val isMulti = "multi" in text || "dual audio" in text || "dual-audio" in text
        if (preferredToken in text) return 0
        val hasKnownLanguage = knownLanguages.keys.any(text::contains)
        return when {
            isMulti -> 1
            hasKnownLanguage -> 2
            else -> 1
        }
    }

    private val languageCodeTokens = mapOf(
        "hi" to "hindi",
        "en" to "english",
        "ta" to "tamil",
        "te" to "telugu",
        "ml" to "malayalam",
        "kn" to "kannada",
        "bn" to "bengali",
        "mr" to "marathi",
        "pa" to "punjabi",
        "gu" to "gujarati",
        "ur" to "urdu",
        "ko" to "korean",
        "ja" to "japanese",
        "zh" to "chinese",
        "es" to "spanish",
        "fr" to "french",
        "de" to "german",
        "ru" to "russian",
        "pt" to "portuguese",
        "it" to "italian",
    )

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
        return sanitizeBrandLabel(
            providerHost
                ?: stream.sourceName?.trim()?.takeIf { it.isNotBlank() && !it.equals("ZYLO Direct", ignoreCase = true) }
                ?: host.takeIf { it.isNotBlank() }
                ?: stream.addonName,
        )
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
