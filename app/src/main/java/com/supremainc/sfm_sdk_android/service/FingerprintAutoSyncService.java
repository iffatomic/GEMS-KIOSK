/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.supremainc.sfm_sdk_android.SignalRService;
import com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto;
import com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;

/**
 * Background service that monitors SignalR fingerprint enrollment events
 * and automatically triggers incremental fingerprint sync when new enrollments occur.
 *
 * USAGE:
 * 1. Start this service from MainActivity or Application.onCreate():
 *    startService(new Intent(this, FingerprintAutoSyncService.class));
 *
 * 2. Stop when app is closing:
 *    stopService(new Intent(this, FingerprintAutoSyncService.class));
 *
 * FLOW:
 * - Connects to SignalR EventHub on service start
 * - Listens for "FingerprintEnrollmentCompleted" events
 * - When event received, triggers FingerprintSyncService.syncAllFingerprintsIncremental()
 * - Only enrolls NEW fingerprints (skips already enrolled ones)
 */
public class FingerprintAutoSyncService extends Service {

    private static final String TAG = "FingerprintAutoSync";

    private SignalRService signalRService;
    private FingerprintSyncService fingerprintSyncService;
    private FingerprintDownloadApiClient fingerprintDownloadApiClient;
    private SFM_SDK_ANDROID sdk;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
        Log.i(TAG, "║ Fingerprint Auto-Sync Service STARTING");
        Log.i(TAG, "╚════════════════════════════════════════════════════════════");

        // Initialize SDK (required for FingerprintSyncService)
        sdk = new SFM_SDK_ANDROID();
        sdk.UF_InitSysParameter();

        // Initialize fingerprint sync service
        fingerprintSyncService = new FingerprintSyncService(this, sdk);

        // Initialize fingerprint download API client
        fingerprintDownloadApiClient = new FingerprintDownloadApiClient(this);

        // Initialize SignalR service
        signalRService = new SignalRService(this);
        signalRService.initialize(new SignalREventListener());

        // Connect to SignalR hub
        connectToSignalR();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        return START_STICKY; // Restart service if killed by system
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
        Log.i(TAG, "║ Fingerprint Auto-Sync Service STOPPING");
        Log.i(TAG, "╚════════════════════════════════════════════════════════════");

        // Disconnect SignalR
        if (signalRService != null) {
            signalRService.stop();
        }
    }

    /**
     * Connect to SignalR EventHub
     */
    private void connectToSignalR() {
        Log.d(TAG, "Connecting to SignalR...");

        signalRService.start(new SignalRService.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "✓ SignalR connected - Auto-sync service active");
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "✗ SignalR connection failed: " + errorMessage);
                // Retry after 5 seconds
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Retrying SignalR connection...");
                    connectToSignalR();
                }, 5000);
            }
        });
    }

    /**
     * SignalR Event Listener
     * Handles fingerprint enrollment completion events
     */
    private class SignalREventListener implements SignalRService.SignalREventListener {

        @Override
        public void onFingerprintEnrollmentCompleted(FingerprintEnrollmentEventDto event) {
            Log.i(TAG, "╔════════════════════════════════════════════════════════════");
            Log.i(TAG, "║ FINGERPRINT ENROLLMENT EVENT RECEIVED");
            Log.i(TAG, "╠════════════════════════════════════════════════════════════");
            Log.i(TAG, "║ Employee: " + event.getFullName());
            Log.i(TAG, "║ Staff ID: " + event.getStaffID());
            Log.i(TAG, "║ Fingerprints: " + event.getFingerprintCount());
            Log.i(TAG, "║ Message: " + event.getMessage());
            Log.i(TAG, "╚════════════════════════════════════════════════════════════");

            // OPTION 1: Call new optimized endpoint to get all fingerprints
            // This is the recommended approach for better performance
            Log.d(TAG, "Calling FingerprintDownload endpoint to get all employee fingerprints...");

            fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
                    new ApiCallback<AllEmployeesFingerprintsResponse>() {
                @Override
                public void onSuccess(AllEmployeesFingerprintsResponse response) {
                    Log.i(TAG, "✓ Successfully fetched fingerprint data from optimized endpoint");
                    Log.d(TAG, "  → Total Employees: " + response.getTotalEmployees());
                    Log.d(TAG, "  → Total Fingerprints: " + response.getTotalFingerprints());

                    // Now trigger incremental sync using the fetched data
                    Log.d(TAG, "Triggering incremental fingerprint sync...");

                    fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                        @Override
                        public void onSyncStarted(int totalFingerprints) {
                            Log.d(TAG, "Auto-sync started: " + totalFingerprints + " fingerprints to check");
                        }

                        @Override
                        public void onFingerprintEnrolled(int current, int total, String employeeName) {
                            Log.d(TAG, "Auto-enrolled: " + employeeName + " (" + current + "/" + total + ")");
                        }

                        @Override
                        public void onSyncCompleted(int successCount, int failCount) {
                            Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                            Log.i(TAG, "║ AUTO-SYNC COMPLETED");
                            Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                            Log.i(TAG, "║ New enrollments: " + successCount);
                            Log.i(TAG, "║ Failed: " + failCount);
                            Log.i(TAG, "╚════════════════════════════════════════════════════════════");
                        }

                        @Override
                        public void onSyncError(String error) {
                            Log.e(TAG, "✗ Auto-sync error: " + error);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "✗ Failed to fetch fingerprints from optimized endpoint: " + error);
                    Log.w(TAG, "Falling back to old sync method...");

                    // OPTION 2: Fallback to old method if new endpoint fails
                    fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                        @Override
                        public void onSyncStarted(int totalFingerprints) {
                            Log.d(TAG, "Fallback sync started: " + totalFingerprints + " fingerprints to check");
                        }

                        @Override
                        public void onFingerprintEnrolled(int current, int total, String employeeName) {
                            Log.d(TAG, "Auto-enrolled: " + employeeName + " (" + current + "/" + total + ")");
                        }

                        @Override
                        public void onSyncCompleted(int successCount, int failCount) {
                            Log.i(TAG, "Fallback sync completed: " + successCount + " new, " + failCount + " failed");
                        }

                        @Override
                        public void onSyncError(String error) {
                            Log.e(TAG, "✗ Fallback sync error: " + error);
                        }
                    });
                }
            });
        }

        // ========== Unused callbacks (no-op) ==========
        @Override
        public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {}

        @Override
        public void onDoorStateChanged(SignalRService.DoorStateData event) {}

        @Override
        public void onVaultIncident(SignalRService.VaultIncidentData incident) {}

        @Override
        public void onVaultIncidentBroadcast(VaultIncidentBroadcastDto incident) {}

        @Override
        public void onKeySwitchChanged(KeySwitchEventDto event) {}

        @Override
        public void onKeySwitchAggregate(KeySwitchAggregateEventDto event) {}

        @Override
        public void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {}

        @Override
        public void onConnectionClosed() {
            Log.w(TAG, "SignalR connection closed - Auto-sync disabled until reconnection");

            // Retry connection after 5 seconds
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (signalRService != null && !signalRService.isConnected()) {
                    Log.d(TAG, "Attempting to reconnect SignalR...");
                    connectToSignalR();
                }
            }, 5000);
        }
    }
}
