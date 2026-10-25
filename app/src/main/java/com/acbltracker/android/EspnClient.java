package com.acbltracker.android;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static com.acbltracker.android.League.*;

public class EspnClient implements AutoCloseable {
    static final String BASE = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba";
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Map<String, NewsCache> newsCache = new ConcurrentHashMap<>();
    private static final class NewsCache {
        final long fetched = System.currentTimeMillis(); final List<PlayerNews> items;
        NewsCache(List<PlayerNews> items) { this.items = items; }
    }
    List<PlayerNews> news(Player player, boolean force) throws Exception {
        NewsCache cached = newsCache.get(player.id);
        if (!force && cached != null && System.currentTimeMillis() - cached.fetched < 300000) return cached.items;
        JSONObject overview = get("https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/" + player.id + "/overview");
        if (!overview.has("news") && !overview.has("rotowire")) throw new IOException("ESPN player news unavailable");
        List<PlayerNews> items = PlayerNews.parse(overview, player); newsCache.put(player.id, new NewsCache(items)); return items;
    }
    public JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestProperty("Accept", "application/json");
        try { if (c.getResponseCode() != 200) throw new IOException("ESPN returned " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return new JSONObject(out.toString(StandardCharsets.UTF_8.name())); }
        } finally { c.disconnect(); }
    }
    static JSONArray array(JSONObject j, String key) { JSONArray a = j == null ? null : j.optJSONArray(key); return a == null ? new JSONArray() : a; }
    static JSONObject object(JSONObject j, String key) { JSONObject a = j == null ? null : j.optJSONObject(key); return a == null ? new JSONObject() : a; }
    public List<Player> directory() throws Exception {
        JSONArray sports = array(get(BASE + "/teams?limit=40"), "sports");
        JSONArray leagues = array(sports.optJSONObject(0), "leagues"); JSONArray teams = array(leagues.optJSONObject(0), "teams");
        if (teams.length() == 0) throw new IOException("ESPN returned no teams");
        List<Future<List<Player>>> jobs = new ArrayList<>();
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = object(teams.optJSONObject(i), "team");
            jobs.add(pool.submit(() -> {
                String id = team.optString("id"), abbr = team.optString("abbreviation"); List<Player> out = new ArrayList<>();
                flatten(array(get(BASE + "/teams/" + id + "/roster"), "athletes"), id, abbr, out); return out;
            }));
        }
        List<Player> out = new ArrayList<>();
        for (Future<List<Player>> job : jobs) out.addAll(job.get());
        if (out.isEmpty()) throw new IOException("ESPN returned no players");
        out.sort(Comparator.comparing(p -> p.name)); return out;
    }
    private void flatten(JSONArray athletes, String teamId, String team, List<Player> out) {
        for (int i = 0; i < athletes.length(); i++) {
            JSONObject a = athletes.optJSONObject(i); if (a == null) continue;
            if (a.has("items")) { flatten(array(a, "items"), teamId, team, out); continue; }
            String pos = object(a, "position").optString("abbreviation", "G");
            if (!a.optString("id").isEmpty()) out.add(new Player(a.optString("id"), a.optString("displayName", a.optString("fullName")), pos.equals("C") ? "C" : pos.contains("F") ? "F" : "G", teamId, team));
        }
    }
    public Map<String, String> injuries() throws Exception {
        Map<String, String> out = new HashMap<>(); JSONArray teams = array(get(BASE + "/injuries"), "injuries");
        for (int i = 0; i < teams.length(); i++) {
            JSONArray injuries = array(teams.optJSONObject(i), "injuries");
            for (int j = 0; j < injuries.length(); j++) {
                JSONObject entry = injuries.optJSONObject(j), detail = object(entry, "details");
                String status = entry.optString("status");
                if (!status.equalsIgnoreCase("active")) out.put(object(entry, "athlete").optString("id"), status + (detail.optString("detail").isEmpty() ? "" : " · " + detail.optString("detail")));
            }
        }
        return out;
    }
    static final class Game {
        String id, date, day, state, status, home, away, homeAbbr, awayAbbr;
    }
    private List<Game> schedule(LocalDate day) throws Exception {
        JSONObject response = get(BASE + "/scoreboard?limit=50&dates=" + day.format(DateTimeFormatter.BASIC_ISO_DATE));
        if (!response.has("events")) throw new IOException("ESPN schedule format changed");
        List<Game> out = new ArrayList<>(); JSONArray events = array(response, "events");
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i), competition = array(e, "competitions").optJSONObject(0), status = object(e, "status"), type = object(status, "type");
            String name = type.optString("name"); if (name.contains("POSTPONED") || name.contains("CANCEL")) continue;
            StringBuilder notes = new StringBuilder(); JSONArray n = array(competition, "notes"); for (int j = 0; j < n.length(); j++) notes.append(n.optJSONObject(j).optString("headline").toLowerCase(Locale.US));
            if (notes.toString().contains("cup") && (notes.toString().contains("championship") || notes.toString().contains("final"))) continue;
            Game g = new Game(); g.id = e.optString("id"); g.date = e.optString("date");
            g.day = OffsetDateTime.parse(g.date).atZoneSameInstant(EASTERN).toLocalDate().toString(); g.state = type.optString("state"); g.status = type.optString("shortDetail", type.optString("description"));
            JSONArray competitors = array(competition, "competitors");
            for (int j = 0; j < competitors.length(); j++) { JSONObject c = competitors.optJSONObject(j), t = object(c, "team"); if (c.optString("homeAway").equals("home")) { g.home = t.optString("id"); g.homeAbbr = t.optString("abbreviation"); } else { g.away = t.optString("id"); g.awayAbbr = t.optString("abbreviation"); } }
            if (g.home != null && g.away != null) out.add(g);
        }
        return out;
    }
    private Map<String, GameLine> box(String id) throws Exception {
        JSONObject response = get(BASE + "/summary?event=" + id), box = response.optJSONObject("boxscore");
        if (box == null || array(box, "players").length() == 0) throw new IOException("Box score not yet available");
        Map<String, GameLine> out = new HashMap<>(); JSONArray teams = array(box, "players");
        for (int i = 0; i < teams.length(); i++) {
            JSONArray groups = array(teams.optJSONObject(i), "statistics");
            if (groups.length() == 0) throw new IOException("Box score has no statistics");
            JSONObject group = groups.optJSONObject(0); JSONArray labels = array(group, "labels");
            if (index(labels, "PTS") < 0 || index(labels, "MIN") < 0) throw new IOException("ESPN box score columns changed");
            JSONArray athletes = array(group, "athletes");
            for (int j = 0; j < athletes.length(); j++) {
                JSONObject a = athletes.optJSONObject(j); JSONArray stats = array(a, "stats"); GameLine l = new GameLine();
                l.minutes = stat(labels, stats, "MIN");
                boolean played = !a.optBoolean("didNotPlay") && stats.length() > 0 && minutesPositive(l.minutes);
                l.state = played ? State.PLAYED : State.DNP;
                try { l.points = Integer.parseInt(stat(labels, stats, "PTS")); } catch (Exception ignored) {}
                l.reb = stat(labels, stats, "REB"); l.ast = stat(labels, stats, "AST"); l.fg = stat(labels, stats, "FG");
                out.put(object(a, "athlete").optString("id"), l);
            }
        }
        return out;
    }
    static boolean minutesPositive(String minutes) { try { return Double.parseDouble(minutes.replace(":", ".")) > 0; } catch (Exception e) { return false; } }
    static int index(JSONArray a, String value) { for (int i = 0; i < a.length(); i++) if (a.optString(i).equalsIgnoreCase(value)) return i; return -1; }
    static String stat(JSONArray labels, JSONArray stats, String label) { int i = index(labels, label); return i < 0 ? "" : stats.optString(i); }
    public Week week(LocalDate start, Lineup lineup) throws Exception {
        List<Future<List<Game>>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) { LocalDate day = start.plusDays(i); jobs.add(pool.submit(() -> schedule(day))); }
        Map<String, Game> unique = new HashMap<>();
        // Never calculate or freeze scores from an incomplete schedule.
        for (Future<List<Game>> job : jobs) for (Game g : job.get()) unique.put(g.id, g);
        List<Game> games = new ArrayList<>(unique.values()); games.sort(Comparator.comparing(g -> g.date));
        Map<String, List<Game>> byTeam = new HashMap<>();
        for (Game g : games) { byTeam.computeIfAbsent(g.home, k -> new ArrayList<>()).add(g); byTeam.computeIfAbsent(g.away, k -> new ArrayList<>()).add(g); }
        Map<String, Future<Map<String, GameLine>>> boxes = new HashMap<>();
        for (Player p : lineup.players) if (p != null) {
            List<Game> list = byTeam.getOrDefault(p.teamId, Collections.emptyList());
            for (int g = 0; g < Math.min(2, list.size()); g++) { Game game = list.get(g); if (!game.state.equals("pre")) boxes.computeIfAbsent(game.id, id -> pool.submit(() -> box(id))); }
        }
        Week w = new Week(); boolean settled = true, any = false;
        for (int i = 0; i < 14; i++) {
            Player p = lineup.players[i]; if (p == null) continue; any = true;
            List<Game> list = byTeam.getOrDefault(p.teamId, Collections.emptyList());
            for (int g = 0; g < 2; g++) {
                if (g >= list.size()) { settled = false; continue; }
                Game game = list.get(g); GameLine l = new GameLine();
                if (boxes.containsKey(game.id)) {
                    Map<String, GameLine> b = boxes.get(game.id).get(); GameLine stat = b.get(p.id);
                    if (stat != null) { l.points = stat.points; l.minutes = stat.minutes; l.reb = stat.reb; l.ast = stat.ast; l.fg = stat.fg; }
                    if (game.state.equals("post")) l.state = stat == null ? State.DNP : stat.state;
                    else if (stat != null && stat.state == State.PLAYED) l.state = State.LIVE;
                }
                l.eventId = game.id; l.day = game.day; l.status = game.status;
                l.opponent = p.teamId.equals(game.home) ? "vs " + game.awayAbbr : "@ " + game.homeAbbr;
                w.lines[g][i] = l; if (!game.state.equals("post")) settled = false; if (game.state.equals("in")) w.live = true;
            }
        }
        w.updated = System.currentTimeMillis(); w.frozen = any && settled && !LocalDate.now(EASTERN).isBefore(start.plusDays(8)); return w;
    }
    public String recentGames(String id) throws Exception {
        LocalDate now = LocalDate.now(EASTERN); int season = now.getMonthValue() >= 9 ? now.getYear() + 1 : now.getYear();
        JSONObject j = get("https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/" + id + "/gamelog?season=" + season);
        JSONArray labels = array(j, "labels"), names = array(j, "names"), types = array(j, "seasonTypes"); JSONObject events = object(j, "events");
        TreeMap<String, String> rows = new TreeMap<>(Collections.reverseOrder());
        for (int t = 0; t < types.length(); t++) {
            JSONArray categories = array(types.optJSONObject(t), "categories");
            for (int c = 0; c < categories.length(); c++) {
                JSONArray es = array(categories.optJSONObject(c), "events");
                for (int e = 0; e < es.length(); e++) {
                    JSONObject entry = es.optJSONObject(e), info = object(events, entry.optString("eventId")); JSONArray stats = array(entry, "stats");
                    String date = info.optString("gameDate"); if (date.length() < 10) continue;
                    int pi = index(names, "points"); if (pi < 0) pi = index(labels, "PTS");
                    rows.put(date, date.substring(0, 10) + "   " + info.optString("atVs", "vs") + " " + object(info, "opponent").optString("abbreviation") + "   " + (pi < 0 ? "—" : stats.optString(pi)) + " PTS");
                }
            }
        }
        StringBuilder text = new StringBuilder(); int count = 0; for (String row : rows.values()) { if (count++ == 5) break; text.append(row).append("\n\n"); }
        return text.length() == 0 ? "No recent games available from ESPN." : text.toString().trim();
    }
    public void close() { pool.shutdownNow(); }
}
