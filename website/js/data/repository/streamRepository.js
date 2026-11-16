import { secureMediaUrl } from "../../core/streams/secureMediaUrl.js";
import { safeApiCall } from "../../core/network/safeApiCall.js";
import { addonRepository } from "./addonRepository.js";
import { StreamApi } from "../remote/api/streamApi.js";
import { MetaApi } from "../remote/api/metaApi.js";
import { PluginManager } from "../../core/player/pluginManager.js";
import {
  cleanLocalPluginVideoId,
  isLocalPluginVideoId,
  resolvePluginSeasonEpisode
} from "../../core/player/pluginModels.js";
import { PLUGIN_LIMITS } from "../../core/player/pluginWorkerRuntime.js";
import { TmdbService } from "../../core/tmdb/tmdbService.js";
import { LocalDebridAvailabilityService } from "../../core/debrid/localDebridAvailabilityService.js";
import { DebridStreamPresentation } from "../../core/debrid/directDebridStreamPresentation.js";
import {
  ZYLO_DIRECT_ADDON,
  buildZyloDirectQueryIds,
  prepareZyloDirectStreams
} from "./zyloDirectStreams.js";

/* Legacy embed-page definitions deliberately retained as migration documentation only.
 * These URLs return HTML players, not media, and must never become stream.url values.
const ZYLO_SERVERS = [
  {
    id: "vid_stpm",
    name: "VidLink - [Multi-Lang]",
    titleFormat: "VidLink [Multi-Lang] • 1080p Ultra Fast Direct",
    description: "Multi-Audio (Hindi • English • Tamil • Telugu) • CloudFront CDN",
    referer: "https://vidlink.pro/",
    isMultiLang: true
  },
  {
    id: "vixo",
    name: "VidSrc - [Multi-Lang]",
    titleFormat: "VidSrc [Multi-Lang] • 1080p High Speed Cluster",
    description: "VidSrc Dedicated Global Streaming Engine",
    referer: "https://vidsrc.xyz/",
    isMultiLang: true
  },
  {
    id: "vid_pro",
    name: "VidSrc Pro",
    titleFormat: "VidSrc Pro • 1080p Dedicated HD Node",
    description: "High-Bandwidth Multi-Source Video Network",
    referer: "https://vidsrc.pro/"
  },
  {
    id: "vid_mv",
    name: "AutoEmbed - [Multi-Lang]",
    titleFormat: "AutoEmbed [Multi-Lang] • 1080p Adaptive HLS",
    description: "Fast Multi-Language Direct Server",
    referer: "https://autoembed.cc/",
    isMultiLang: true
  },
  {
    id: "vid_fk",
    name: "SmashyStream",
    titleFormat: "SmashyStream • 1080p / 720p Clean Direct",
    description: "SmashyStream Low-Latency Edge Cluster",
    referer: "https://embed.smashystream.com/"
  },
  {
    id: "prvibd",
    name: "MultiEmbed",
    titleFormat: "MultiEmbed • 1080p Multi-Source Stream",
    description: "MultiEmbed High-Bandwidth Cluster",
    referer: "https://multiembed.mov/"
  },
  {
    id: "vid_emd",
    name: "2Embed",
    titleFormat: "2Embed • 1080p Adaptive Stream Engine",
    description: "2Embed Edge Video Network",
    referer: "https://2embed.cc/"
  },
  {
    id: "vid_emt",
    name: "2Embed Stream",
    titleFormat: "2Embed Stream • 1080p Direct Node",
    description: "2Embed Stream Dedicated Cluster",
    referer: "https://www.2embed.stream/"
  },
  {
    id: "embed_su",
    name: "EmbedSu",
    titleFormat: "EmbedSu • 1080p Fast Direct Stream",
    description: "EmbedSu Bufferless Edge Server",
    referer: "https://embed.su/"
  },
  {
    id: "super_embed",
    name: "SuperEmbed",
    titleFormat: "SuperEmbed • 1080p Global Edge Network",
    description: "SuperEmbed High-Performance Cluster",
    referer: "https://superembed.stream/"
  },
  {
    id: "ophm",
    name: "Ophim",
    titleFormat: "Ophim • OpenHLS Master Stream",
    description: "Ophim Adaptive Media Network",
    referer: "https://ophim1.com/"
  },
  {
    id: "show_nitro",
    name: "Cloud Febbox",
    titleFormat: "Febbox • 4K / 1080p Cloud Drive",
    description: "Febbox Cloud Cache • Add Febbox cookie in settings for 4K",
    referer: "https://www.febbox.com/",
    isCloud: true
  }
];

function buildZyloServerUrl(server, isTv, tmdbId, imdbId, season, episode) {
  const effectiveId = tmdbId || imdbId || "0";
  const s = season || 1;
  const e = episode || 1;
  if (isTv) {
    switch (server.id) {
      case "vid_stpm": return `https://vidlink.pro/tv/${effectiveId}/${s}/${e}`;
      case "vixo": return `https://vidsrc.xyz/embed/tv?tmdb=${effectiveId}&season=${s}&episode=${e}`;
      case "vid_pro": return `https://vidsrc.pro/embed/tv/${effectiveId}/${s}/${e}`;
      case "vid_mv": return `https://autoembed.cc/embed/tv/${effectiveId}/${s}/${e}`;
      case "vid_fk": return `https://embed.smashystream.com/playere.php?tmdb=${effectiveId}&season=${s}&episode=${e}`;
      case "prvibd": return `https://multiembed.mov/?video_id=${effectiveId}&tmdb=1&s=${s}&e=${e}`;
      case "vid_emd": return imdbId ? `https://www.2embed.cc/embedtv/${imdbId}&s=${s}&e=${e}` : `https://www.2embed.cc/embedtv/${effectiveId}&s=${s}&e=${e}`;
      case "vid_emt": return `https://www.2embed.stream/embed/tv/${effectiveId}/${s}/${e}`;
      case "embed_su": return `https://embed.su/embed/tv/${effectiveId}/${s}/${e}`;
      case "super_embed": return `https://multiembed.mov/?video_id=${effectiveId}&tmdb=1&s=${s}&e=${e}`;
      case "ophm": return `https://ophim1.com/tv/${effectiveId}/${s}/${e}`;
      case "show_nitro": return `https://febbox.com/embed/tv/${effectiveId}/${s}/${e}`;
      default: return `https://vidlink.pro/tv/${effectiveId}/${s}/${e}`;
    }
  } else {
    switch (server.id) {
      case "vid_stpm": return `https://vidlink.pro/movie/${effectiveId}`;
      case "vixo": return `https://vidsrc.xyz/embed/movie?tmdb=${effectiveId}`;
      case "vid_pro": return `https://vidsrc.pro/embed/movie/${effectiveId}`;
      case "vid_mv": return `https://autoembed.cc/embed/movie/${effectiveId}`;
      case "vid_fk": return `https://embed.smashystream.com/playere.php?tmdb=${effectiveId}`;
      case "prvibd": return `https://multiembed.mov/?video_id=${effectiveId}&tmdb=1`;
      case "vid_emd": return imdbId ? `https://www.2embed.cc/embed/${imdbId}` : `https://www.2embed.cc/embed/${effectiveId}`;
      case "vid_emt": return `https://www.2embed.stream/embed/movie/${effectiveId}`;
      case "embed_su": return `https://embed.su/embed/movie/${effectiveId}`;
      case "super_embed": return `https://multiembed.mov/?video_id=${effectiveId}&tmdb=1`;
      case "ophm": return `https://ophim1.com/movie/${effectiveId}`;
      case "show_nitro": return `https://febbox.com/embed/movie/${effectiveId}`;
      default: return `https://vidlink.pro/movie/${effectiveId}`;
    }
  }
}
*/

