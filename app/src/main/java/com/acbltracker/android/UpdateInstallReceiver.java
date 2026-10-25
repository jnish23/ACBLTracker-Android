package com.acbltracker.android;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = result.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirm); }
                catch (RuntimeException e) { Toast.makeText(context, "Open ACBL Tracker and try the update again.", Toast.LENGTH_LONG).show(); }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String message = status == PackageInstaller.STATUS_FAILURE_ABORTED ? "Update cancelled. Your current app and roster are unchanged." : "Android couldn't install the update. Check free space and try again.";
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
