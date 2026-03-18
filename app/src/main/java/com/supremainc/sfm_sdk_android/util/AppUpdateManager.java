/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.supremainc.sfm_sdk_android.util.ConfigManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Checks for APK updates from the device's FTP upload directory.
 *
 * HOW IT WORKS:
 * 1. Upload two files into Internal Memory/Ftp/ on the device via FTP (Material Files):
 *      - version.json        describes the new version
 *      - gems-kiosk.apk      the new APK
 * 2. On every app startup, this class reads version.json and compares versionCode.
 * 3. If the file has a higher versionCode than the installed app, a dialog appears.
 * 4. User taps "Update Now" → Android installer launches (APK is already on device).
 *
 * PERMISSION NOTE:
 * On Android 11+, MANAGE_EXTERNAL_STORAGE must be granted once via:
 *   Settings → Apps → GEMS Kiosk → Permissions → Files and media → Allow management
 * The app will prompt for this automatically on first run if not already granted.
 *
 * version.json format:
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "apkFileName": "gems-kiosk.apk",
 *   "releaseNotes": "Bug fixes",
 *   "forceUpdate": false
 * }
 */
public class AppUpdateManager {

    private static final String TAG = "AppUpdateManager";
    private static final String VERSION_FILE = "version.json";
    private static final String FTP_FOLDER = "Ftp";

    /**
     * Returns the update check interval in milliseconds, read from AppConfig.
     * Default: 10 minutes. Minimum: 1 minute.
     */
    public static long getCheckIntervalMs(android.content.Context context) {
        int minutes = ConfigManager.getConfig().getUpdateCheckIntervalMinutes();
        return (long) minutes * 60 * 1000L;
    }

    public interface UpdateCheckCallback {
        void onUpdateAvailable(VersionInfo versionInfo, File apkFile);
        void onNoUpdateNeeded();
        void onCheckFailed(String error);
    }

    public static class VersionInfo {
        @SerializedName("versionCode")
        public int versionCode;

        @SerializedName("versionName")
        public String versionName;

        @SerializedName("apkFileName")
        public String apkFileName;

        @SerializedName("releaseNotes")
        public String releaseNotes;

        @SerializedName("forceUpdate")
        public boolean forceUpdate;
    }

    /**
     * Returns the FTP folder: /storage/emulated/0/Ftp/
     */
    public static File getFtpFolder() {
        return new File(Environment.getExternalStorageDirectory(), FTP_FOLDER);
    }

    /**
     * Returns true if the app can read from external storage.
     * On Android 11+, MANAGE_EXTERNAL_STORAGE must be granted.
     * On Android 10 and below, READ_EXTERNAL_STORAGE is sufficient.
     */
    public static boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true; // READ_EXTERNAL_STORAGE declared in manifest is enough on < Android 11
    }

    /**
     * Opens the system Settings screen where the user can grant MANAGE_EXTERNAL_STORAGE.
     * Only needed on Android 11+.
     */
    public static void requestStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                // Fallback if the specific intent isn't supported
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    /**
     * Check /storage/emulated/0/Ftp/ for a version.json and compare against installed version.
     * If permission is missing on Android 11+, silently skips the check.
     */
    public static void checkForUpdate(Context context, UpdateCheckCallback callback) {
        // On Android 11+, we need MANAGE_EXTERNAL_STORAGE to read the FTP folder
        if (!hasStoragePermission()) {
            Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted — skipping update check");
            callback.onCheckFailed("storage_permission_missing");
            return;
        }

        File ftpFolder = getFtpFolder();
        File versionFile = new File(ftpFolder, VERSION_FILE);

        Log.d(TAG, "Checking FTP folder: " + ftpFolder.getAbsolutePath());

        if (!versionFile.exists()) {
            Log.d(TAG, "No version.json in FTP folder — no update");
            callback.onNoUpdateNeeded();
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            VersionInfo versionInfo = new Gson().fromJson(sb.toString(), VersionInfo.class);

            if (versionInfo == null || versionInfo.apkFileName == null) {
                Log.w(TAG, "version.json is empty or missing apkFileName");
                callback.onNoUpdateNeeded();
                return;
            }

            int currentVersionCode = getCurrentVersionCode(context);
            Log.d(TAG, "Installed versionCode=" + currentVersionCode
                    + ", FTP versionCode=" + versionInfo.versionCode
                    + " (" + versionInfo.versionName + ")");

            if (versionInfo.versionCode <= currentVersionCode) {
                Log.d(TAG, "Already up to date");
                callback.onNoUpdateNeeded();
                return;
            }

            File apkFile = new File(ftpFolder, versionInfo.apkFileName);
            if (!apkFile.exists()) {
                Log.w(TAG, "APK not found: " + apkFile.getAbsolutePath());
                callback.onCheckFailed("APK file not found: " + versionInfo.apkFileName);
                return;
            }

            callback.onUpdateAvailable(versionInfo, apkFile);

        } catch (Exception e) {
            Log.e(TAG, "Error reading version.json", e);
            callback.onCheckFailed(e.getMessage());
        }
    }

    /**
     * Show update dialog. Tapping "Update Now" launches the Android installer immediately —
     * no download needed since the APK is already in the FTP folder.
     */
    public static void showUpdateDialog(Context context, VersionInfo versionInfo, File apkFile, Runnable onSkip) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Update Available — v" + versionInfo.versionName);

        StringBuilder message = new StringBuilder();
        message.append("A new version of GEMS Kiosk is ready to install.");
        if (versionInfo.releaseNotes != null && !versionInfo.releaseNotes.isEmpty()) {
            message.append("\n\nWhat's new:\n").append(versionInfo.releaseNotes);
        }
        builder.setMessage(message.toString());
        builder.setCancelable(false);

        builder.setPositiveButton("Update Now", (dialog, which) -> installApk(context, apkFile));

        if (!versionInfo.forceUpdate && onSkip != null) {
            builder.setNegativeButton("Skip", (dialog, which) -> onSkip.run());
        }

        builder.show();
    }

    private static void installApk(Context context, File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error launching installer", e);
            Toast.makeText(context, "Installation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static int getCurrentVersionCode(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