class StreamRepository {
  async getStreamsFromAddon(baseUrl, type, videoId) {
    const url = this.buildStreamUrl(baseUrl, type, videoId);
    const result = await safeApiCall(() => StreamApi.getStreams(url));
    if (result.status !== "success") {
      return result;
    }

    const streams = (result.data?.streams || []).map((stream) => this.mapStream(stream));
    return { status: "success", data: streams };
  }

  async getStreamsFromAllAddons(type, videoId, options = {}) {
    const installedAddons = (await addonRepository.getInstalledAddons()).map((addon, index) => ({
      ...addon,
      orderIndex: index
    }));
    const onAddon = typeof options?.onAddon === "function" ? options.onAddon : null;

    const onChunk = typeof options?.onChunk === "function" ? options.onChunk : null;

    const notifyChunk = (group) => {
      if (!onChunk || !group?.streams?.length) {
        return;
      }
      try {
        onChunk({
          status: "success",
          data: [group]
        });
      } catch (error) {
        console.warn("Stream chunk callback failed", error);
      }
    };

    const notifyAddon = (addon, orderIndex) => {
      if (!onAddon || !addon) {
        return;
      }
      try {
        onAddon({ ...addon, orderIndex });
      } catch (error) {
        console.warn("Stream addon callback failed", error);
      }
    };

    const prepareDebridGroup = async (group) => {
      const checkingGroup =
        DebridStreamPresentation.apply(LocalDebridAvailabilityService.markChecking([group]))[0] ||
        group;
      const checkedGroup =
        (await LocalDebridAvailabilityService.annotateCachedAvailability([checkingGroup]))[0] ||
        checkingGroup;
      const presentedGroup = DebridStreamPresentation.apply([checkedGroup])[0] || checkedGroup;
      notifyChunk(presentedGroup);
      return presentedGroup;
    };

    const sourceAddons = options.preferredPluginOnly ? [] : installedAddons;
    const addonTasks = sourceAddons.map(async (addon) => {
      try {
        // Secondary/aggregator catalogs sometimes expose a channel row while
        // the original addon owns the ID under `tv`. Exact type remains the
        // default; an explicit, unambiguous idPrefix may recover the owner type.
        const streamRequestType = addonRepository.resolveResourceRequestType(
          addon,
          "stream",
          type,
          videoId,
          { allowIdTypeFallback: true }
        );
        const metaRequestType = addonRepository.resolveResourceRequestType(
          addon,
          "meta",
          type,
          videoId,
          { allowIdTypeFallback: true }
        );
        const canStream = Boolean(streamRequestType);
        const canMeta = Boolean(metaRequestType);
        // Meta-only stream discovery is a compatibility path for debrid cloud
        // items, which are exposed through the `other` type. Regular movie/series
        // metadata addons must not be queried as stream sources.
        const canTryMetaOnlyStreams =
          canMeta &&
          String(type || "")
            .trim()
            .toLowerCase() === "other";
        if (!canStream && !canTryMetaOnlyStreams) {
          return null;
        }
        const orderIndex = Number(addon.orderIndex ?? Number.MAX_SAFE_INTEGER);
        notifyAddon(addon, orderIndex);
        let addonStreams = [];
        let streamRequestSucceeded = false;
        if (canStream) {
          const streamsResult = await this.getStreamsFromAddon(
            addon.baseUrl,
            streamRequestType,
            videoId
          );
          if (streamsResult.status === "success") {
            streamRequestSucceeded = true;
            if (streamsResult.data.length) {
              addonStreams = streamsResult.data;
            }
          }
        }
        // Match Android: when a declared stream endpoint succeeds with no
        // results, try the matching meta video's inline streams even if the
        // manifest omitted its meta resource. Keep the existing meta-only
        // compatibility path for debrid cloud `other` catalogs as well.
        if (
          addonStreams.length === 0 &&
          ((canStream && streamRequestSucceeded) || canTryMetaOnlyStreams)
        ) {
          addonStreams = await this.fetchInlineStreamsFromMeta(
            addon,
            canStream ? streamRequestType : metaRequestType,
            videoId
          );
        }
        if (addonStreams.length === 0) {
          return null;
        }

        const group = {
          addonId: addon.id,
          addonBaseUrl: addon.baseUrl,
          addonName: addon.displayName,
          addonLogo: addon.logo,
          addonOrderIndex: orderIndex,
          streamOrigin: {
            kind: "addon",
            addonId: addon.id,
            addonBaseUrl: addon.baseUrl,
            addonName: addon.displayName,
            addonOrderIndex: orderIndex
          },
          streams: addonStreams.map((stream) => ({
            ...stream,
            addonId: addon.id,
            addonBaseUrl: addon.baseUrl,
            addonName: addon.displayName,
            addonLogo: addon.logo,
            addonOrderIndex: orderIndex,
            streamOrigin: {
              ...(stream.streamOrigin || {}),
              kind: "addon",
              addonId: addon.id,
              addonBaseUrl: addon.baseUrl,
              addonName: addon.displayName,
              addonOrderIndex: orderIndex
            }
          }))
        };
        return prepareDebridGroup(group);
      } catch (_) {
        return null;
      }
    });

    const pluginTask = (async () => {
      const prepared = [];
      const controller = new AbortController();
      const abort = () => controller.abort();
      options.signal?.addEventListener("abort", abort, { once: true });
      if (options.signal?.aborted) abort();
      const timeout = setTimeout(abort, PLUGIN_LIMITS.globalTimeoutMs);
      try {
        await this.getPluginStreams(type, videoId, {
          ...options,
          signal: controller.signal,
          onGroup: (group) => prepared.push(prepareDebridGroup(group).catch(() => group))
        });
      } catch (error) {
        if (!controller.signal.aborted) console.warn("Plugin stream fetch failed", error);
      } finally {
        clearTimeout(timeout);
        options.signal?.removeEventListener("abort", abort);
      }
      const grouped = new Map();
      for (const group of await Promise.all(prepared)) {
        const previous = grouped.get(group.sourceProviderId);
        grouped.set(
          group.sourceProviderId,
          previous ? { ...previous, streams: [...previous.streams, ...group.streams] } : group
        );
      }
      return [...grouped.values()];
    })();

    const zyloServerTask = (async () => {
      if (options.preferredPluginOnly) return [];
      try {
        const group = await this.getZyloServerStreams(type, videoId, options);
        if (group?.streams?.length) {
          notifyChunk(group);
          return [group];
        }
        return [];
      } catch (_) {
        return [];
      }
    })();

    const [addonsWithStreams, pluginStreams, zyloServerStreams] = await Promise.all([
      Promise.all(addonTasks).then((items) =>
        items.filter(Boolean).sort(
          (left, right) => Number(left.addonOrderIndex || 0) - Number(right.addonOrderIndex || 0)
        )
      ),
      pluginTask,
      zyloServerTask
    ]);

    return {
      status: "success",
      data: [...zyloServerStreams, ...addonsWithStreams, ...pluginStreams]
    };
  }

