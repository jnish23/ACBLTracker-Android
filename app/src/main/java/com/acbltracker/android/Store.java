package com.acbltracker.android;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static com.acbltracker.android.League.*;

/** Atomic, app-private storage. Snapshots resolve to the most recent earlier lineup. */
public final class Store {
    private final File file;
    private JSONObject root;
    public Store(Context context) {
        this(context.getFilesDir());
    }
    Store(File directory) {
        file = new File(directory, "tracker.json");
        try { root = new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)); }
        catch (Exception e) { root = new JSONObject(); }
        if (!root.has("lineups")) put(root, "lineups", new JSONObject());
        if (!root.has("weeks")) put(root, "weeks", new JSONObject());
        if (root.optJSONObject("lineups").length() == 0) put(root.optJSONObject("lineups"), "0000-00-00", lineupJson(new Lineup()));
    }
    public synchronized Lineup lineup(LocalDate week) {
        JSONObject entries = root.optJSONObject("lineups");
        String best = "0000-00-00";
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) { String k = keys.next(); if (k.compareTo(week.toString()) <= 0 && k.compareTo(best) > 0) best = k; }
        return readLineup(entries.optJSONObject(best));
    }
    public synchronized void saveLineup(LocalDate week, Lineup lineup) throws IOException {
        if (week.isBefore(currentWeek())) return;
        put(root.optJSONObject("lineups"), week.toString(), lineupJson(lineup)); save();
    }
    public synchronized void rename(String name) throws IOException {
        JSONObject entries = root.optJSONObject("lineups");
        for (Iterator<String> it = entries.keys(); it.hasNext();) put(entries.optJSONObject(it.next()), "name", name);
        save();
    }
    public synchronized List<Player> directory() {
        ArrayList<Player> out = new ArrayList<>(); JSONArray a = root.optJSONArray("directory");
        if (a != null) for (int i = 0; i < a.length(); i++) { Player p = readPlayer(a.optJSONObject(i)); if (p != null) out.add(p); }
        return out;
    }
    public synchronized void directory(List<Player> players) throws IOException {
        JSONArray a = new JSONArray(); for (Player p : players) a.put(playerJson(p)); put(root, "directory", a); save();
    }
    public synchronized Set<String> weekKeys() {
        Set<String> keys = new TreeSet<>(Collections.reverseOrder()); root.optJSONObject("weeks").keys().forEachRemaining(keys::add); return keys;
    }
    public synchronized Week week(LocalDate date, Lineup lineup) {
        JSONObject obj = root.optJSONObject("weeks").optJSONObject(date.toString());
        if (obj == null) return null;
        Week w = new Week(); w.updated = obj.optLong("updated"); w.frozen = obj.optBoolean("frozen");
        JSONObject players = obj.optJSONObject("players"); if (players == null) return w;
        for (int i = 0; i < 14; i++) {
            Player p = lineup.players[i]; if (p == null) continue;
            JSONArray games = players.optJSONArray(p.id); if (games == null) continue;
            for (int g = 0; g < 2; g++) {
                JSONObject j = games.optJSONObject(g); if (j == null) continue;
                GameLine l = new GameLine();
                try { l.state = State.valueOf(j.optString("state", "PENDING")); } catch (Exception ignored) {}
                l.points = j.optInt("points"); l.eventId = j.optString("event"); l.day = j.optString("day");
                l.opponent = j.optString("opponent"); l.status = j.optString("status"); l.minutes = j.optString("minutes");
                l.reb = j.optString("reb"); l.ast = j.optString("ast"); l.fg = j.optString("fg");
                w.lines[g][i] = l;
            }
        }
        return w;
    }
    public synchronized void saveWeek(LocalDate date, Lineup lineup, Week week) throws IOException {
        JSONObject obj = new JSONObject(), players = new JSONObject();
        put(obj, "updated", week.updated); put(obj, "frozen", week.frozen); put(obj, "players", players);
        for (int i = 0; i < 14; i++) {
            Player p = lineup.players[i]; if (p == null) continue;
            JSONArray a = new JSONArray();
            for (int g = 0; g < 2; g++) {
                GameLine l = week.lines[g][i]; if (l == null) { a.put(JSONObject.NULL); continue; }
                JSONObject j = new JSONObject(); put(j, "state", l.state.name()); put(j, "points", l.points);
                put(j, "event", l.eventId); put(j, "day", l.day); put(j, "opponent", l.opponent); put(j, "status", l.status);
                put(j, "minutes", l.minutes); put(j, "reb", l.reb); put(j, "ast", l.ast); put(j, "fg", l.fg); a.put(j);
            }
            put(players, p.id, a);
        }
        put(root.optJSONObject("weeks"), date.toString(), obj); save();
    }
    private void save() throws IOException {
        File tmp = new File(file.getParentFile(), "tracker.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(root.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
    static JSONObject playerJson(Player p) {
        if (p == null) return null;
        JSONObject j = new JSONObject(); put(j, "id", p.id); put(j, "name", p.name); put(j, "position", p.position); put(j, "teamId", p.teamId); put(j, "team", p.team); return j;
    }
    static Player readPlayer(JSONObject j) { return j == null || j.optString("id").isEmpty() ? null : new Player(j.optString("id"), j.optString("name"), j.optString("position", "G"), j.optString("teamId"), j.optString("team")); }
    static JSONObject lineupJson(Lineup l) { JSONObject j = new JSONObject(); put(j, "name", l.name); JSONArray a = new JSONArray(); for (Player p : l.players) a.put(p == null ? JSONObject.NULL : playerJson(p)); put(j, "players", a); return j; }
    static Lineup readLineup(JSONObject j) { Lineup l = new Lineup(); if (j != null) { l.name = j.optString("name", "My Team"); JSONArray a = j.optJSONArray("players"); if (a != null) for (int i = 0; i < 14; i++) l.players[i] = readPlayer(a.optJSONObject(i)); } return l; }
    static void put(JSONObject j, String key, Object value) { try { j.put(key, value); } catch (JSONException e) { throw new IllegalArgumentException(e); } }
}
