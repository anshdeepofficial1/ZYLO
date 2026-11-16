package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.withTimeoutOrNull

object ZyloStreams {

    data class ServerDefinition(
        val id: String,
        val name: String,
        val titleFormat: String,
        val description: String,
        val referer: String,
        val isMultiLang: Boolean = false,
        val isHindi: Boolean = false,
        val isCloud: Boolean = false,
    )

    val SERVERS: List<ServerDefinition> = listOf(
        ServerDefinition(
            id = "vid_stpm",
            name = "VidLink - [Multi-Lang]",
            titleFormat = "VidLink [Multi-Lang] • 1080p Ultra Fast Direct",
            description = "Multi-Audio (Hindi • English • Tamil • Telugu) • CloudFront CDN",
            referer = "https://vidlink.pro/",
            isMultiLang = true,
        ),
        ServerDefinition(
            id = "vixo",
            name = "VidSrc - [Multi-Lang]",
            titleFormat = "VidSrc [Multi-Lang] • 1080p High Speed Cluster",
            description = "VidSrc Dedicated Global Streaming Engine",
            referer = "https://vidsrc.xyz/",
            isMultiLang = true,
        ),
        ServerDefinition(
            id = "vid_pro",
            name = "VidSrc Pro",
            titleFormat = "VidSrc Pro • 1080p Dedicated HD Node",
            description = "High-Bandwidth Multi-Source Video Network",
            referer = "https://vidsrc.pro/",
        ),
        ServerDefinition(
            id = "vid_mv",
            name = "AutoEmbed - [Multi-Lang]",
            titleFormat = "AutoEmbed [Multi-Lang] • 1080p Adaptive HLS",
            description = "Fast Multi-Language Direct Server",
            referer = "https://autoembed.cc/",
            isMultiLang = true,
        ),
        ServerDefinition(
            id = "vid_fk",
            name = "SmashyStream",
            titleFormat = "SmashyStream • 1080p / 720p Clean Direct",
            description = "SmashyStream Low-Latency Edge Cluster",
            referer = "https://embed.smashystream.com/",
        ),
        ServerDefinition(
            id = "prvibd",
            name = "MultiEmbed",
            titleFormat = "MultiEmbed • 1080p Multi-Source Stream",
            description = "MultiEmbed High-Bandwidth Cluster",
            referer = "https://multiembed.mov/",
        ),
        ServerDefinition(
            id = "vid_emd",
            name = "2Embed",
            titleFormat = "2Embed • 1080p Adaptive Stream Engine",
            description = "2Embed Edge Video Network",
            referer = "https://2embed.cc/",
        ),
        ServerDefinition(
            id = "vid_emt",
            name = "2Embed Stream",
            titleFormat = "2Embed Stream • 1080p Direct Node",
            description = "2Embed Stream Dedicated Cluster",
            referer = "https://www.2embed.stream/",
        ),
        ServerDefinition(
            id = "embed_su",
            name = "EmbedSu",
            titleFormat = "EmbedSu • 1080p Fast Direct Stream",
            description = "EmbedSu Bufferless Edge Server",
            referer = "https://embed.su/",
        ),
        ServerDefinition(
            id = "super_embed",
            name = "SuperEmbed",
            titleFormat = "SuperEmbed • 1080p Global Edge Network",
            description = "SuperEmbed High-Performance Cluster",
            referer = "https://superembed.stream/",
        ),
        ServerDefinition(
            id = "ophm",
            name = "Ophm",
            titleFormat = "Ophm • OpenHLS Master Stream",
            description = "Ophim Adaptive Media Network",
            referer = "https://ophim1.com/",
        ),
        ServerDefinition(
            id = "show_nitro",
            name = "Cloud Febbox",
            titleFormat = "Febbox • 4K / 1080p Cloud Drive",
            description = "Febbox Cloud Cache • Add Febbox cookie in settings for 4K",
            referer = "https://www.febbox.com/",
            isCloud = true,
        ),
    )

    suspend fun resolveDirectStreams(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): AddonStreamGroup {
        val meta = MetaDetailsRepository.peek(type = type, id = parentMetaId ?: videoId.substringBefore(":"))
        val rawId = videoId.removePrefix("tmdb:").removePrefix("movie:").removePrefix("series:").substringBefore(":")
        
        var imdbId = if (rawId.startsWith("tt", ignoreCase = true)) rawId else meta?.imdbId
        var tmdbId = if (rawId.all { it.isDigit() }) rawId else null

        if (tmdbId == null && imdbId != null) {
            tmdbId = TmdbService.ensureTmdbId(imdbId, type)
        }
        if (imdbId == null && tmdbId != null && tmdbId.all { it.isDigit() }) {
            imdbId = tmdbId.toIntOrNull()?.let { TmdbService.tmdbToImdb(it, type) }
        }

        val s = season ?: 1
        val e = episode ?: 1
        val isTv = type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true) || season != null
        val streamType = if (isTv) "series" else "movie"

        // Candidate query IDs to try on direct extractors (PlavioHD / NetMirror / MovieBox)
        val queryIds = mutableListOf<String>()
        if (isTv) {
            if (!imdbId.isNullOrBlank()) queryIds.add("$imdbId:$s:$e")
            if (!tmdbId.isNullOrBlank()) queryIds.add("tmdb:$tmdbId:$s:$e")
            if (rawId.isNotBlank() && rawId != imdbId && rawId != tmdbId) queryIds.add("$rawId:$s:$e")
        } else {
            if (!imdbId.isNullOrBlank()) queryIds.add(imdbId)
            if (!tmdbId.isNullOrBlank()) queryIds.add("tmdb:$tmdbId")
            if (rawId.isNotBlank() && rawId != imdbId && rawId != tmdbId) queryIds.add(rawId)
        }