  async getZyloServerStreams(type, videoId, options = {}) {
    const rawVideoId = String(videoId || "").trim();
    const isTv =
      String(type || "").toLowerCase() === "series" ||
      String(type || "").toLowerCase() === "tv" ||
      Boolean(options?.season != null);

    const parts = rawVideoId.split(":");
    let imdbId = null;
    let tmdbId = null;
    let season = options?.season != null ? Number(options.season) : 1;
    let episode = options?.episode != null ? Number(options.episode) : 1;

    for (const part of parts) {
      if (/^tt\d+$/i.test(part)) imdbId = part;
      else if (/^\d+$/.test(part) && !tmdbId) tmdbId = part;
    }
    const embeddedEpisodeParts = parts[0]?.toLowerCase() === "tmdb" ? parts.slice(2) : parts.slice(1);
    if (
      options?.season == null &&
      options?.episode == null &&
      embeddedEpisodeParts.length >= 2 &&
      !isNaN(embeddedEpisodeParts[0]) &&
      !isNaN(embeddedEpisodeParts[1])
    ) {
      season = Number(embeddedEpisodeParts[0]);
      episode = Number(embeddedEpisodeParts[1]);
    }
    if (options.imdbId && !imdbId) imdbId = options.imdbId;
    if (options.tmdbId && !tmdbId) tmdbId = String(options.tmdbId);

    if (!tmdbId && imdbId) {
      try {
        tmdbId = await TmdbService.ensureTmdbId(imdbId, isTv ? "tv" : "movie", {
          requireEnabled: false,
          signal: options.signal
        });
      } catch (_) {}
    }

    if (!(tmdbId || imdbId || rawVideoId)) return null;

    const queryIds = buildZyloDirectQueryIds({
      isTv,
      rawId: parts[0] === "tmdb" ? tmdbId : parts[0] || rawVideoId,
      imdbId,
      tmdbId,
      season,
      episode
    });
    const streamType = isTv ? "series" : "movie";
    let streams = [];
    for (const queryId of queryIds) {
      if (options.signal?.aborted) break;
      const controller = new AbortController();
      const abort = () => controller.abort();
      options.signal?.addEventListener("abort", abort, { once: true });
      const timer = setTimeout(abort, 8000);
      const endpoint = `${ZYLO_DIRECT_ADDON.endpoint}/${streamType}/${encodeURIComponent(queryId)}.json`;
      const result = await safeApiCall(() =>
        StreamApi.getStreams(endpoint, {
          signal: controller.signal,
          headers: { Accept: "application/json" }
        })
      );
      clearTimeout(timer);
      options.signal?.removeEventListener("abort", abort);
      if (result.status !== "success") continue;
      const mapped = (result.data?.streams || []).map((stream) => this.mapStream(stream));
      streams = prepareZyloDirectStreams(mapped);
      if (streams.length) break;
    }

    if (!streams.length) return null;

    return {
      addonId: ZYLO_DIRECT_ADDON.id,
      addonBaseUrl: ZYLO_DIRECT_ADDON.endpoint,
      addonName: ZYLO_DIRECT_ADDON.name,
      addonLogo: null,
      addonOrderIndex: -1,
      streamOrigin: {
        kind: "server",
        addonId: ZYLO_DIRECT_ADDON.id,
        addonName: ZYLO_DIRECT_ADDON.name
      },
      streams
    };
  }

