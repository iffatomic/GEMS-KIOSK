package com.supremainc.sfm_sdk_android.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class GEMSDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "GEMSDeviceAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin disabled");
    }
}
