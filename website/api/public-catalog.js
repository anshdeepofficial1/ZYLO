import { TMDB_API_KEY } from "../js/config.js";

// Public movie metadata only. No arbitrary URLs, video URLs or provider proxy.
export default async function handler(req, res) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    return res.status(405).json({ error: "Method not allowed" });
  }
  const key = String(process.env.TMDB_API_KEY || TMDB_API_KEY || "").trim();
  if (!key) return res.status(503).json({ error: "Catalog not configured" });
  const incoming = new URL(req.url, "https://localhost").searchParams;
  const route = incoming.get("route");
  if (route !== "discover" && route !== "search") return res.status(400).json({ error: "Invalid catalog route" });
  const params = new URLSearchParams({ api_key: key, include_adult: "false" });
  if (route === "search") {
    const query = (incoming.get("query") || "").trim();
    if (!query || query.length > 160) return res.status(400).json({ error: "Invalid search query" });
    params.set("query", query);
  } else {
    params.set("sort_by", "popularity.desc");
    params.set("page", "1");
    const language = incoming.get("with_original_language");
    if (language && !["hi","te","ta","ml","kn","pa"].includes(language)) return res.status(400).json({ error: "Invalid language" });
    if (language) { params.set("with_original_language", language); params.set("with_origin_country", "IN"); }
  }
  try {
    const response = await fetch("https://api.themoviedb.org/3/" + route + "/movie?" + params, { signal: AbortSignal.timeout(10000) });
    if (!response.ok) return res.status(response.status === 429 ? 429 : 502).json({ error: "Metadata provider unavailable" });
    const payload = await response.json();
    if (!Array.isArray(payload?.results)) return res.status(502).json({ error: "Invalid metadata response" });
    if (route === "discover") res.setHeader("Cache-Control", "public, s-maxage=300, stale-while-revalidate=600");
    else res.setHeader("Cache-Control", "no-store");
    return res.status(200).json({ results: payload.results, page: payload.page, total_pages: payload.total_pages });
  } catch {
    return res.status(503).json({ error: "Metadata request failed" });
  }
}
