package com.acbltracker.android;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Pure Java league rules, independent of Android and the data provider. */
public final class League {
    public static final ZoneId EASTERN = ZoneId.of("America/New_York");
    public static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    public static final String[] POSITIONS = {"G", "G", "F", "F", "C"};
    public static final LocalDate SEASON_START = LocalDate.of(2026, 10, 20);
    public static LocalDate currentWeek() { return weekStart(LocalDate.now(EASTERN)); }
    public static LocalDate weekStart(LocalDate day) { return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY)); }
    public static int weekNumber(LocalDate day) { long days = ChronoUnit.DAYS.between(SEASON_START, day); return days < 0 ? 0 : (int) days / 7 + 1; }
    public static int bonus(int points) { return points >= 40 ? 5 : points >= 30 ? 2 : 0; }

    public static final class Player {
        public String id, name, position, teamId, team;
        public Player(String id, String name, String position, String teamId, String team) {
            this.id = id; this.name = name; this.position = position; this.teamId = teamId; this.team = team;
        }
        public Player atPosition(String pos) { return new Player(id, name, pos, teamId, team); }
    }
    public static final class Lineup {
        public String name = "My Team";
        public final Player[] players = new Player[14];
        public Lineup copy() { Lineup l = new Lineup(); l.name = name; System.arraycopy(players, 0, l.players, 0, 14); return l; }
        public boolean contains(String id) { for (Player p : players) if (p != null && p.id.equals(id)) return true; return false; }
        public int firstOpen(String pos) { for (int i = 0; i < 14; i++) if (players[i] == null && accepts(i, pos)) return i; return -1; }
        public boolean accepts(int slot, String pos) { return slot >= 0 && slot < 14 && (slot >= 5 || POSITIONS[slot].equals(pos)); }
        public boolean move(int from, int to) {
            if (from < 0 || from >= 14 || to < 0 || to >= 14 || from == to) return false;
            Player a = players[from], b = players[to];
            if (from < 5 || to < 5) {
                if ((a != null && !accepts(to, a.position)) || (b != null && !accepts(from, b.position))) return false;
                players[from] = b; players[to] = a;
            } else {
                if (from < to) System.arraycopy(players, from + 1, players, from, to - from);
                else System.arraycopy(players, to, players, to + 1, from - to);
                players[to] = a;
            }
            return true;
        }
    }
    public enum State { PENDING, LIVE, PLAYED, DNP }
    public enum Role { STARTER, PROMOTED, HALF, RESERVE }
    public static final class GameLine {
        public State state = State.PENDING;
        public int points;
        public String eventId = "", day = "", opponent = "", status = "", minutes = "", reb = "", ast = "", fg = "";
        public GameLine() {}
        public GameLine(State state, int points) { this.state = state; this.points = points; }
    }
    public static final class Result {
        public Role role = Role.RESERVE;
        public GameLine line;
        public int counted, bonus, replacing = -1;
        public int total() { return counted + bonus; }
        /** Reserve NBA points are visible for comparison, but never become counted points. */
        public int displayPoints() { return role == Role.RESERVE ? line.points : total(); }
        public String displayLabel() { return line.state == State.PENDING ? "—" : line.state == State.DNP ? "DNP" : String.valueOf(displayPoints()); }
    }
    public static Result[] score(Lineup lineup, GameLine[] lines) {
        Result[] r = new Result[14];
        for (int i = 0; i < 14; i++) {
            r[i] = new Result();
            r[i].line = lineup.players[i] == null ? new GameLine(State.DNP, 0) : lines[i] == null ? new GameLine() : lines[i];
        }
        for (int i = 0; i < 5; i++) {
            if (r[i].line.state != State.DNP) full(r[i], Role.STARTER);
            else {
                for (int j = 5; j < 14; j++) {
                    Player p = lineup.players[j];
                    if (p != null && POSITIONS[i].equals(p.position) && r[j].role != Role.PROMOTED && r[j].line.state != State.DNP) {
                        full(r[j], Role.PROMOTED); r[j].replacing = i; break;
                    }
                }
            }
        }
        int subs = 0;
        for (int i = 5; i < 14; i++) {
            if (r[i].role == Role.PROMOTED || r[i].line.state == State.DNP) continue;
            if (subs++ < 3) { r[i].role = Role.HALF; r[i].counted = (r[i].line.points + 1) / 2; }
        }
        return r;
    }
    private static void full(Result r, Role role) { r.role = role; r.counted = r.line.points; r.bonus = bonus(r.counted); }
    public static final class Week {
        public GameLine[][] lines = new GameLine[2][14];
        public long updated;
        public boolean frozen, live;
        public Result[][] results(Lineup l) { return new Result[][] { score(l, lines[0]), score(l, lines[1]) }; }
        public int total(Lineup l, int game) { int t = 0; for (Result r : score(l, lines[game])) t += r.total(); return t; }
        public int bonusTotal(Lineup l) { int t = 0; for (Result[] game : results(l)) for (Result r : game) t += r.bonus; return t; }
        /** Same comparison as the original: best unused reserve gain per starter, ignoring half-point offsets. */
        public int benchRegret(Lineup l) {
            int total = 0;
            for (Result[] game : results(l)) {
                Set<Integer> taken = new HashSet<>();
                for (int i = 0; i < 5; i++) {
                    if (l.players[i] == null || game[i].role != Role.STARTER || game[i].line.state != State.PLAYED) continue;
                    int best = -1, gain = 0;
                    for (int j = 5; j < 14; j++) {
                        Player p = l.players[j]; Result r = game[j];
                        if (p == null || taken.contains(j) || !p.position.equals(POSITIONS[i]) || r.role == Role.PROMOTED || r.line.state != State.PLAYED) continue;
                        int difference = r.line.points + bonus(r.line.points) - game[i].total();
                        if (difference > gain) { best = j; gain = difference; }
                    }
                    if (best >= 0) { taken.add(best); total += gain; }
                }
            }
            return total;
        }
    }
}
