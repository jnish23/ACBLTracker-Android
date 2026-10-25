package com.acbltracker.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.time.*;
import static org.junit.Assert.*;
import static com.acbltracker.android.League.*;

public class StoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Player guard(String id) { return new Player(id, "Guard " + id, "G", "2", "BOS"); }
    @Test public void snapshotsCarryForwardButNeverRewritePriorWeeks() throws Exception {
        Store s = new Store(folder.getRoot()); LocalDate now = currentWeek(); Lineup l = s.lineup(now); l.players[0] = guard("1"); s.saveLineup(now, l);
        Lineup next = s.lineup(now.plusWeeks(1)); assertEquals("1", next.players[0].id); next.players[0] = guard("2"); s.saveLineup(now.plusWeeks(1), next);
        assertEquals("1", s.lineup(now).players[0].id); assertEquals("2", s.lineup(now.plusWeeks(2)).players[0].id); assertNull(s.lineup(now.minusWeeks(1)).players[0]);
        Store reopened = new Store(folder.getRoot()); assertEquals("2", reopened.lineup(now.plusWeeks(1)).players[0].id);
    }
    @Test public void pastWeekEditsAreRejected() throws Exception {
        Store s = new Store(folder.getRoot()); Lineup l = new Lineup(); l.players[0] = guard("1"); s.saveLineup(currentWeek().minusWeeks(1), l); assertNull(s.lineup(currentWeek().minusWeeks(1)).players[0]);
    }
    @Test public void teamRenameAppliesAcrossSnapshots() throws Exception {
        Store s = new Store(folder.getRoot()); s.saveLineup(currentWeek(), new Lineup()); s.saveLineup(currentWeek().plusWeeks(1), new Lineup()); s.rename("New name"); assertEquals("New name", s.lineup(currentWeek().minusWeeks(1)).name); assertEquals("New name", s.lineup(currentWeek().plusWeeks(1)).name);
    }
    @Test public void cachedGamesFollowPlayerIdentityAfterMove() throws Exception {
        Store s = new Store(folder.getRoot()); Lineup l = new Lineup(); l.players[0] = guard("1"); l.players[5] = guard("2");
        Week w = new Week(); w.lines[0][0] = new GameLine(State.PLAYED, 31); w.lines[0][5] = new GameLine(State.PLAYED, 17); w.updated = 123; s.saveWeek(currentWeek(), l, w);
        l.move(0, 5); Week restored = s.week(currentWeek(), l); assertEquals(17, restored.lines[0][0].points); assertEquals(31, restored.lines[0][5].points); assertEquals(123, restored.updated);
        // The other G starter spot is empty, so the reserve is promoted at full value.
        assertEquals(17 + 33, restored.total(l, 0));
    }
    @Test public void frozenWeekSurvivesRestartWithDetailedStats() throws Exception {
        Store s = new Store(folder.getRoot()); Lineup l = new Lineup(); l.players[0] = guard("1"); Week w = new Week(); w.frozen = true; GameLine g = new GameLine(State.PLAYED, 42); g.minutes = "32"; g.eventId = "99"; g.day = currentWeek().toString(); g.ast = "8"; w.lines[0][0] = g; s.saveWeek(currentWeek(), l, w);
        Week restored = new Store(folder.getRoot()).week(currentWeek(), l); assertTrue(restored.frozen); assertEquals(47, restored.total(l, 0)); assertEquals("8", restored.lines[0][0].ast); assertEquals("99", restored.lines[0][0].eventId);
    }
}
