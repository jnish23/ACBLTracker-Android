package com.acbltracker.android;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
import static com.acbltracker.android.League.*;

public class LeagueTest {
    private Lineup lineup() {
        Lineup l = new Lineup(); String[] positions = {"G", "G", "F", "F", "C", "G", "F", "C", "F", "G", "G", "C", "F", "G"};
        for (int i = 0; i < 14; i++) l.players[i] = new Player("" + i, "Player " + i, positions[i], "1", "BOS");
        return l;
    }
    private GameLine[] played() { GameLine[] lines = new GameLine[14]; for (int i = 0; i < 14; i++) lines[i] = new GameLine(State.PLAYED, 10); return lines; }
    @Test public void starterBonusBoundaries() { int[] points = {0,29,30,39,40,52}; int[] expected = {0,0,2,2,5,5}; for (int i = 0; i < points.length; i++) assertEquals(expected[i], bonus(points[i])); }
    @Test public void fullStarterPointsAndBonus() { GameLine[] a = played(); a[0].points = 34; Result r = score(lineup(), a)[0]; assertEquals(Role.STARTER, r.role); assertEquals(36, r.total()); }
    @Test public void halfRoundedUpWithoutBonus() { GameLine[] a = played(); a[5].points = 17; a[6].points = 31; a[7].points = 40; Result[] r = score(lineup(), a); assertEquals(9, r[5].total()); assertEquals(16, r[6].total()); assertEquals(20, r[7].total()); assertEquals(0, r[6].bonus); assertEquals(0, r[8].total()); }
    @Test public void dnpPromotesSamePositionAndBackfillsBench() { GameLine[] a = played(); a[0].state = State.DNP; a[5].points = 40; Result[] r = score(lineup(), a); assertEquals(Role.PROMOTED, r[5].role); assertEquals(45, r[5].total()); assertEquals(0, r[5].replacing); assertEquals(Role.HALF, r[8].role); assertEquals(Role.RESERVE, r[9].role); }
    @Test public void promotionSkipsInactiveAndUsesDeepBench() { GameLine[] a = played(); a[0].state = State.DNP; a[5].state = State.DNP; Result[] r = score(lineup(), a); assertEquals(Role.PROMOTED, r[9].role); assertEquals(Role.RESERVE, r[5].role); }
    @Test public void twoStartersCannotShareReplacement() { GameLine[] a = played(); a[0].state = a[1].state = State.DNP; Result[] r = score(lineup(), a); assertEquals(Role.PROMOTED, r[5].role); assertEquals(Role.PROMOTED, r[9].role); assertEquals(0, r[5].replacing); assertEquals(1, r[9].replacing); }
    @Test public void noPositionReplacementScoresZero() { Lineup l = lineup(); for (int i = 5; i < 14; i++) l.players[i] = l.players[i].atPosition("F"); GameLine[] a = played(); a[4].state = State.DNP; Result[] r = score(l, a); assertEquals(0, r[4].total()); for (Result s : r) assertNotEquals(Role.PROMOTED, s.role); }
    @Test public void benchDnpBackfillsFromDeep() { GameLine[] a = played(); a[6].state = State.DNP; Result[] r = score(lineup(), a); assertEquals(Role.HALF, r[8].role); assertEquals(Role.RESERVE, r[6].role); }
    @Test public void pendingIsProvisionalAndZeroPlayedIsNotDnp() { GameLine[] a = played(); a[0] = null; a[1].points = 0; Result[] r = score(lineup(), a); assertEquals(Role.STARTER, r[0].role); assertEquals(State.PENDING, r[0].line.state); assertEquals(Role.STARTER, r[1].role); assertEquals(State.PLAYED, r[1].line.state); }
    @Test public void emptySlotsNeverCountOrPromote() { Lineup l = new Lineup(); l.players[0] = new Player("1", "Guard", "G", "1", "BOS"); GameLine[] a = played(); a[0].points = 35; Result[] r = score(l, a); int counted = 0; for (Result x : r) if (x.role != Role.RESERVE) counted++; assertEquals(1, counted); assertEquals(37, r[0].total()); }
    @Test public void positionLocksAndSwap() { Lineup l = lineup(); assertFalse(l.move(0, 2)); assertEquals("0", l.players[0].id); assertTrue(l.move(5, 0)); assertEquals("5", l.players[0].id); assertEquals("0", l.players[5].id); assertFalse(l.move(-1, 2)); assertFalse(l.move(2, 14)); }
    @Test public void reserveMoveInsertsInPriorityOrder() { Lineup l = lineup(); assertTrue(l.move(8, 5)); assertEquals("8", l.players[5].id); assertEquals("5", l.players[6].id); assertEquals("7", l.players[8].id); assertTrue(l.move(5, 8)); for (int i = 5; i < 14; i++) assertEquals("" + i, l.players[i].id); }
    @Test public void openSlotsPreferPositionThenBench() { Lineup l = new Lineup(); assertEquals(4, l.firstOpen("C")); l.players[0] = lineup().players[0]; l.players[1] = lineup().players[1]; assertEquals(5, l.firstOpen("G")); }
    @Test public void weeksAreTuesdayBased() { assertEquals(LocalDate.of(2026,10,20), weekStart(LocalDate.of(2026,10,26))); assertEquals(LocalDate.of(2026,10,27), weekStart(LocalDate.of(2026,10,27))); assertEquals(1, weekNumber(LocalDate.of(2026,10,20))); assertEquals(0, weekNumber(LocalDate.of(2026,10,13))); }
    @Test public void easternDateAcrossMidnightAndDst() { assertEquals(LocalDate.of(2026,10,26), Instant.parse("2026-10-27T02:00:00Z").atZone(EASTERN).toLocalDate()); assertEquals(LocalDate.of(2026,10,27), weekStart(Instant.parse("2026-11-02T03:00:00Z").atZone(EASTERN).toLocalDate())); }
    @Test public void scoringIndependentForEachGame() { Week w = new Week(); w.lines[0] = played(); w.lines[1] = played(); w.lines[0][0].state = State.DNP; Result[][] r = w.results(lineup()); assertEquals(Role.PROMOTED, r[0][5].role); assertEquals(Role.HALF, r[1][5].role); }
    @Test public void jsonLineupRoundTripPreservesLeaguePositions() { Lineup l = lineup(); l.name = "Brun & Gun"; Lineup restored = Store.readLineup(Store.lineupJson(l)); assertEquals(l.name, restored.name); assertEquals("C", restored.players[4].position); assertEquals("13", restored.players[13].id); }
    @Test public void boxMinutesRecognizePartialMinute() { assertTrue(EspnClient.minutesPositive("0:32")); assertTrue(EspnClient.minutesPositive("32")); assertFalse(EspnClient.minutesPositive("0:00")); assertFalse(EspnClient.minutesPositive("--")); }
    @Test public void benchRegretUsesEachReserveOnlyOncePerGame() { Lineup l = lineup(); Week w = new Week(); w.lines[0] = played(); w.lines[0][5].points = 40; assertEquals(35, w.benchRegret(l)); }
    @Test public void reserveNbaPointsAreVisibleWithoutChangingTotals() {
        Lineup l = lineup(); Week w = new Week(); w.lines[0] = played(); w.lines[0][8].points = 43;
        Result r = w.results(l)[0][8]; assertEquals(Role.RESERVE, r.role); assertEquals("43", r.displayLabel()); assertEquals(43, r.displayPoints()); assertEquals(0, r.total()); assertEquals(65, w.total(l, 0)); assertEquals(0, w.bonusTotal(l));
    }
    @Test public void deepBenchBackfillDisplaysOnlyCountedHalfPoints() {
        Lineup l = lineup(); Week w = new Week(); w.lines[0] = played(); w.lines[0][6].state = State.DNP; w.lines[0][8].points = 33;
        Result r = w.results(l)[0][8]; assertEquals(Role.HALF, r.role); assertEquals("17", r.displayLabel()); assertEquals(17, r.total()); assertEquals(77, w.total(l, 0));
    }
    @Test public void deepBenchPromotionDisplaysFullPointsAndBonus() {
        Lineup l = lineup(); GameLine[] a = played(); a[0].state = a[5].state = State.DNP; a[9].points = 40;
        Result r = score(l, a)[9]; assertEquals(Role.PROMOTED, r.role); assertEquals("45", r.displayLabel()); assertEquals(45, r.total());
    }
    @Test public void reserveLabelsDistinguishZeroPendingLiveAndDnp() {
        GameLine[] a = played(); a[8].points = 0; assertEquals("0", score(lineup(), a)[8].displayLabel());
        a[8] = new GameLine(State.LIVE, 12); assertEquals("12", score(lineup(), a)[8].displayLabel());
        a[8].state = State.PENDING; assertEquals("—", score(lineup(), a)[8].displayLabel());
        a[8].state = State.DNP; assertEquals("DNP", score(lineup(), a)[8].displayLabel());
    }
}
