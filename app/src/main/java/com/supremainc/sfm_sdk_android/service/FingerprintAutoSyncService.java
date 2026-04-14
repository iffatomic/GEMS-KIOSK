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

import com.supremainc.sfm_sdk_android.DatabaseHelper;
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

                // CATCH-UP MECHANISM: Perform incremental sync on connection/reconnection
                // This catches any fingerprints enrolled while we were disconnected
                Log.i(TAG, "Performing catch-up sync to detect missed enrollments...");

                fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                    @Override
                    public void onSyncStarted(int totalFingerprints) {
                        Log.d(TAG, "Catch-up sync started: checking " + totalFingerprints + " fingerprint(s)");
                    }

                    @Override
                    public void onFingerprintEnrolled(int current, int total, String employeeName) {
                        Log.i(TAG, "Caught-up: " + employeeName + " (" + current + "/" + total + ")");
                    }

                    @Override
                    public void onSyncCompleted(int successCount, int failCount) {
                        if (successCount > 0) {
                            Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                            Log.i(TAG, "║ CATCH-UP SYNC COMPLETED");
                            Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                            Log.i(TAG, "║ Found " + successCount + " new fingerprint(s) while offline");
                            Log.i(TAG, "║ Failed: " + failCount);
                            Log.i(TAG, "╚════════════════════════════════════════════════════════════");
                        } else {
                            Log.d(TAG, "✓ Catch-up complete - no missed enrollments detected");
                        }
                    }

                    @Override
                    public void onSyncError(String error) {
                        Log.e(TAG, "✗ Catch-up sync error: " + error);
                    }
                });
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

            // HYBRID APPROACH: Auto-detect database state
            // - If database is empty → Full sync (catches historical data)
            // - If database has data → Targeted sync (efficient)
            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            int totalSyncedFingerprints = dbHelper.getSyncedFingerprintCount();
            dbHelper.close();

            String employeeNumber = event.getStaffID();

            if (totalSyncedFingerprints == 0) {
                // Database is empty - likely first deployment or after reset
                Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ DATABASE EMPTY - TRIGGERING FULL SYNC");
                Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ This will download ALL historical fingerprints");
                Log.i(TAG, "║ Synced fingerprints in DB: 0");
                Log.i(TAG, "╚════════════════════════════════════════════════════════════");

                // Full incremental sync (downloads all, skips none since DB is empty)
                fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                    @Override
                    public void onSyncStarted(int totalFingerprints) {
                        Log.d(TAG, "Initial full sync started: " + totalFingerprints + " fingerprints to check");
                    }

                    @Override
                    public void onFingerprintEnrolled(int current, int total, String employeeName) {
                        Log.d(TAG, "Auto-enrolled: " + employeeName + " (" + current + "/" + total + ")");
                    }

                    @Override
                    public void onSyncCompleted(int successCount, int failCount) {
                        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ INITIAL FULL SYNC COMPLETED");
                        Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ Total enrolled: " + successCount);
                        Log.i(TAG, "║ Failed: " + failCount);
                        Log.i(TAG, "║ Future syncs will be targeted (single user)");
                        Log.i(TAG, "╚════════════════════════════════════════════════════════════");
                    }

                    @Override
                    public void onSyncError(String error) {
                        Log.e(TAG, "✗ Initial full sync error: " + error);
                    }
                });

            } else {
                // Database has data - use targeted sync for efficiency
                Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ DATABASE HAS DATA - USING TARGETED SYNC");
                Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ Synced fingerprints in DB: " + totalSyncedFingerprints);
                Log.i(TAG, "║ Target employee: " + event.getFullName() + " (" + employeeNumber + ")");
                Log.i(TAG, "╚════════════════════════════════════════════════════════════");

                if (employeeNumber == null || employeeNumber.isEmpty()) {
                    Log.e(TAG, "✗ No employee number in event - cannot perform targeted sync");
                    Log.w(TAG, "Falling back to full incremental sync");

                    // Fallback to full sync if employee number is missing
                    fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                        @Override
                        public void onSyncStarted(int totalFingerprints) {
                            Log.d(TAG, "Fallback full sync started: " + totalFingerprints + " fingerprints");
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
                    return;
                }

                // OPTIMIZED: Sync only the specific employee who just enrolled
                fingerprintSyncService.syncSingleUser(employeeNumber, new FingerprintSyncService.SyncCallback() {
                    @Override
                    public void onSyncStarted(int totalFingerprints) {
                        Log.d(TAG, "Targeted sync started for " + event.getFullName() +
                              ": " + totalFingerprints + " fingerprint(s)");
                    }

                    @Override
                    public void onFingerprintEnrolled(int current, int total, String employeeName) {
                        Log.d(TAG, "Auto-enrolled: " + employeeName + " - fingerprint " +
                              current + "/" + total);
                    }

                    @Override
                    public void onSyncCompleted(int successCount, int failCount) {
                        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ TARGETED SYNC COMPLETED");
                        Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ Employee: " + event.getFullName());
                        Log.i(TAG, "║ New enrollments: " + successCount);
                        Log.i(TAG, "║ Failed: " + failCount);

                        if (successCount > 0) {
                            Log.i(TAG, "║ ✓ Successfully enrolled " + successCount +
                                  " fingerprint(s) for " + event.getFullName());
                        } else if (failCount == 0) {
                            Log.i(TAG, "║ ⊙ No new fingerprints to enroll (already synced)");
                        }
                        Log.i(TAG, "╚════════════════════════════════════════════════════════════");
                    }

                    @Override
                    public void onSyncError(String error) {
                        Log.e(TAG, "╔════════════════════════════════════════════════════════════");
                        Log.e(TAG, "║ TARGETED SYNC ERROR");
                        Log.e(TAG, "╠════════════════════════════════════════════════════════════");
                        Log.e(TAG, "║ Employee: " + event.getFullName() + " (" + employeeNumber + ")");
                        Log.e(TAG, "║ Error: " + error);
                        Log.e(TAG, "╚════════════════════════════════════════════════════════════");
                    }
                });
            }
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
        public void onKioskPatchReady() {}

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

    /**
     * PUBLIC TEST METHOD - Manually trigger sync for testing
     * Call this from anywhere to test fingerprint download and enrollment
     */
    public void manualTriggerSync() {
        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
        Log.i(TAG, "║ MANUAL TEST SYNC TRIGGERED");
        Log.i(TAG, "╚════════════════════════════════════════════════════════════");

        // Just call the incremental sync directly
        fingerprintSyncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
            @Override
            public void onSyncStarted(int totalFingerprints) {
                Log.d(TAG, "Manual sync started: " + totalFingerprints + " fingerprints to check");
            }

            @Override
            public void onFingerprintEnrolled(int current, int total, String employeeName) {
                Log.d(TAG, "Enrolled: " + employeeName + " (" + current + "/" + total + ")");
            }

            @Override
            public void onSyncCompleted(int successCount, int failCount) {
                Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ MANUAL SYNC COMPLETED");
                Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ New enrollments: " + successCount);
                Log.i(TAG, "║ Failed: " + failCount);
                Log.i(TAG, "╚════════════════════════════════════════════════════════════");
            }

            @Override
            public void onSyncError(String error) {
                Log.e(TAG, "✗ Manual sync error: " + error);
            }
        });
    }
}
