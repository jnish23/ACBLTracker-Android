package com.acbltracker.android;

import org.json.*;
import java.net.URI;
import java.io.IOException;
import java.util.Locale;

/** Validates the small manifest accompanying each published APK. */
final class ReleaseInfo {
    static final long MAX_APK_BYTES = 64L * 1024 * 1024;
    final long code, size;
    final String name, apkUrl, sha256;
    ReleaseInfo(long code, String name, String apkUrl, String sha256, long size) {
        this.code = code; this.name = name; this.apkUrl = apkUrl; this.sha256 = sha256; this.size = size;
    }
    static ReleaseInfo parse(JSONObject j, String repository) throws IOException {
        long code = j.optLong("versionCode", -1), size = j.optLong("size", -1);
        String name = j.optString("versionName"), url = j.optString("apkUrl"), hash = j.optString("sha256").toLowerCase(Locale.ROOT);
        if (code < 1 || code > Integer.MAX_VALUE || size < 1 || size > MAX_APK_BYTES || name.isEmpty() || name.length() > 80 || !hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid update manifest");
        if (!trustedAsset(url, repository) || !url.endsWith("/ACBLTracker-android.apk")) throw new IOException("Unexpected APK location");
        return new ReleaseInfo(code, name, url, hash, size);
    }
    static boolean trustedAsset(String url, String repository) {
        try {
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return false;
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost()) && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPath().startsWith("/" + repository + "/releases/download/") && !uri.getPath().contains("..") && !uri.getRawPath().contains("%");
        } catch (Exception e) { return false; }
    }
    boolean newerThan(long installed) { return code > installed; }
}
