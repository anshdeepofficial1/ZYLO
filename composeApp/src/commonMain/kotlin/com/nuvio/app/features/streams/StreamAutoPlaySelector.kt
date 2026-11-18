package com.nuvio.app.features.streams

import com.nuvio.app.core.build.AppFeaturePolicy

object StreamAutoPlaySelector {

    fun orderAddonStreams(
        groups: List<AddonStreamGroup>,
        installedOrder: List<String>,
        preferredGroupIds: Set<String> = emptySet(),
    ): List<AddonStreamGroup> {
        if (groups.isEmpty()) return groups
        if (preferredGroupIds.isNotEmpty()) {
            val (preferred, rest) = groups.partition { it.addonId in preferredGroupIds }
            return preferred + orderAddonStreams(rest, installedOrder)
        }

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = groups.partition { group ->
            group.addonId.startsWith("debrid:") ||
                group.streams.any { stream -> stream.isAddonDebridCandidate && stream.isDirectDebridStream }
        }
        if (installedOrder.isEmpty()) return directDebridEntries + remainingEntries

        val (addonEntries, pluginEntries) = remainingEntries.partition { group ->
            group.addonName in addonRankByName
        }
        val orderedAddons = addonEntries.sortedBy { group ->
            addonRankByName.getValue(group.addonName)
        }
        return directDebridEntries + orderedAddons + pluginEntries
    }

    fun selectAutoPlayStream(
        streams: List<StreamItem>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        debridEnabled: Boolean = true,
        activeResolverProviderId: String? = null,
    ): StreamItem? =
        evaluateAutoPlayStream(
            streams = streams,
            mode = mode,
            regexPattern = regexPattern,
            source = source,
            installedAddonNames = installedAddonNames,
            selectedAddons = selectedAddons,
            selectedPlugins = selectedPlugins,
            preferredBingeGroup = preferredBingeGroup,
            preferBingeGroupInSelection = preferBingeGroupInSelection,
            bingeGroupOnly = bingeGroupOnly,
            debridEnabled = debridEnabled,
            activeResolverProviderId = activeResolverProviderId,
        ).stream