  async getPluginStreams(type, videoId, options = {}) {
    if (!PluginManager.pluginsEnabled || options.signal?.aborted) return [];
    const rawVideoId = String(videoId || "").trim();
    const localId = isLocalPluginVideoId(rawVideoId);
    const knownTmdbId = [options.tmdbId, options.itemId, rawVideoId]
      .map((id) => /^(?:(?:tmdb|movie|series):)*(\d+)(?::|$)/i.exec(String(id ?? "").trim())?.[1])
      .find(Boolean);
    const tmdbLookupId = localId
      ? rawVideoId
      : String(knownTmdbId || options.imdbId || options.itemId || rawVideoId).trim();
    const tmdbId = await TmdbService.ensureTmdbId(tmdbLookupId, type, {
      requireEnabled: false,
      signal: options.signal
    });
    const pluginId = tmdbId || (localId ? cleanLocalPluginVideoId(rawVideoId) : null);
    if (!pluginId) return [];
    const mapGroup = (result) => ({
      sourceProviderId: result.sourceId || result.sourceName || null,
      addonName: result.sourceName,
      addonLogo: null,
      streamOrigin: {
        kind: "plugin",
        sourceProviderId: result.sourceId || result.sourceName || null,
        addonName: result.sourceName || null
      },
      streams: (result.streams || []).map((stream) => ({
        ...stream,
        sourceProviderId: result.sourceId || result.sourceName || null,
        addonName: result.sourceName,
        addonLogo: null,
        streamOrigin: {
          ...(stream.streamOrigin || {}),
          kind: "plugin",
          sourceProviderId: result.sourceId || result.sourceName || null,
          addonName: result.sourceName || null
        }
      }))
    });
    const pluginResults = await PluginManager.executeScrapersStreaming({
      ...resolvePluginSeasonEpisode(rawVideoId, options.season, options.episode),
      tmdbId: pluginId,
      mediaType: tmdbId && (type === "series" || type === "show") ? "tv" : type,
      signal: options.signal,
      priorityBingeGroup: options.priorityBingeGroup,
      preferredPluginOnly: options.preferredPluginOnly,
      onGroup: options.onGroup ? (group) => options.onGroup(mapGroup(group)) : undefined
    });
    return pluginResults.map(mapGroup);
  }

