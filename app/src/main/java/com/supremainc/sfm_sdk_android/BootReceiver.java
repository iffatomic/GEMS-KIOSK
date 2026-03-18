/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver - Auto-starts the app when device boots
 * Listens for BOOT_COMPLETED broadcast and launches SplashActivity
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG, "Boot broadcast received: " + action);

        // Check if this is a boot completed event
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.i(TAG, "Device boot detected - Starting GEMS Kiosk app...");

            // Create intent to launch SplashActivity
            Intent startIntent = new Intent(context, SplashActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // Start the app
            context.startActivity(startIntent);

            Log.i(TAG, "GEMS Kiosk app launched successfully");
        }
    }
}
