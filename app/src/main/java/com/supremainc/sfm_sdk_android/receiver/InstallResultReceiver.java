package com.supremainc.sfm_sdk_android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * Receives the result of a silent PackageInstaller session commit.
 *
 * STATUS_SUCCESS         → install completed silently (Device Owner path)
 * STATUS_PENDING_USER_ACTION → not Device Owner; fall back to system installer UI
 * anything else          → log the failure
 */
public class InstallResultReceiver extends BroadcastReceiver {

    private static final String TAG = "InstallResultReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                // Relaunch is handled by PackageReplacedReceiver via ACTION_MY_PACKAGE_REPLACED
                Log.i(TAG, "Silent install succeeded: " + packageName);
                break;

            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // App is not Device Owner — Android still requires user confirmation.
                // Launch the system installer so at least the update can proceed.
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            default:
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.e(TAG, "Silent install failed — status=" + status + ", msg=" + message);
                break;
        }
    }
}