  buildStreamUrl(baseUrl, type, videoId) {
    const cleanBaseUrl = addonRepository.canonicalizeUrl(baseUrl);
    const queryStart = cleanBaseUrl.indexOf("?");
    const basePath =
      queryStart >= 0 ? cleanBaseUrl.slice(0, queryStart).replace(/\/+$/, "") : cleanBaseUrl;
    const baseQuery = queryStart >= 0 ? cleanBaseUrl.slice(queryStart) : "";
    return `${basePath}/stream/${this.encode(type)}/${this.encode(videoId)}.json${baseQuery}`;
  }

  buildMetaUrl(baseUrl, type, id) {
    const cleanBaseUrl = addonRepository.canonicalizeUrl(baseUrl);
    const queryStart = cleanBaseUrl.indexOf("?");
    const basePath =
      queryStart >= 0 ? cleanBaseUrl.slice(0, queryStart).replace(/\/+$/, "") : cleanBaseUrl;
    const baseQuery = queryStart >= 0 ? cleanBaseUrl.slice(queryStart) : "";
    return `${basePath}/meta/${this.encode(type)}/${this.encode(id)}.json${baseQuery}`;
  }

  encode(value) {
    return encodeURIComponent(String(value || "")).replace(/\+/g, "%20");
  }

  mapStream(stream = {}) {
    const sidecarSubtitles = Array.isArray(stream.subtitles)
      ? stream.subtitles
          .filter((entry) => entry && entry.url)
          .map((entry) => ({
            id: entry.id || null,
            url: entry.url,
            lang: entry.lang || "unknown"
          }))
      : [];

    return {
      name: stream.name || null,
      title: stream.title || null,
      description: stream.description || null,
      url: stream.url ? secureMediaUrl(stream.url) : null,
      ytId: stream.ytId || null,
      infoHash: stream.infoHash || null,
      fileIdx: stream.fileIdx ?? null,
      externalUrl: stream.externalUrl || null,
      behaviorHints: stream.behaviorHints || null,
      sources: Array.isArray(stream.sources) ? stream.sources : [],
      quality: stream.quality || null,
      qualityValue: Number.isFinite(Number(stream.qualityValue)) ? Number(stream.qualityValue) : -1,
      clientResolve: stream.clientResolve || null,
      debridCacheStatus: stream.debridCacheStatus || null,
      subtitles: sidecarSubtitles
    };
  }

