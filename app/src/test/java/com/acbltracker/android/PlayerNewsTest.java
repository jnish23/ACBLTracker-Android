package com.acbltracker.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlayerNewsTest {
    private final League.Player player = new League.Player("3934672", "Jalen Brunson", "G", "18", "NY");
    private JSONObject story(String title, String id, String date) throws Exception {
        return new JSONObject().put("headline", title).put("published", date).put("description", title)
            .put("links", new JSONObject().put("web", new JSONObject().put("href", "http://www.espn.com/nba/story/_/id/" + id)))
            .put("categories", new JSONArray().put(new JSONObject().put("athleteId", "3934672")));
    }
    @Test public void athleteIdsFilterUnrelatedLeagueNewsAndSortNewestFirst() throws Exception {
        JSONObject unrelated = story("Unrelated player news", "2", "2026-10-24T14:00Z").put("categories", new JSONArray().put(new JSONObject().put("athleteId", "1966")));
        JSONObject older = story("Brunson team update", "1", "2026-10-22T19:00:00-07:00"), newer = story("Brunson scores 30", "3", "2026-10-23T21:00Z");
        List<PlayerNews> news = PlayerNews.parse(new JSONObject().put("news", new JSONArray().put(older).put(unrelated).put(newer)), player);
        assertEquals(2, news.size()); assertEquals("Brunson scores 30", news.get(0).headline); assertTrue(news.get(0).url.startsWith("https://www.espn.com/")); assertEquals("", news.get(0).summary);
    }
    @Test public void nestedAthleteIdsAndFullNamesAreSupported() throws Exception {
        JSONObject nested = story("A tagged game recap", "1", "2026-10-23T21:00Z").put("categories", new JSONArray().put(new JSONObject().put("athlete", new JSONObject().put("id", "3934672"))));
        JSONObject named = story("Jalen Brunson: Back at practice", "2", "2026-10-23T20:00Z").put("categories", new JSONArray());
        JSONObject falseName = story("Jalen Brunsonson trade rumors", "3", "2026-10-23T20:00Z").put("categories", new JSONArray());
        assertEquals(2, PlayerNews.parse(new JSONObject().put("news", new JSONArray().put(nested).put(named).put(falseName)), player).size());
    }
    @Test public void fantasyUpdateIsPlayerScopedAndDeduplicated() throws Exception {
        JSONObject fantasy = new JSONObject().put("headline", "Brunson scores 21").put("story", "<p>A brief fantasy update &amp; analysis.</p>").put("published", "Tue Oct 20 19:44:30 PDT 2026");
        JSONObject response = new JSONObject().put("rotowire", fantasy).put("news", new JSONArray().put(story("Brunson scores 21", "1", "2026-10-20T21:00Z")));
        List<PlayerNews> news = PlayerNews.parse(response, player); assertEquals(1, news.size()); assertEquals("ESPN · RotoWire", news.get(0).source); assertEquals("A brief fantasy update & analysis.", news.get(0).summary); assertTrue(news.get(0).published > 0); assertTrue(news.get(0).url.endsWith("/3934672"));
    }
    @Test public void emptyOrMalformedArticlesDoNotCreateFakeNews() throws Exception {
        assertTrue(PlayerNews.parse(new JSONObject(), player).isEmpty());
        JSONObject malformed = new JSONObject().put("news", new JSONArray().put(JSONObject.NULL).put(new JSONObject()).put(story("", "1", "invalid")));
        assertTrue(PlayerNews.parse(malformed, player).isEmpty());
    }
    @Test public void directFantasyUpdateIsProminentAlongsideNewerTaggedVideos() throws Exception {
        JSONObject response = new JSONObject().put("rotowire", new JSONObject().put("headline", "Brunson practice update").put("published", "2026-10-20T20:00Z"))
            .put("news", new JSONArray().put(story("A tagged game video", "1", "2026-10-23T21:00Z")));
        List<PlayerNews> news = PlayerNews.parse(response, player); assertEquals("Brunson practice update", news.get(0).headline); assertEquals(2, news.size());
    }
    @Test public void unsafeUrlsAreRejected() {
        for (String url : new String[] {"javascript:alert(1)", "https://espn.com.evil.test/a", "https://evil.test/a", "https://user@www.espn.com/a", "https://www.espn.com:443/a"}) assertEquals("", PlayerNews.safeUrl(url));
        assertEquals("https://www.espn.com/nba/story/_/id/1", PlayerNews.safeUrl("http://www.espn.com/nba/story/_/id/1"));
    }
    @Test public void newsIsLimitedAndSummariesStayBrief() throws Exception {
        JSONArray articles = new JSONArray();
        for (int i = 0; i < 8; i++) articles.put(story("Headline " + i, "" + i, "2026-10-23T20:00Z").put("description", "Long analysis ".repeat(60)));
        List<PlayerNews> news = PlayerNews.parse(new JSONObject().put("news", articles), player); assertEquals(5, news.size()); assertTrue(news.get(0).summary.length() <= 200);
    }
    @Test public void clientCachesPerPlayerAndRefreshBypassesCache() throws Exception {
        int[] requests = {0};
        try (EspnClient client = new EspnClient() { @Override public JSONObject get(String url) throws Exception { requests[0]++; assertTrue(url.endsWith("/athletes/3934672/overview")); return new JSONObject().put("news", new JSONArray()); } }) {
            client.news(player, false); client.news(player, false); assertEquals(1, requests[0]); client.news(player, true); assertEquals(2, requests[0]);
        }
    }
}
