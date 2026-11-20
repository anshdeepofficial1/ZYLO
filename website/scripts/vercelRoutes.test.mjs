import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("Vercel sends PWA deep links back to the application shell", async () => {
  const config = JSON.parse(await readFile(new URL("../vercel.json", import.meta.url), "utf8"));
  const sources = config.rewrites?.map((entry) => String(entry.source || "")) || [];
  const deepLinkRule = sources.find((source) => source.endsWith("/:path*"));

  assert.ok(deepLinkRule, "deep-link rewrite must exist");
  for (const route of ["catalog", "detail", "streams", "watch", "cast", "profiles"]) {
    assert.match(deepLinkRule, new RegExp(`(?:\\||\\()${route}(?:\\||\\))`));
  }
});