  async fetchInlineStreamsFromMeta(addon, type, videoId) {
    const rawVideoId = String(videoId || "").trim();
    if (!addon?.baseUrl || !rawVideoId) {
      return [];
    }

    // Try the content-level id (handles series episode ids like tt123:1:2)
    // and the raw id (handles content whose clicked id is the meta id itself,
    // e.g. debrid cloud "other" items keyed dmm:<torrentId>).
    const contentLevelId = this.buildContentLevelMetaId(rawVideoId);
    const candidateMetaIds = [];
    if (contentLevelId) {
      candidateMetaIds.push(contentLevelId);
    }
    if (rawVideoId && rawVideoId !== contentLevelId) {
      candidateMetaIds.push(rawVideoId);
    }

    for (const metaId of candidateMetaIds) {
      const url = this.buildMetaUrl(addon.baseUrl, type, metaId);
      const result = await safeApiCall(() => MetaApi.getMeta(url));

      if (result.status !== "success") {
        continue;
      }

      const meta = result.data?.meta || null;
      const videos = Array.isArray(meta?.videos) ? meta.videos : [];

      if (!videos.length) {
        continue;
      }

      const matchingVideo =
        videos.find((video) => String(video?.id || "") === rawVideoId) ||
        (type !== "series" && videos.length === 1 ? videos[0] : null);

      const streams = Array.isArray(matchingVideo?.streams) ? matchingVideo.streams : [];

      const mapped = streams
        .map((stream) => this.mapStream(stream))
        .filter(
          (stream) =>
            stream.url ||
            stream.externalUrl ||
            stream.ytId ||
            stream.clientResolve ||
            stream.infoHash
        );

      if (mapped.length) {
        return mapped;
      }
    }

    return [];
  }

  buildContentLevelMetaId(videoId) {
    const raw = String(videoId || "").trim();
    if (!raw) {
      return "";
    }
    const parts = raw.split(":");
    if (parts.length <= 1) {
      return raw;
    }
    let trailingNumericCount = 0;
    for (let index = parts.length - 1; index >= 0; index -= 1) {
      if (!/^\d+$/.test(parts[index])) {
        break;
      }
      trailingNumericCount += 1;
    }
    const firstSegment = parts[0];
    const minSegments = /^tt/i.test(firstSegment) || /^\d+$/.test(firstSegment) ? 1 : 2;
    const segmentsToDrop = Math.min(trailingNumericCount, Math.max(0, parts.length - minSegments));
    return segmentsToDrop > 0 ? parts.slice(0, -segmentsToDrop).join(":") : raw;
  }
}

export const streamRepository = new StreamRepository();