    fun evaluateAutoPlayStream(
        streams: List<StreamItem>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        debridEnabled: Boolean = true,
        activeResolverProviderId: String? = null,
    ): StreamAutoPlayEvaluation {
        if (streams.isEmpty()) return StreamAutoPlayEvaluation()

        val sourceScopedStreams = when (source) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.serverTarget != null || it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (stream.serverTarget != null) {
                true
            } else if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return StreamAutoPlayEvaluation()
        if (mode == StreamAutoPlayMode.MANUAL && !bingeGroupOnly) {
            return StreamAutoPlayEvaluation()
        }

        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        val bingeGroupCandidates = if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            candidateStreams.filter { stream -> stream.behaviorHints.bingeGroup == targetBingeGroup }
        } else {
            emptyList()
        }
        val preferredReadyStream = bingeGroupCandidates.firstOrNull { stream ->
            stream.isAutoPlayable(debridEnabled, activeResolverProviderId)
        }
        if (bingeGroupOnly) {
            val readyStreams = preferredReadyStream?.let(::listOf).orEmpty()
            return StreamAutoPlayEvaluation(
                stream = preferredReadyStream,
                readyStreams = readyStreams,
                hasPendingDebridCandidate = preferredReadyStream == null &&
                    bingeGroupCandidates.any {
                        it.isPendingDebridAutoPlay(debridEnabled, activeResolverProviderId)
                    },
            )
        }
        if (mode == StreamAutoPlayMode.MANUAL) {
            return StreamAutoPlayEvaluation()
        }
        val preferredStream = if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            candidateStreams.firstOrNull { stream ->
                stream.behaviorHints.bingeGroup == targetBingeGroup &&
                    stream.isAutoPlayable(debridEnabled, activeResolverProviderId)
            }
        } else {
            null
        }
        val matchingStreams = when (mode) {
            StreamAutoPlayMode.MANUAL -> emptyList()
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()

                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                    ?: return StreamAutoPlayEvaluation()

                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex(
                        "\\b(${exclusionWords.joinToString("|") { Regex.escape(it) }})\\b",
                        RegexOption.IGNORE_CASE,
                    )
                } else null

                candidateStreams.filter { stream ->
                    val url = stream.playableDirectUrl.orEmpty()

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.streamLabel).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(url)
                    }

                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }
            }
        }
        if (matchingStreams.isEmpty() && preferredStream == null) return StreamAutoPlayEvaluation()

        val readyStreams = buildList {
            preferredStream?.let(::add)
            matchingStreams
                .filter { it.isAutoPlayable(debridEnabled, activeResolverProviderId) }
                .filterNot { it == preferredStream }
                .forEach(::add)
        }
        // Direct URLs start instantly; among torrents, the best-seeded copy buffers the least.
        val orderedReadyStreams = readyStreams.sortedBy { it.autoPlaySelectionRank() }
        val selected = orderedReadyStreams.firstOrNull()
        if (selected != null) {
            return StreamAutoPlayEvaluation(
                stream = selected,
                readyStreams = orderedReadyStreams,
            )
        }

        return StreamAutoPlayEvaluation(
            readyStreams = readyStreams,
            hasPendingDebridCandidate = matchingStreams.any {
                it.isPendingDebridAutoPlay(debridEnabled, activeResolverProviderId)
            },
        )
    }

    private fun StreamItem.isAutoPlayable(
        debridEnabled: Boolean,
        activeResolverProviderId: String?,
    ): Boolean =
        playableDirectUrl != null ||
            serverTarget != null ||
            (
                AppFeaturePolicy.p2pEnabled &&
                    needsLocalDebridResolve &&
                    p2pInfoHash != null &&
                    !isPendingDebridAutoPlay(debridEnabled, activeResolverProviderId)
            ) ||
            (debridEnabled && isAddonDebridCandidate && isReadyDebridAutoPlay(activeResolverProviderId))

    /**
     * Lower is picked first. Streams the player can open directly start instantly; torrent
     * copies are ordered by how many peers the addon reported, so autoplay does not land on a
     * dead torrent while healthy copies are available.
     */
    private fun StreamItem.autoPlaySelectionRank(): Int =
        when {
            playableDirectUrl != null || serverTarget != null -> 0
            isTorrentStream || needsLocalDebridResolve -> when (parsedSeederCount()) {
                in 100..Int.MAX_VALUE -> 1
                in 10..99 -> 2
                in 1..9 -> 3
                else -> 4
            }
            else -> 5
        }

    /** Seeders the addon advertises in the stream text, e.g. "👤 128" or "128 seeders". */
    private fun StreamItem.parsedSeederCount(): Int {
        val text = listOfNotNull(name, title, description).joinToString(" ")
        val match = Regex("""(\d+)\s*(?:👤|seeders?)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun StreamItem.isReadyDebridAutoPlay(activeResolverProviderId: String?): Boolean =
        when {
            isDirectDebridStream -> clientResolve?.service.matchesResolver(activeResolverProviderId)
            isCachedDebridTorrentStream -> debridCacheStatus?.providerId.matchesResolver(activeResolverProviderId)
            else -> false
        }

    private fun StreamItem.isPendingDebridAutoPlay(
        debridEnabled: Boolean,
        activeResolverProviderId: String?,
    ): Boolean {
        if (!debridEnabled || !isInstalledAddonStream || !needsLocalDebridResolve) return false
        if (!debridCacheStatus?.providerId.matchesResolver(activeResolverProviderId)) return false
        val state = debridCacheStatus?.state
        return state == null || state == StreamDebridCacheState.CHECKING
    }

    private fun String?.matchesResolver(activeResolverProviderId: String?): Boolean {
        val active = activeResolverProviderId?.trim().orEmpty()
        return active.isBlank() || this == null || equals(active, ignoreCase = true)
    }
}

data class StreamAutoPlayEvaluation(
    val stream: StreamItem? = null,
    val readyStreams: List<StreamItem> = emptyList(),
    val hasPendingDebridCandidate: Boolean = false,
)
