package com.acbltracker.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static com.acbltracker.android.League.*;

public final class MainActivity extends Activity {
    private static final int BG = 0xff101513, CARD = 0xff1a221e, BORDER = 0xff2c3831, GOLD = 0xffe9b86b,
            WHITE = 0xfff0f3ec, MUTED = 0xff95a399, GREEN = 0xff8bcfa8, RED = 0xffff8d84;
    private Store store;
    private UpdateManager updates;
    private final EspnClient client = new EspnClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocalDate date = currentWeek();
    private Lineup lineup;
    private Week week;
    private List<Player> directory = new ArrayList<>();
    private Map<String, String> injuries = new HashMap<>();
    private LinearLayout root, content;
    private ScrollView scroll;
    private int tab = 0, night = 0, generation = 0;
    private boolean loading, directoryLoading, editing, destroyed;
    private String message = "", directoryError = "";
    private Runnable pickerRefresh;
    private final Runnable poll = new Runnable() {
        @Override public void run() { if (week != null && week.live && !loading) refresh(); handler.postDelayed(this, 60000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        store = new Store(this); directory = store.directory(); updates = new UpdateManager(this);
        if (state != null) { try { date = LocalDate.parse(state.getString("week")); } catch (Exception ignored) {} tab = state.getInt("tab"); night = state.getInt("night"); }
        else { LocalDate today = LocalDate.now(EASTERN); night = (int) java.time.temporal.ChronoUnit.DAYS.between(date, today); }
        loadLocal(); render(); loadDirectory(false); refresh(); ReminderReceiver.schedule(this);
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("week", date.toString()); state.putInt("tab", tab); state.putInt("night", night); }
    @Override protected void onResume() { super.onResume(); handler.postDelayed(poll, 60000); if (store != null && !loading) refresh(); if (updates != null) updates.onResume(); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(poll); }
    @Override protected void onDestroy() { destroyed = true; generation++; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); client.close(); if (updates != null) updates.close(); super.onDestroy(); }
    private void loadLocal() { lineup = store.lineup(date); week = store.week(date, lineup); }
    private boolean editable() { return !date.isBefore(currentWeek()); }
    private void shift(int amount) { generation++; date = date.plusWeeks(amount); night = 0; editing = false; loading = false; message = ""; loadLocal(); render(); refresh(); }
    private void refresh() {
        if (loading || destroyed) return;
        if (week != null && week.frozen) { render(); return; }
        boolean empty = true; for (Player p : lineup.players) if (p != null) empty = false;
        if (empty) { loading = false; render(); return; }
        final int request = ++generation; final LocalDate requestedDate = date; final Lineup requested = lineup.copy();
        loading = true; message = ""; render();
        worker.execute(() -> {
            try {
                Week result = client.week(requestedDate, requested);
                // A roster edit or week change invalidates this request before it can overwrite stored results.
                runOnUiThread(() -> {
                    if (destroyed || request != generation) return;
                    try { store.saveWeek(requestedDate, requested, result); week = result; message = ""; }
                    catch (Exception e) { message = "Scores loaded, but couldn't save on this phone."; week = result; }
                    loading = false; render();
                });
            } catch (Exception e) { android.util.Log.w("ACBL", "ESPN refresh failed", e); runOnUiThread(() -> { if (destroyed || request != generation) return; loading = false; message = week == null ? "Couldn't load ESPN scores. Tap Refresh to retry." : "ESPN unavailable. Showing saved scores."; render(); }); }
        });
    }
    private void loadDirectory(boolean force) {
        if (directoryLoading) return;
        if (!force && !directory.isEmpty()) { worker.execute(this::fetchInjuries); return; }
        directoryLoading = true; directoryError = "";
        worker.execute(() -> {
            try { List<Player> found = client.directory(); store.directory(found);
                runOnUiThread(() -> { if (destroyed) return; directory = found; directoryLoading = false; if (pickerRefresh != null) pickerRefresh.run(); });
            } catch (Exception e) { runOnUiThread(() -> { if (destroyed) return; directoryLoading = false; directoryError = "Couldn't reach ESPN. Check your connection and retry."; if (pickerRefresh != null) pickerRefresh.run(); }); }
            fetchInjuries();
        });
    }
    private void fetchInjuries() { try { Map<String, String> report = client.injuries(); runOnUiThread(() -> { if (!destroyed) { injuries = report; render(); } }); } catch (Exception ignored) {} }
    private void commit(Lineup changed) {
        if (!editable()) return;
        try { store.saveLineup(date, changed); generation++; loading = false; loadLocal(); render(); refresh(); }
        catch (Exception e) { toast("Couldn't save your lineup. Check available storage."); }
    }
    private void render() {
        if (destroyed) return;
        int oldY = scroll == null ? 0 : scroll.getScrollY();
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = row(); header.setPadding(dp(22), dp(16), dp(16), dp(10));
        LinearLayout brand = column(); brand.addView(text("ACBL", 25, WHITE, true)); TextView tag = text("FANTASY BASKETBALL", 9, GOLD, true); tag.setLetterSpacing(.18f); brand.addView(tag);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1)); header.addView(button("↻", "Refresh scores", this::refresh)); header.addView(button("⋯", "Settings and scoring rules", this::settings)); root.addView(header);
        LinearLayout nav = row(); nav.setPadding(dp(15), 0, dp(15), dp(8));
        nav.addView(button("‹", "Previous week", () -> shift(-1)));
        LinearLayout center = column(); TextView range = text(date.format(DateTimeFormatter.ofPattern("MMM d")) + " – " + date.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d, yyyy")), 15, WHITE, true); range.setGravity(Gravity.CENTER); center.addView(range);
        int n = weekNumber(date); TextView label = text((n > 0 ? "WEEK " + n : "PRESEASON") + (date.equals(currentWeek()) ? "  ·  THIS WEEK" : "  ·  TAP FOR CURRENT"), 10, MUTED, true); label.setGravity(Gravity.CENTER); center.addView(label); center.setPadding(0, dp(8), 0, dp(8));
        center.setContentDescription("Return to current week"); center.setOnClickListener(v -> { generation++; date = currentWeek(); night = 0; loading = false; editing = false; loadLocal(); render(); refresh(); });
        nav.addView(center, new LinearLayout.LayoutParams(0, -2, 1)); nav.addView(button("›", "Next week", () -> shift(1))); root.addView(nav);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = column(); content.setPadding(dp(18), dp(4), dp(18), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tab == 0) lineupScreen(); else if (tab == 1) nightlyScreen(); else historyScreen();
        LinearLayout tabs = row(); tabs.setPadding(dp(14), dp(5), dp(14), dp(8)); tabs.setBackgroundColor(CARD);
        String[] names = {"☷  Lineup", "☾  Nightly", "▥  History"};
        for (int i = 0; i < 3; i++) { final int index = i; TextView t = text(names[i], 14, tab == i ? GOLD : MUTED, tab == i); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(52)); t.setBackground(round(tab == i ? 0xff30352a : CARD, 14)); t.setOnClickListener(v -> { tab = index; scroll = null; render(); }); tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); }
        root.addView(tabs); setContentView(root); root.requestApplyInsets(); scroll.post(() -> scroll.scrollTo(0, oldY));
    }
    private void summary() {
        LinearLayout card = column(); card.setPadding(dp(18), dp(17), dp(18), dp(17)); card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xff30392b, 0xff20291f}) {{ setCornerRadius(dp(22)); setStroke(dp(1), BORDER); }});
        LinearLayout title = row(); TextView name = text(lineup.name + "  ✎", 19, WHITE, true); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); name.setOnClickListener(v -> rename()); title.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        title.addView(text(loading ? "SYNCING" : week != null && week.frozen ? "FINAL" : week != null && week.live ? "● LIVE" : "", 10, GREEN, true)); card.addView(title);
        LinearLayout stats = row(); stats.setPadding(0, dp(20), 0, dp(16));
        int g1 = week == null ? 0 : week.total(lineup, 0), g2 = week == null ? 0 : week.total(lineup, 1);
        String[] labels = {"WEEK TOTAL", "GAME 1", "GAME 2", "BONUS"}; int[] values = {g1 + g2, g1, g2, week == null ? 0 : week.bonusTotal(lineup)};
        for (int i = 0; i < 4; i++) { LinearLayout item = column(); item.addView(text(labels[i], 9, MUTED, true)); item.addView(text(week == null ? "—" : String.valueOf(values[i]), i == 0 ? 32 : 28, i == 0 || i == 3 ? GOLD : WHITE, true)); stats.addView(item, new LinearLayout.LayoutParams(0, -2, 1)); }
        card.addView(stats);
        if (!message.isEmpty()) card.addView(text(message, 12, RED, false));
        else if (week != null) card.addView(text(week.frozen ? "Final scores · saved on this phone" : "Updated " + Instant.ofEpochMilli(week.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) + " · scores saved offline", 11, MUTED, false));
        View divider = new View(this); divider.setBackgroundColor(BORDER); LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, dp(1)); d.setMargins(0, dp(14), 0, dp(12)); card.addView(divider, d);
        ZonedDateTime due = date.atTime(13, 0).atZone(PACIFIC);
        if (date.equals(currentWeek()) && !due.isAfter(ZonedDateTime.now(PACIFIC))) due = due.plusWeeks(1);
        card.addView(text(editable() ? "◷  Lineup due " + due.format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")) + " PT" : "Past week · lineup locked", 11, GOLD, false));
        if (week != null && week.benchRegret(lineup) > 0) card.addView(text(week.benchRegret(lineup) + " pts left on bench · compares full starter values", 11, GOLD, false));
        addBlock(card);
    }
    private void lineupScreen() {
        summary();
        LinearLayout heading = row(); heading.setPadding(0, dp(18), 0, dp(10)); heading.addView(text("Your lineup", 22, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (editable()) { heading.addView(smallButton(editing ? "Done" : "Edit", () -> { editing = !editing; render(); })); heading.addView(smallButton("+ Player", () -> picker(-1))); } content.addView(heading);
        if (editing) note("Hold and drag a player to move. Tap a player for move options. Starter positions are fixed.");
        boolean empty = true; for (Player p : lineup.players) if (p != null) empty = false;
        if (empty && editable()) { LinearLayout welcome = panel(); welcome.addView(text("Build your starting five.", 18, GOLD, true)); TextView sub = text("Find your players, assign their league positions, and follow every counting game.", 13, MUTED, false); sub.setPadding(0, dp(8), 0, dp(8)); welcome.addView(sub); welcome.addView(smallButton("Find NBA players  →", () -> picker(-1))); addBlock(welcome); }
        section("STARTERS", "Full points + bonus", 0, 5); section("BENCH", "Next 3 active · half points", 5, 8); section("DEEP BENCH", "Reserves · in priority order", 8, 14);
        note("First two team games · Tuesday–Monday league weeks. Pending games are provisional until the box score is final.");
    }
    private void section(String title, String subtitle, int start, int end) {
        LinearLayout label = row(); label.setPadding(dp(2), dp(17), dp(2), dp(9)); label.addView(text(title, 11, GOLD, true), new LinearLayout.LayoutParams(0, -2, 1)); label.addView(text("G1        G2", 10, MUTED, true)); content.addView(label);
        LinearLayout card = panel(); card.setPadding(0, dp(2), 0, dp(2));
        Result[][] result = week == null ? new Week().results(lineup) : week.results(lineup);
        for (int i = start; i < end; i++) {
            final int slot = i; Player p = lineup.players[i]; LinearLayout r = row(); r.setPadding(dp(13), dp(12), dp(10), dp(12)); r.setMinimumHeight(dp(70));
            TextView badge = text(i < 5 ? POSITIONS[i] : String.format(Locale.US, "%02d", i - 4), 12, p == null ? MUTED : GOLD, true); badge.setGravity(Gravity.CENTER); badge.setBackground(round(0xff28322b, 10)); r.addView(badge, new LinearLayout.LayoutParams(dp(33), dp(33)));
            LinearLayout info = column(); info.setPadding(dp(12), 0, dp(4), 0); TextView name = text(p == null ? "Add " + (i < 5 ? POSITIONS[i] : "player") + "  +" : p.name + (editable() && injuries.containsKey(p.id) ? "  +" : ""), 14, p == null ? MUTED : WHITE, true); name.setMaxLines(2); info.addView(name);
            info.addView(text(p == null ? (editable() ? "Open roster spot" : "Empty slot") : p.team + "  ·  " + p.position + (editing ? "  ·  Hold to move" : ""), 10, MUTED, false)); r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            for (int g = 0; g < 2; g++) { Result rr = result[g][i]; LinearLayout score = column(); score.setGravity(Gravity.CENTER); TextView points = text(p == null ? "—" : scoreLabel(rr), 14, rr.line.state == State.DNP ? RED : rr.bonus > 0 ? GOLD : rr.role == Role.RESERVE ? MUTED : WHITE, true); points.setGravity(Gravity.CENTER); score.addView(points); if (p != null && rr.bonus > 0) score.addView(text("+" + rr.bonus + " bonus", 8, GOLD, true)); else if (p != null && rr.line.state != State.PENDING && rr.line.state != State.DNP) score.addView(text(rr.role == Role.HALF ? "½ pts" : rr.role == Role.RESERVE ? "reserve" : rr.role == Role.PROMOTED ? "↑ full" : rr.line.state == State.LIVE ? "live" : "played", 8, MUTED, false)); r.addView(score, new LinearLayout.LayoutParams(dp(48), -2)); }
            r.setOnClickListener(v -> { if (p == null) { if (editable()) picker(slot); } else if (editing && editable()) playerMenu(slot); else detail(slot); });
            r.setOnLongClickListener(v -> { if (!editable() || p == null) return false; editing = true; v.startDragAndDrop(ClipData.newPlainText("slot", String.valueOf(slot)), new View.DragShadowBuilder(v), null, 0); return true; });
            r.setOnDragListener((v, event) -> { if (!editable()) return false; switch (event.getAction()) { case DragEvent.ACTION_DRAG_STARTED: return event.getClipDescription().hasMimeType("text/plain"); case DragEvent.ACTION_DRAG_ENTERED: v.setBackgroundColor(BORDER); return true; case DragEvent.ACTION_DRAG_EXITED: case DragEvent.ACTION_DRAG_ENDED: v.setBackgroundColor(android.graphics.Color.TRANSPARENT); return true; case DragEvent.ACTION_DROP: try { int from = Integer.parseInt(event.getClipData().getItemAt(0).getText().toString()); Lineup l = lineup.copy(); if (l.move(from, slot)) commit(l); else toast("Starter slots require the same league position."); } catch (Exception ignored) {} return true; default: return true; } });
            card.addView(r); if (i != end - 1) { View line = new View(this); line.setBackgroundColor(BORDER); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.setMargins(dp(58), 0, dp(12), 0); card.addView(line, lp); }
        }
        addBlock(card); TextView hint = text(subtitle, 10, MUTED, false); hint.setPadding(dp(3), dp(5), 0, 0); content.addView(hint);
    }
    private String scoreLabel(Result r) { if (r.line.state == State.PENDING) return "—"; if (r.line.state == State.DNP) return "DNP"; return String.valueOf(r.total()); }
    private void nightlyScreen() {
        summary(); TextView title = text("Nightly courtside", 23, WHITE, true); title.setPadding(0, dp(22), 0, dp(5)); content.addView(title); note("Counting games for your roster · dates in Eastern time");
        HorizontalScrollView days = new HorizontalScrollView(this); days.setHorizontalScrollBarEnabled(false); LinearLayout strip = row();
        for (int i = 0; i < 10; i++) { final int index = i; LocalDate day = date.plusDays(i); LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setPadding(dp(14), dp(12), dp(14), dp(12)); item.setBackground(round(night == i ? GOLD : CARD, 13)); TextView d = text(day.format(DateTimeFormatter.ofPattern("EEE")), 10, night == i ? BG : MUTED, true); item.addView(d); item.addView(text(String.valueOf(day.getDayOfMonth()), 19, night == i ? BG : WHITE, true)); item.setOnClickListener(v -> { night = index; render(); }); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, dp(6), 0); strip.addView(item, lp); } days.addView(strip); addBlock(days); days.post(() -> days.smoothScrollTo(dp(Math.max(0, night - 3) * 60), 0));
        int total = 0, count = 0; Result[][] results = week == null ? new Week().results(lineup) : week.results(lineup);
        for (int i = 0; i < 14; i++) for (int g = 0; g < 2; g++) {
            Player p = lineup.players[i]; Result r = results[g][i]; if (p == null || !r.line.day.equals(date.plusDays(night).toString())) continue;
            count++; total += r.total(); final int slot = i; LinearLayout card = panel(); LinearLayout heading = row(); heading.addView(text(p.name, 16, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1)); heading.addView(text(scoreLabel(r), 24, GOLD, true)); card.addView(heading);
            card.addView(text("G" + (g + 1) + "  ·  " + r.line.opponent + "  ·  " + r.line.status, 12, MUTED, false)); card.addView(text(role(r) + (r.line.state == State.PENDING ? " · provisional" : " · " + r.line.points + " NBA points"), 12, GREEN, false)); card.setOnClickListener(v -> detail(slot)); addBlock(card);
        }
        if (count == 0) { LinearLayout empty = panel(); empty.addView(text(loading ? "Finding tonight’s games…" : "A quiet night.", 20, GOLD, true)); empty.addView(text("No counting games loaded for this date. Add players or refresh to check ESPN.", 13, MUTED, false)); addBlock(empty); }
        else { TextView totalView = text("NIGHT TOTAL    " + total + " PTS", 16, GOLD, true); totalView.setPadding(dp(5), dp(15), 0, 0); content.addView(totalView); }
    }
    private String role(Result r) { return r.role == Role.STARTER ? "Starter · full points" : r.role == Role.PROMOTED ? "Promoted · full points + bonus" : r.role == Role.HALF ? "Playing sub · half rounded up" : "Not counted"; }
    private void historyScreen() {
        TextView title = text("Season archive", 26, WHITE, true); title.setPadding(0, dp(12), 0, dp(8)); content.addView(title); note("Saved weeks stay on your phone. Tap a week to revisit its lineup and results.");
        Set<String> keys = store.weekKeys();
        if (keys.isEmpty()) { LinearLayout empty = panel(); empty.addView(text("Your season starts here.", 20, GOLD, true)); empty.addView(text("Once your roster’s scores load, weekly totals will appear here automatically.", 14, MUTED, false)); addBlock(empty); return; }
        int max = 1; for (String k : keys) { Lineup l = store.lineup(LocalDate.parse(k)); Week w = store.week(LocalDate.parse(k), l); max = Math.max(max, w.total(l, 0) + w.total(l, 1)); }
        for (String key : keys) {
            LocalDate day = LocalDate.parse(key); Lineup l = store.lineup(day); Week w = store.week(day, l); int total = w.total(l, 0) + w.total(l, 1);
            LinearLayout card = panel(); LinearLayout row = row(); row.addView(text("Week " + weekNumber(day), 18, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1)); row.addView(text(total + " pts", 21, GOLD, true)); card.addView(row);
            card.addView(text(day.format(DateTimeFormatter.ofPattern("MMM d")) + " – " + day.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d")) + "  ·  " + (w.frozen ? "FINAL" : "LAST SAVED"), 11, MUTED, true));
            LinearLayout bar = row(); bar.setPadding(0, dp(12), 0, dp(12)); View fill = new View(this); fill.setBackground(round(GOLD, 4)); bar.addView(fill, new LinearLayout.LayoutParams(0, dp(5), Math.max(.01f, (float) total / max))); bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(5), Math.max(.01f, 1f - (float) total / max))); card.addView(bar);
            card.addView(text("G1  " + w.total(l, 0) + "     G2  " + w.total(l, 1) + "     BONUS  +" + w.bonusTotal(l), 12, MUTED, false));
            card.setOnClickListener(v -> { generation++; date = day; tab = 0; loading = false; editing = false; loadLocal(); scroll = null; render(); refresh(); }); addBlock(card);
        }
    }
    private void rename() {
        EditText input = input("Team name"); input.setText(lineup.name); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Name your team").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> { String name = input.getText().toString().trim(); if (name.isEmpty()) return; try { store.rename(name); loadLocal(); render(); } catch (Exception e) { toast("Couldn't save team name."); } }).show();
    }
    private void playerMenu(int slot) {
        Player p = lineup.players[slot]; if (p == null || !editable()) return;
        new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[] {"View player", "Move to…", "Change league position", "Replace player", "Remove player"}, (dialog, which) -> {
            if (which == 0) detail(slot); else if (which == 1) moveMenu(slot); else if (which == 2) changePosition(slot); else if (which == 3) picker(slot);
            else new AlertDialog.Builder(this).setTitle("Remove " + p.name + "?").setMessage("This changes the viewed week’s lineup.").setNegativeButton("Cancel", null).setPositiveButton("Remove", (d, w) -> { Lineup l = lineup.copy(); l.players[slot] = null; commit(l); }).show();
        }).show();
    }
    private void moveMenu(int from) {
        ArrayList<String> labels = new ArrayList<>(); ArrayList<Integer> slots = new ArrayList<>();
        for (int to = 0; to < 14; to++) { Lineup l = lineup.copy(); if (l.move(from, to)) { Player p = lineup.players[to]; slots.add(to); labels.add((to < 5 ? "Starter " + POSITIONS[to] : to < 8 ? "Bench " + (to - 4) : "Deep bench " + (to - 7)) + " · " + (p == null ? "Empty" : p.name)); } }
        new AlertDialog.Builder(this).setTitle("Move " + lineup.players[from].name).setItems(labels.toArray(new String[0]), (d, w) -> { Lineup l = lineup.copy(); if (l.move(from, slots.get(w))) commit(l); }).setNegativeButton("Cancel", null).show();
    }
    private void changePosition(int slot) {
        new AlertDialog.Builder(this).setTitle("League position").setItems(new String[] {"G · Guard", "F · Forward", "C · Center"}, (d, w) -> { String pos = new String[] {"G", "F", "C"}[w]; Lineup l = lineup.copy(); if (!l.accepts(slot, pos)) { toast("Move this player to the bench before changing position."); return; } l.players[slot] = l.players[slot].atPosition(pos); commit(l); }).show();
    }
    private void detail(int slot) {
        Player p = lineup.players[slot]; if (p == null) return;
        LinearLayout body = column(); body.setPadding(dp(22), dp(12), dp(22), dp(12)); body.addView(text(p.team + "  ·  LEAGUE POSITION " + p.position, 12, GOLD, true));
        if (editable() && injuries.containsKey(p.id)) body.addView(text("Injury report: " + injuries.get(p.id), 13, RED, false));
        Result[][] rs = week == null ? new Week().results(lineup) : week.results(lineup);
        for (int g = 0; g < 2; g++) { Result r = rs[g][slot]; TextView t = text("GAME " + (g + 1) + "   " + r.line.opponent + "\n" + r.line.status + "\n" + role(r) + "\n" + (r.line.state == State.DNP ? "DNP · 0 counted points" : r.line.state == State.PENDING ? "Pending · provisional" : r.line.points + " PTS  ·  " + r.line.minutes + " MIN  ·  " + r.line.reb + " REB  ·  " + r.line.ast + " AST\n" + r.total() + " counted points (bonus +" + r.bonus + ")"), 13, WHITE, false); t.setPadding(0, dp(16), 0, dp(16)); body.addView(t); }
        TextView recent = text("RECENT GAMES\nLoading ESPN game log…", 12, MUTED, false); body.addView(recent); ScrollView scroller = new ScrollView(this); scroller.addView(body);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(p.name).setView(scroller).setPositiveButton("Close", null); if (editable()) b.setNeutralButton("Manage player", (d, w) -> playerMenu(slot)); AlertDialog dialog = b.show();
        worker.execute(() -> { try { String games = client.recentGames(p.id); runOnUiThread(() -> { if (dialog.isShowing()) recent.setText("RECENT GAMES\n\n" + games); }); } catch (Exception e) { runOnUiThread(() -> { if (dialog.isShowing()) recent.setText("Recent games unavailable. Saved counting games are shown above."); }); } });
    }
    private void picker(int target) {
        if (!editable()) return; loadDirectory(false);
        LinearLayout body = column(); body.setPadding(dp(16), dp(6), dp(16), dp(8));
        EditText search = input("Search player or NBA team"); search.setSingleLine(true); body.addView(search);
        TextView status = text("", 12, MUTED, false); status.setPadding(dp(4), dp(10), 0, dp(8)); body.addView(status);
        ListView list = new ListView(this); list.setDividerHeight(0); body.addView(list, new LinearLayout.LayoutParams(-1, dp(330)));
        ArrayList<Player> matches = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels); list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add NBA player").setView(body).setNegativeButton("Cancel", null).setNeutralButton("Reload directory", null).create();
        Runnable update = () -> { matches.clear(); labels.clear(); String query = normalized(search.getText().toString()); int count = 0;
            for (Player p : directory) if (!lineup.contains(p.id) && normalized(p.name + " " + p.team).contains(query)) { count++; if (matches.size() < 100) { matches.add(p); labels.add(p.name + "  ·  " + p.team); } }
            status.setText(directoryLoading ? "Loading NBA rosters from ESPN…" : !directoryError.isEmpty() ? directoryError : count == 0 ? "No matching players. Try another name or reload." : count + " players" + (count > 100 ? " · type to narrow the list" : "") + " · choose league position next"); adapter.notifyDataSetChanged();
        }; pickerRefresh = update;
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); } public void afterTextChanged(Editable e) {} });
        list.setOnItemClickListener((parent, view, position, id) -> {
            Player p = matches.get(position);
            new AlertDialog.Builder(this).setTitle(p.name + " · league position").setItems(new String[] {"G · Guard", "F · Forward", "C · Center"}, (d, w) -> {
                String pos = new String[] {"G", "F", "C"}[w]; int slot = target < 0 ? lineup.firstOpen(pos) : target;
                if (slot < 0) { toast("No open slot for this position. Replace or move a player first."); return; }
                if (!lineup.accepts(slot, pos)) { toast("This starter spot requires " + POSITIONS[slot] + "."); return; }
                if (lineup.contains(p.id)) { toast("This player is already on your roster."); return; }
                Lineup l = lineup.copy(); l.players[slot] = p.atPosition(pos); dialog.dismiss(); commit(l);
            }).setNegativeButton("Cancel", null).show();
        });
        dialog.setOnDismissListener(d -> { pickerRefresh = null; ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0); });
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { loadDirectory(true); update.run(); }); update.run();
    }
    private void settings() {
        boolean enabled = getPreferences(MODE_PRIVATE).getBoolean("reminders", false);
        new AlertDialog.Builder(this).setTitle("ACBL Tracker").setItems(new String[] {"Scoring rules", enabled ? "Turn off deadline reminders" : "Turn on deadline reminders", "Refresh NBA player directory", "App updates", "About this Android port"}, (d, which) -> {
            if (which == 0) new AlertDialog.Builder(this).setTitle("How your points count").setMessage("5 starters: G, G, F, F, C.\n3 playing subs + 6 deep reserves.\n\nEach NBA team’s first 2 eligible games count. Weeks begin Tuesday in Eastern time; the original app’s 10-day schedule window allows a second game to spill past Monday.\n\nStarters get full points, plus +2 for 30–39 or +5 for 40+.\n\nA DNP starter is replaced by the first active sub at the same position, bench first. A promoted sub gets full points and bonus.\n\nThe next 3 active subs get half points, rounded up, without bonus. Remaining reserves score zero.\n\nA played 0 is different from DNP. Pending games remain provisional. The NBA Cup championship is excluded.\n\nLineups are editable for current/future weeks. Past weeks are view-only. Final weeks save locally after the correction window.\n\nLineups are normally due Tuesday 1 PM Pacific; free agents Sunday 9 PM Pacific. Check league announcements for exceptions.").setPositiveButton("Got it", null).show();
            else if (which == 1) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("reminders", !enabled).apply();
                if (!enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 42);
                ReminderReceiver.schedule(this); toast(enabled ? "Reminders turned off" : "Reminders enabled for one hour before deadlines.");
            } else if (which == 2) { loadDirectory(true); toast("Refreshing NBA directory…"); }
            else if (which == 3) updates.showSettings();
            else new AlertDialog.Builder(this).setTitle("ACBL Tracker for Android").setMessage("Native Android port based on jfung-dev/ACBLTracker.\n\nOriginal project: github.com/jfung-dev/ACBLTracker\n\nESPN’s unofficial public feeds supply scores and rosters. Not affiliated with ESPN, the NBA, or USACBL.\n\nYour lineups and saved results stay on this phone. No account or subscription. Uninstalling erases this data.\n\nVersion " + BuildConfig.VERSION_NAME).setPositiveButton("Close", null).show();
        }).setNegativeButton("Close", null).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); if (request == 42 && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) { getPreferences(MODE_PRIVATE).edit().putBoolean("reminders", false).apply(); toast("Notifications weren't allowed. Reminders remain off."); } ReminderReceiver.schedule(this); }
    private String normalized(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.US).trim(); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout panel() { LinearLayout l = column(); l.setPadding(dp(16), dp(16), dp(16), dp(16)); l.setBackground(round(CARD, 18)); return l; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String label, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(label); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); t.setLineSpacing(dp(2), 1); return t; }
    private TextView button(String label, String description, Runnable action) { TextView b = text(label, 28, GOLD, false); b.setGravity(Gravity.CENTER); b.setMinWidth(dp(48)); b.setMinHeight(dp(48)); b.setContentDescription(description); b.setOnClickListener(v -> action.run()); return b; }
    private TextView smallButton(String label, Runnable action) { TextView b = text(label, 12, GOLD, true); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(48)); b.setPadding(dp(12), dp(10), dp(12), dp(10)); b.setOnClickListener(v -> action.run()); return b; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16); e.setTextColor(WHITE); e.setHintTextColor(MUTED); e.setPadding(dp(15), dp(12), dp(15), dp(12)); return e; }
    private void note(String label) { TextView t = text(label, 12, MUTED, false); t.setPadding(dp(2), dp(6), dp(2), dp(13)); content.addView(t); }
    private void addBlock(View v) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(5)); content.addView(v, lp); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
