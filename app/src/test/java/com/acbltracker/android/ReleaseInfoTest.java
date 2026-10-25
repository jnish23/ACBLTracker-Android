package com.acbltracker.android;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class ReleaseInfoTest {
    private static final String REPO = "jnish23/ACBLTracker-Android";
    private JSONObject manifest() throws Exception {
        return new JSONObject().put("versionCode", 1001).put("versionName", "1.1.1").put("size", 123456).put("sha256", "ab".repeat(32)).put("apkUrl", "https://github.com/" + REPO + "/releases/download/v1.1.1/ACBLTracker-android.apk");
    }
    @Test public void validManifestAndNumericVersionComparison() throws Exception { ReleaseInfo r = ReleaseInfo.parse(manifest(), REPO); assertEquals("1.1.1", r.name); assertTrue(r.newerThan(2)); assertFalse(r.newerThan(1001)); assertFalse(r.newerThan(1002)); }
    @Test public void checksumIsNormalized() throws Exception { assertEquals("ab".repeat(32), ReleaseInfo.parse(manifest().put("sha256", "AB".repeat(32)), REPO).sha256); }
    @Test public void untrustedAssetUrlsRejected() {
        String[] bad = {"http://github.com/" + REPO + "/releases/download/v1/app.apk", "https://github.com.evil.test/" + REPO + "/releases/download/v1/app.apk", "https://github.com/other/repo/releases/download/v1/app.apk", "https://evil@github.com/" + REPO + "/releases/download/v1/app.apk", "https://github.com/" + REPO + "/releases/download/../app.apk", "https://github.com/" + REPO + "/releases/download/%2e%2e/app.apk", "https://github.com:443/" + REPO + "/releases/download/v1/app.apk"};
        for (String url : bad) assertFalse(url, ReleaseInfo.trustedAsset(url, REPO));
    }
    @Test public void invalidManifestsRejected() throws Exception {
        String[] fields = {"versionCode", "versionCode", "size", "size", "sha256", "versionName", "apkUrl"};
        Object[] values = {0, 2147483648L, 0, ReleaseInfo.MAX_APK_BYTES + 1, "wrong", "", "https://github.com/" + REPO + "/releases/download/v1/wrong.apk"};
        for (int i = 0; i < fields.length; i++) { try { ReleaseInfo.parse(manifest().put(fields[i], values[i]), REPO); fail(fields[i] + " should fail"); } catch (IOException expected) {} }
    }
    @Test public void tagNamesDoNotDetermineVersionOrder() throws Exception { ReleaseInfo r = ReleaseInfo.parse(manifest().put("versionName", "1.10.0"), REPO); assertTrue(r.newerThan(1000)); }
}
