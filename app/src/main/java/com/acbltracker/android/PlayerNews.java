package com.acbltracker.android;

import org.json.*;
import java.net.URI;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Small, player-matched view of ESPN overview news; no article bodies are retained. */
final class PlayerNews {
    final String headline, summary, url, source;
    final long published;
    PlayerNews(String headline, String summary, String url, String source, long published) {
        this.headline = headline; this.summary = summary; this.url = url; this.source = source; this.published = published;
    }
    static List<PlayerNews> parse(JSONObject overview, League.Player player) {
        ArrayList<PlayerNews> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        JSONObject fantasy = overview.optJSONObject("rotowire");
        if (fantasy != null) {
            String headline = clean(fantasy.optString("headline"));
            if (!headline.isEmpty()) {
                String url = "https://www.espn.com/nba/player/_/id/" + player.id;
                out.add(new PlayerNews(headline, snippet(fantasy.optString("story"), headline), url, "ESPN · RotoWire", timestamp(fantasy.optString("published"))));
                seen.add(normalized(headline));
            }
        }
        JSONArray articles = overview.optJSONArray("news");
        if (articles != null) for (int i = 0; i < articles.length(); i++) {
            JSONObject a = articles.optJSONObject(i); if (a == null || !matches(a, player)) continue;
            String headline = clean(a.optString("headline"));
            String url = safeUrl(EspnClient.object(EspnClient.object(a, "links"), "web").optString("href"));
            if (headline.isEmpty() || url.isEmpty() || !seen.add(normalized(headline))) continue;
            out.add(new PlayerNews(headline, snippet(a.optString("description"), headline), url, "ESPN" + ("Media".equals(a.optString("type")) ? " · Video" : ""), timestamp(a.optString("published", a.optString("lastModified")))));
        }
        // Put the direct player/fantasy update before broader tagged videos and recaps.
        out.sort(Comparator.comparingInt((PlayerNews n) -> n.source.equals("ESPN · RotoWire") ? 0 : 1)
                .thenComparing(Comparator.comparingLong((PlayerNews n) -> n.published).reversed()));
        return Collections.unmodifiableList(new ArrayList<>(out.subList(0, Math.min(5, out.size()))));
    }
    private static boolean matches(JSONObject article, League.Player player) {
        JSONArray categories = article.optJSONArray("categories");
        if (categories != null) for (int i = 0; i < categories.length(); i++) {
            JSONObject c = categories.optJSONObject(i); if (c == null) continue;
            if (player.id.equals(c.optString("athleteId")) || player.id.equals(EspnClient.object(c, "athlete").optString("id"))) return true;
        }
        String name = normalized(player.name);
        return !name.isEmpty() && (" " + normalized(article.optString("headline") + " " + article.optString("description")) + " ").contains(" " + name + " ");
    }
    static String safeUrl(String url) {
        try {
            URI u = URI.create(url); String host = u.getHost();
            if (host == null || !(host.equalsIgnoreCase("espn.com") || host.toLowerCase(Locale.ROOT).endsWith(".espn.com")) || u.getUserInfo() != null || u.getPort() != -1 || !("http".equals(u.getScheme()) || "https".equals(u.getScheme()))) return "";
            return new URI("https", u.getAuthority(), u.getPath(), u.getQuery(), u.getFragment()).toString();
        } catch (Exception e) { return ""; }
    }
    static long timestamp(String text) {
        try { return OffsetDateTime.parse(text).toInstant().toEpochMilli(); } catch (Exception ignored) {}
        try { return ZonedDateTime.parse(text, DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss z yyyy", Locale.US)).toInstant().toEpochMilli(); } catch (Exception ignored) {}
        return 0;
    }
    static String clean(String text) { return text.replaceAll("<[^>]*>", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim(); }
    private static String snippet(String text, String headline) { String s = clean(text); return s.equalsIgnoreCase(headline) ? "" : s.length() > 200 ? s.substring(0, 197).trim() + "…" : s; }
    private static String normalized(String text) { return Normalizer.normalize(clean(text), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
}
