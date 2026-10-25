package com.acbltracker.android;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Public release discovery, verified downloads, then Android's user-approved installer. */
final class UpdateManager implements AutoCloseable {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final String repository = BuildConfig.UPDATE_REPOSITORY;
    private boolean busy;
    private volatile boolean closed;
    private ProgressDialog progress;
    private Future<?> task;
    UpdateManager(Activity activity) { this.activity = activity; prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE); }
    void onResume() {
        if (prefs.getBoolean("awaitingPermission", false)) {
            prefs.edit().remove("awaitingPermission").apply();
            if (activity.getPackageManager().canRequestPackageInstalls()) installPending();
            else toast("To install updates, allow ACBL Tracker to install apps in Android settings.");
            return;
        }
        if (prefs.getBoolean("automatic", true) && System.currentTimeMillis() - prefs.getLong("lastAttempt", 0) >= 24 * 60 * 60 * 1000L) check(false);
    }
    void showSettings() {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL); int p = (int) (20 * activity.getResources().getDisplayMetrics().density); box.setPadding(p, p / 2, p, p / 2);
        TextView text = new TextView(activity); text.setText("Installed: " + BuildConfig.VERSION_NAME + "\n\nUpdates come from " + repository + ". Android will ask you to approve installation. Your roster and scores stay on this phone."); box.addView(text);
        CheckBox automatic = new CheckBox(activity); automatic.setText("Check daily when I open the app"); automatic.setChecked(prefs.getBoolean("automatic", true)); automatic.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("automatic", checked).apply()); box.addView(automatic);
        new AlertDialog.Builder(activity).setTitle("App updates").setView(box).setPositiveButton("Check now", (d, w) -> check(true)).setNegativeButton("Close", null).show();
    }
    private boolean active() { return !closed && !activity.isDestroyed() && !activity.isFinishing(); }
    private void post(Runnable r) { ui.post(() -> { if (active()) r.run(); }); }
    private void toast(String text) { if (active()) Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    void check(boolean manual) {
        if (busy) { if (manual) toast("An update check or download is already in progress."); return; }
        busy = true; prefs.edit().putLong("lastAttempt", System.currentTimeMillis()).apply();
        if (manual) showProgress("Checking GitHub Releases…");
        task = io.submit(() -> {
            try {
                JSONObject release = json("https://api.github.com/repos/" + repository + "/releases/latest");
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new IOException("No stable release available");
                JSONArray assets = release.optJSONArray("assets"); String manifestUrl = "";
                if (assets != null) for (int i = 0; i < assets.length(); i++) { JSONObject a = assets.optJSONObject(i); if (a != null && "update.json".equals(a.optString("name"))) manifestUrl = a.optString("browser_download_url"); }
                if (!ReleaseInfo.trustedAsset(manifestUrl, repository)) throw new IOException("Release has no update manifest");
                ReleaseInfo info = ReleaseInfo.parse(json(manifestUrl), repository);
                post(() -> {
                    busy = false; dismissProgress();
                    if (!info.newerThan(BuildConfig.VERSION_CODE)) { if (manual) toast("You're up to date (" + BuildConfig.VERSION_NAME + ")."); return; }
                    if (!manual && prefs.getLong("notifiedCode", 0) == info.code) return;
                    prefs.edit().putLong("notifiedCode", info.code).apply();
                    new AlertDialog.Builder(activity).setTitle("Update available: " + info.name).setMessage("Download the latest ACBL Tracker?\n\nYour roster and saved scores will stay intact. Android will ask you to confirm installation.").setPositiveButton("Download update", (d, w) -> download(info)).setNegativeButton("Later", null).show();
                });
            } catch (Exception e) { post(() -> { busy = false; dismissProgress(); if (manual) toast("Couldn't check for updates. Check your connection and try again later."); }); }
        });
    }
    private HttpURLConnection connection(String url) throws IOException {
        URL target = new URL(url);
        // GitHub assets redirect to a signed HTTPS CDN URL. Never send credentials or allow HTTP.
        for (int redirects = 0; redirects < 6; redirects++) {
            String host = target.getHost();
            if (!target.getProtocol().equals("https") || !(host.equals("api.github.com") || host.equals("github.com") || host.endsWith(".githubusercontent.com"))) throw new IOException("Unexpected update server");
            HttpURLConnection c = (HttpURLConnection) target.openConnection(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent", "ACBLTracker-Android/" + BuildConfig.VERSION_NAME); c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 200) return c;
            String next = c.getHeaderField("Location"); c.disconnect();
            if (code >= 300 && code < 400 && next != null) { target = new URL(target, next); continue; }
            throw new IOException("Update server returned " + code);
        }
        throw new IOException("Too many update redirects");
    }
    private JSONObject json(String url) throws Exception {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(); if (out.size() + n > 1024 * 1024) throw new IOException("Update response too large"); out.write(buffer, 0, n); }
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        } finally { c.disconnect(); }
    }
    private File apkFile() { return new File(activity.getCacheDir(), "acbl-update.apk"); }
    private void download(ReleaseInfo info) {
        if (busy) return; busy = true; showProgress("Downloading " + info.name + "…");
        task = io.submit(() -> {
            File part = null;
            try {
                part = File.createTempFile("acbl-update-", ".part", activity.getCacheDir());
                HttpURLConnection c = connection(info.apkUrl); long bytes = 0; MessageDigest hash = MessageDigest.getInstance("SHA-256");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[32768]; int n;
                    while ((n = in.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(); bytes += n; if (bytes > info.size || bytes > ReleaseInfo.MAX_APK_BYTES) throw new IOException("APK size mismatch"); hash.update(buffer, 0, n); out.write(buffer, 0, n); }
                    out.getFD().sync();
                } finally { c.disconnect(); }
                if (bytes != info.size || !hex(hash.digest()).equals(info.sha256)) throw new IOException("APK verification failed");
                verifyApk(part, info.code);
                if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                java.nio.file.Files.move(part.toPath(), apkFile().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                prefs.edit().putLong("pendingCode", info.code).putString("pendingHash", info.sha256).apply();
                post(() -> { busy = false; dismissProgress(); requestInstall(); });
            } catch (Exception e) { if (part != null) part.delete(); post(() -> { busy = false; dismissProgress(); if (!(e instanceof InterruptedIOException)) new AlertDialog.Builder(activity).setTitle("Update wasn't installed").setMessage("Couldn't download or verify the update. Your current app and saved data are unchanged. Please try again.").setPositiveButton("OK", null).show(); }); }
        });
    }
    private void requestInstall() {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Allow app updates").setMessage("Android needs your permission for ACBL Tracker to install updates. Enable “Allow from this source” on the next screen, then return here.").setPositiveButton("Open settings", (d, w) -> {
                prefs.edit().putBoolean("awaitingPermission", true).apply();
                try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                catch (ActivityNotFoundException e) { prefs.edit().remove("awaitingPermission").apply(); toast("Open Android settings to allow app installs for ACBL Tracker."); }
            }).setNegativeButton("Later", null).show();
        } else installPending();
    }
    private void installPending() {
        if (busy) return; busy = true; showProgress("Preparing Android installer…");
        task = io.submit(() -> {
            int sessionId = -1;
            try {
                File apk = apkFile(); long expected = prefs.getLong("pendingCode", 0);
                if (expected <= BuildConfig.VERSION_CODE || !apk.isFile()) throw new IOException("No newer APK downloaded");
                MessageDigest hash = MessageDigest.getInstance("SHA-256"); try (InputStream in = new FileInputStream(apk)) { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) hash.update(b, 0, n); }
                if (!hex(hash.digest()).equals(prefs.getString("pendingHash", ""))) throw new IOException("Downloaded APK changed");
                verifyApk(apk, expected);
                PackageInstaller installer = activity.getPackageManager().getPackageInstaller(); PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL); params.setAppPackageName(activity.getPackageName()); params.setSize(apk.length());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                        byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); session.fsync(out);
                    }
                    Intent callback = new Intent(activity, UpdateInstallReceiver.class).setAction("com.acbltracker.UPDATE_RESULT");
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent result = PendingIntent.getBroadcast(activity, sessionId, callback, flags); session.commit(result.getIntentSender());
                }
                post(() -> { busy = false; dismissProgress(); });
            } catch (Exception e) {
                android.util.Log.w("ACBLUpdate", "Couldn't prepare Android installer", e);
                if (sessionId >= 0) { try { activity.getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch (Exception ignored) {} }
                post(() -> { busy = false; dismissProgress(); toast("Couldn't prepare the update. Check free space and try again."); });
            }
        });
    }
    @SuppressWarnings("deprecation")
    private void verifyApk(File file, long expectedCode) throws Exception {
        PackageManager pm = activity.getPackageManager(); int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo downloaded = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags), installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (downloaded == null || !activity.getPackageName().equals(downloaded.packageName)) throw new IOException("Wrong app package");
        long code = Build.VERSION.SDK_INT >= 28 ? downloaded.getLongVersionCode() : downloaded.versionCode;
        if (code != expectedCode || code <= BuildConfig.VERSION_CODE) throw new IOException("Wrong update version");
        Signature[] a = Build.VERSION.SDK_INT >= 28 ? downloaded.signingInfo.getApkContentsSigners() : downloaded.signatures;
        Signature[] b = Build.VERSION.SDK_INT >= 28 ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (a == null || b == null || a.length == 0 || !new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)))) throw new IOException("APK signing key differs");
    }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString(); }
    private void showProgress(String message) {
        progress = new ProgressDialog(activity); progress.setMessage(message); progress.setIndeterminate(true); progress.setCancelable(false); progress.show();
    }
    private void dismissProgress() { if (progress != null) { progress.dismiss(); progress = null; } }
    @Override public void close() { closed = true; if (task != null) task.cancel(true); io.shutdownNow(); ui.removeCallbacksAndMessages(null); dismissProgress(); }
}