        var fetchedStreams: List<StreamItem> = emptyList()

        for (qid in queryIds) {
            val url = "https://hd.plavio.site/stream/$streamType/$qid.json"
            val response = withTimeoutOrNull(4500L) {
                runCatching {
                    httpRequestRaw(
                        method = "GET",
                        url = url,
                        headers = mapOf(
                            "Accept" to "application/json",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        ),
                        body = "",
                    )
                }.getOrNull()
            }
            if (response != null && response.status in 200..299 && response.body.isNotBlank()) {
                val parsed = StreamParser.parse(response.body, "ZYLO Servers", "zylo_servers")
                val validDirect = parsed.filter { it.url != null && it.url.isNotBlank() }
                if (validDirect.isNotEmpty()) {
                    fetchedStreams = validDirect
                    break
                }
            }
        }

        val items = mutableListOf<StreamItem>()
        val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        SERVERS.forEachIndexed { index, server ->
            val directStream = fetchedStreams.getOrNull(index)
            val fallbackUrl = buildServerUrl(server, isTv, tmdbId, imdbId, s, e)
            val streamUrl = directStream?.url?.takeIf { it.isNotBlank() } ?: fallbackUrl

            val title = if (directStream != null) {
                "${server.name} • ${directStream.title ?: directStream.name ?: "1080p Fast Direct"}"
            } else {
                server.titleFormat
            }

            val desc = if (directStream?.description?.isNotBlank() == true) {
                "${server.description} • ${directStream.description}"
            } else {
                server.description
            }

            items.add(
                StreamItem(
                    name = server.name,
                    title = title,
                    description = desc,
                    url = streamUrl,
                    externalUrl = fallbackUrl,
                    sourceName = "ZYLO Servers",
                    addonName = "ZYLO Servers",
                    addonId = "zylo_servers",
                    streamType = "direct",
                    behaviorHints = StreamBehaviorHints(
                        bingeGroup = "zylo_servers|${server.id}",
                        proxyHeaders = StreamProxyHeaders(
                            request = mapOf(
                                "User-Agent" to defaultUserAgent,
                                "Referer" to server.referer,
                            )
                        ),
                    ),
                )
            )
        }

        return AddonStreamGroup(
            addonName = "ZYLO Servers",
            addonId = "zylo_servers",
            streams = items,
            isLoading = false,
        )
    }

    private fun buildServerUrl(
        server: ServerDefinition,
        isTv: Boolean,
        tmdbId: String?,
        imdbId: String?,
        season: Int,
        episode: Int,
    ): String {
        val effectiveId = tmdbId ?: imdbId ?: "0"
        return if (isTv) {
            when (server.id) {
                "vid_stpm" -> "https://vidlink.pro/tv/$effectiveId/$season/$episode"
                "vixo" -> "https://vidsrc.xyz/embed/tv?tmdb=$effectiveId&season=$season&episode=$episode"
                "vid_pro" -> "https://vidsrc.pro/embed/tv/$effectiveId/$season/$episode"
                "vid_mv" -> "https://autoembed.cc/embed/tv/$effectiveId/$season/$episode"
                "vid_fk" -> "https://embed.smashystream.com/playere.php?tmdb=$effectiveId&season=$season&episode=$episode"
                "prvibd" -> "https://multiembed.mov/?video_id=$effectiveId&tmdb=1&s=$season&e=$episode"
                "vid_emd" -> if (!imdbId.isNullOrBlank()) "https://www.2embed.cc/embedtv/$imdbId&s=$season&e=$episode" else "https://www.2embed.cc/embedtv/$effectiveId&s=$season&e=$episode"
                "vid_emt" -> "https://www.2embed.stream/embed/tv/$effectiveId/$season/$episode"
                "embed_su" -> "https://embed.su/embed/tv/$effectiveId/$season/$episode"
                "super_embed" -> "https://multiembed.mov/?video_id=$effectiveId&tmdb=1&s=$season&e=$episode"
                "ophm" -> "https://ophim1.com/tv/$effectiveId/$season/$episode"
                "show_nitro" -> "https://febbox.com/embed/tv/$effectiveId/$season/$episode"
                else -> "https://vidlink.pro/tv/$effectiveId/$season/$episode"
            }
        } else {
            when (server.id) {
                "vid_stpm" -> "https://vidlink.pro/movie/$effectiveId"
                "vixo" -> "https://vidsrc.xyz/embed/movie?tmdb=$effectiveId"
                "vid_pro" -> "https://vidsrc.pro/embed/movie/$effectiveId"
                "vid_mv" -> "https://autoembed.cc/embed/movie/$effectiveId"
                "vid_fk" -> "https://embed.smashystream.com/playere.php?tmdb=$effectiveId"
                "prvibd" -> "https://multiembed.mov/?video_id=$effectiveId&tmdb=1"
                "vid_emd" -> if (!imdbId.isNullOrBlank()) "https://www.2embed.cc/embed/$imdbId" else "https://www.2embed.cc/embed/$effectiveId"
                "vid_emt" -> "https://www.2embed.stream/embed/movie/$effectiveId"
                "embed_su" -> "https://embed.su/embed/movie/$effectiveId"
                "super_embed" -> "https://multiembed.mov/?video_id=$effectiveId&tmdb=1"
                "ophm" -> "https://ophim1.com/movie/$effectiveId"
                "show_nitro" -> "https://febbox.com/embed/movie/$effectiveId"
                else -> "https://vidlink.pro/movie/$effectiveId"
            }
        }
    }
}
