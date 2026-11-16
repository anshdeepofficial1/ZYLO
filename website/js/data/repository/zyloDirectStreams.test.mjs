import assert from "node:assert/strict";
import test from "node:test";

import {
  buildZyloDirectQueryIds,
  prepareZyloDirectStreams
} from "./zyloDirectStreams.js";

test("builds direct discovery ids for movies and episodes", () => {
  assert.deepEqual(
    buildZyloDirectQueryIds({ rawId: "603", imdbId: "tt0133093", tmdbId: "603" }),
    ["tt0133093", "tmdb:603"]
  );
  assert.deepEqual(
    buildZyloDirectQueryIds({
      isTv: true,
      rawId: "1396",
      imdbId: "tt0903747",
      tmdbId: "1396",
      season: 2,
      episode: 3
    }),
    ["tt0903747:2:3", "tmdb:1396:2:3"]
  );
});

test("rejects embed-only entries and sorts browser-compatible media first", () => {
  const prepared = prepareZyloDirectStreams([
    { title: "4K HEVC 40 GB", url: "https://cdn.example/large.mkv" },
    {
      title: "1080p x264 AAC 2 GB",
      url: "https://cdn.example/fast.mp4",
      externalUrl: "https://embed.example/player",
      behaviorHints: {
        filename: "movie.1080p.x264.aac.mp4",
        proxyHeaders: { request: { Referer: "https://source.example/" } }
      }
    },
    { title: "HTML embed only", externalUrl: "https://embed.example/player" }
  ]);

  assert.equal(prepared.length, 2);
  assert.equal(prepared[0].url, "https://cdn.example/fast.mp4");
  assert.equal(prepared[0].externalUrl, null);
  assert.deepEqual(prepared[0].behaviorHints.proxyHeaders.request, {
    Referer: "https://source.example/"
  });
  assert.match(prepared[0].behaviorHints.bingeGroup, /^zylo_direct\|/);
});
