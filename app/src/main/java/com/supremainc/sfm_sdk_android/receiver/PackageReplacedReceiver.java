package com.supremainc.sfm_sdk_android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Listens for ACTION_MY_PACKAGE_REPLACED — fired by Android after a silent
 * self-update completes. Runs in the newly installed process, so it can
 * reliably relaunch the app.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

        Log.i(TAG, "Package replaced — relaunching app");

        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(launchIntent);
        }
    }
}
