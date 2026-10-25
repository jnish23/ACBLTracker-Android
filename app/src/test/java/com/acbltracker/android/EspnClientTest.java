package com.acbltracker.android;

import org.json.*;
import org.junit.Test;
import java.io.IOException;
import java.time.*;
import static org.junit.Assert.*;
import static com.acbltracker.android.League.*;

public class EspnClientTest {
    private JSONObject event(String id, String date, String state, String notes) throws Exception {
        return new JSONObject("{\"id\":\"" + id + "\",\"date\":\"" + date + "T23:00:00Z\",\"status\":{\"type\":{\"name\":\"STATUS_FINAL\",\"state\":\"" + state + "\",\"shortDetail\":\"Final\"}},\"competitions\":[{\"notes\":[{\"headline\":\"" + notes + "\"}],\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"1\",\"abbreviation\":\"BOS\"}},{\"homeAway\":\"away\",\"team\":{\"id\":\"2\",\"abbreviation\":\"NYK\"}}]}]}");
    }
    private JSONObject box(String minutes) throws Exception {
        return new JSONObject("{\"boxscore\":{\"players\":[{\"statistics\":[{\"labels\":[\"MIN\",\"PTS\",\"REB\",\"AST\"],\"athletes\":[{\"athlete\":{\"id\":\"11\"},\"stats\":[\"" + minutes + "\",\"0\",\"2\",\"3\"]}]}]}]}}");
    }
    private Lineup lineup() { Lineup l = new Lineup(); l.players[0] = new Player("11", "Guard", "G", "1", "BOS"); return l; }
    private EspnClient fake(JSONArray events, JSONObject box) {
        return new EspnClient() { @Override public JSONObject get(String url) throws Exception { return url.contains("/summary") ? box : new JSONObject().put("events", events); } };
    }
    @Test public void onlyFirstTwoEligibleGamesCountIncludingSpillover() throws Exception {
        LocalDate d = currentWeek().minusWeeks(3); JSONArray events = new JSONArray(); events.put(event("cup", d.toString(), "post", "NBA Cup Championship")); events.put(event("a", d.plusDays(2).toString(), "post", "")); events.put(event("b", d.plusDays(8).toString(), "post", "")); events.put(event("c", d.plusDays(9).toString(), "post", ""));
        try (EspnClient c = fake(events, box("0:32"))) { Week w = c.week(d, lineup()); assertEquals("a", w.lines[0][0].eventId); assertEquals("b", w.lines[1][0].eventId); assertTrue(w.frozen); assertEquals(State.PLAYED, w.lines[0][0].state); assertEquals(0, w.lines[0][0].points); }
    }
    @Test public void liveMissingPlayerIsPendingNotDnp() throws Exception {
        LocalDate d = currentWeek(); JSONArray events = new JSONArray().put(event("a", d.toString(), "in", "")); JSONObject b = box("0");
        try (EspnClient c = fake(events, b)) { Week w = c.week(d, lineup()); assertTrue(w.live); assertEquals(State.PENDING, w.lines[0][0].state); assertFalse(w.frozen); }
    }
    @Test public void finalZeroMinutesIsDnp() throws Exception { LocalDate d = currentWeek(); try (EspnClient c = fake(new JSONArray().put(event("a", d.toString(), "post", "")), box("0:00"))) { assertEquals(State.DNP, c.week(d, lineup()).lines[0][0].state); } }
    @Test public void espnTimestampsWithoutSecondsAreAccepted() throws Exception { LocalDate d = currentWeek(); JSONObject e = event("a", d.toString(), "pre", ""); e.put("date", d + "T23:00Z"); try (EspnClient c = fake(new JSONArray().put(e), box("30"))) { assertEquals(d.toString(), c.week(d, lineup()).lines[0][0].day); } }
    @Test public void missingBoxNeverBecomesDnpOrFreezes() throws Exception { LocalDate d = currentWeek().minusWeeks(3); try (EspnClient c = fake(new JSONArray().put(event("a", d.toString(), "post", "")), new JSONObject())) { try { c.week(d, lineup()); fail("Incomplete box must fail refresh"); } catch (java.util.concurrent.ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); } } }
    @Test public void incompleteScheduleFailsEntireRefresh() throws Exception {
        try (EspnClient c = new EspnClient() { @Override public JSONObject get(String url) { return new JSONObject(); } }) { try { c.week(currentWeek(), lineup()); fail("Malformed schedule must fail"); } catch (java.util.concurrent.ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); } }
    }
}
