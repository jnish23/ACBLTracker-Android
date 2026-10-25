package com.acbltracker.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import static com.acbltracker.android.League.PACIFIC;

/** Inexact local alarms: Android may delay them under battery restrictions. */
public final class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "league-deadlines";
    static boolean enabled(Context c) { return c.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getBoolean("reminders", false); }
    public static void schedule(Context c) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        NotificationManager notifications = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "League deadline reminders", NotificationManager.IMPORTANCE_DEFAULT));
        for (int kind = 0; kind < 2; kind++) {
            Intent intent = new Intent(c, ReminderReceiver.class).setAction("com.acbltracker.DEADLINE").putExtra("kind", kind);
            PendingIntent pending = PendingIntent.getBroadcast(c, kind, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.cancel(pending);
            if (!enabled(c) || (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) continue;
            ZonedDateTime now = ZonedDateTime.now(PACIFIC);
            ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(kind == 0 ? DayOfWeek.TUESDAY : DayOfWeek.SUNDAY)).withHour(kind == 0 ? 12 : 20).withMinute(0).withSecond(0).withNano(0);
            if (!next.isAfter(now)) next = next.plusWeeks(1);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending);
        }
    }
    @Override public void onReceive(Context c, Intent intent) {
        if ("com.acbltracker.DEADLINE".equals(intent.getAction()) && enabled(c)) {
            int kind = intent.getIntExtra("kind", 0);
            if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(c, CHANNEL).setSmallIcon(com.acbltracker.android.R.drawable.ic_launcher).setContentTitle(kind == 0 ? "Lineup due in one hour" : "Free agents due in one hour").setContentText(kind == 0 ? "Submit your lineup by 1 PM Pacific. Check league notices for exceptions." : "Free agent requests close at 9 PM Pacific.").setContentIntent(open).setAutoCancel(true).build();
                ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(kind + 1, n);
            }
        }
        schedule(c);
    }
}
