export const ZYLO_DIRECT_ADDON = Object.freeze({
  id: "zylo_servers",
  name: "ZYLO Direct",
  endpoint: "https://hd.plavio.site/stream"
});

export function buildZyloDirectQueryIds({
  isTv,
  rawId,
  imdbId,
  tmdbId,
  season = 1,
  episode = 1
} = {}) {
  const ids = [];
  const add = (value) => {
    const next = String(value || "").trim();
    if (next && !ids.includes(next)) ids.push(next);
  };
  if (isTv) {
    if (imdbId) add(`${imdbId}:${season}:${episode}`);
    if (tmdbId) add(`tmdb:${tmdbId}:${season}:${episode}`);
    if (rawId && rawId !== imdbId && rawId !== tmdbId) add(`${rawId}:${season}:${episode}`);
  } else {
    add(imdbId);
    if (tmdbId) add(`tmdb:${tmdbId}`);
    if (rawId && rawId !== imdbId && rawId !== tmdbId) add(rawId);
  }
  return ids;
}

function streamText(stream = {}) {
  return [
    stream.name,
    stream.title,
    stream.description,
    stream.behaviorHints?.filename,
    stream.url
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

function parsedSizeGigabytes(text) {
  const sizes = [...String(text || "").matchAll(/(\d+(?:\.\d+)?)\s*gb\b/gi)]
    .map((match) => Number(match[1]))
    .filter(Number.isFinite);
  return sizes.length ? Math.min(...sizes) : null;
}

export function zyloDirectReliabilityScore(stream = {}) {
  const text = streamText(stream);
  let score = 100;
  if (text.includes("1080p")) score -= 18;
  if (text.includes("720p")) score -= 12;
  if (text.includes("480p")) score -= 4;
  if (text.includes("2160p") || text.includes("4k")) score += 28;
  if (text.includes(".mp4")) score -= 18;
  if (/(?:x264|h264|avc)/.test(text)) score -= 14;
  if (text.includes("aac")) score -= 8;
  if (text.includes(".mkv")) score += 5;
  if (/(?:x265|h265|hevc)/.test(text)) score += 10;
  if (/(?:truehd|\sdts|atmos)/.test(text)) score += 8;
  if (/(?:\shdr|dolby vision|\sdv\s)/.test(text)) score += 5;
  const size = parsedSizeGigabytes(text);
  if (size != null) {
    if (size <= 2.5) score -= 8;
    else if (size <= 5) score -= 4;
    else if (size >= 25) score += 18;
    else if (size >= 12) score += 10;
  }
  return score;
}

function urlHost(value) {
  try {
    return new URL(String(value || "")).hostname.replace(/^www\./, "").toLowerCase();
  } catch {
    return "";
  }
}

function providerKey(stream = {}, index = 0) {
  const text = streamText(stream);
  const known = [
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
    "dexter"
  ].find((provider) => text.includes(provider));
  if (known) return known;
  try {
    const outer = new URL(String(stream.url || ""));
    return urlHost(outer.searchParams.get("url")) || urlHost(stream.url) || `source_${index}`;
  } catch {
    return urlHost(stream.url) || `source_${index}`;
  }
}

function stableId(text) {
  let hash = 2166136261;
  for (const char of String(text || "")) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

/**
 * Only direct HTTP media returned by the discovery API is playable. Embed-page fallbacks are
 * intentionally excluded: feeding their HTML to a video element produces the unrecognised-format
 * error this adapter exists to prevent.
 */
export function prepareZyloDirectStreams(streams = []) {
  const seen = new Set();
  return (Array.isArray(streams) ? streams : [])
    .filter((stream) => {
      try {
        const url = new URL(String(stream?.url || ""));
        if (!["https:", "http:"].includes(url.protocol) || seen.has(url.href)) return false;
        seen.add(url.href);
        return true;
      } catch {
        return false;
      }
    })
    .sort((left, right) => zyloDirectReliabilityScore(left) - zyloDirectReliabilityScore(right))
    .map((stream, index) => {
      const sourceProviderId = providerKey(stream, index);
      const filename = String(stream?.behaviorHints?.filename || "");
      const identity = [sourceProviderId, stream.name, stream.title, filename].join("|");
      return {
        ...stream,
        id: `zylo_direct_${sourceProviderId}_${stableId(identity)}`,
        externalUrl: null,
        externalFrameUrl: null,
        addonId: ZYLO_DIRECT_ADDON.id,
        addonName: ZYLO_DIRECT_ADDON.name,
        addonBaseUrl: ZYLO_DIRECT_ADDON.endpoint,
        addonOrderIndex: -1,
        sourceName: ZYLO_DIRECT_ADDON.name,
        sourceProviderId,
        streamType: stream.streamType || "direct",
        streamOrigin: {
          kind: "server",
          addonId: ZYLO_DIRECT_ADDON.id,
          addonName: ZYLO_DIRECT_ADDON.name,
          sourceProviderId
        },
        behaviorHints: {
          ...(stream.behaviorHints || {}),
          bingeGroup: stream.behaviorHints?.bingeGroup || `zylo_direct|${sourceProviderId}`
        }
      };
    });
}
