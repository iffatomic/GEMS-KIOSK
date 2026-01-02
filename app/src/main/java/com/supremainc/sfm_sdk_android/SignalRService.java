/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Context;
import android.util.Log;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;
import com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

/**
 * Enhanced SignalR Client for connecting to PAC API EventHub
 * Receives real-time events about door states, manual override key switches, and aggregates
 */
public class SignalRService {

    private static final String TAG = "SignalRService";

    private final Context context;
    private HubConnection hubConnection;
    private SignalREventListener eventListener;

    // Constructor with context for configuration
    public SignalRService(Context context) {
        this.context = context.getApplicationContext();
    }

    // Legacy constructor for backward compatibility
    public SignalRService() {
        this.context = null;
    }

    /**
     * Get SignalR hub URL from context or use default
     */
    private String getHubUrl() {
        if (context != null) {
            return ApiConstants.getSignalRHubUrl(context);
        }
        // Fallback URL (should not be used if context is provided)
        return "http://192.2.2.253:7000/eventHub";
    }

    /**
     * Initialize SignalR connection
     */
    public void initialize(SignalREventListener listener) {
        this.eventListener = listener;

        String hubUrl = getHubUrl();
        Log.i(TAG, "Initializing SignalR connection to: " + hubUrl);

        // Simple connection without auto-reconnect
        hubConnection = HubConnectionBuilder.create(hubUrl)
                .build();

        setupEventHandlers();
    }

    /**
     * Setup event handlers for SignalR messages
     */
    private void setupEventHandlers() {

        // Listen for Manual Override Events (key switches changing)
        hubConnection.on("ManualOverrideEvent", (event) -> {
            Log.d(TAG, "Received ManualOverrideEvent: " + event.toString());

            if (eventListener != null) {
                eventListener.onManualOverrideEvent(event);
            }
        }, ManualOverrideEventData.class);

        // Listen for Door State Changes (door lock/unlock)
        hubConnection.on("DoorStateChanged", (event) -> {
            Log.d(TAG, "Received DoorStateChanged: Door " + event.doorId + " - " +
                    (event.isOpen ? "OPEN" : "CLOSED"));

            if (eventListener != null) {
                eventListener.onDoorStateChanged(event);
            }
        }, DoorStateData.class);

        // Listen for Vault Incidents (Legacy - keeping for backward compatibility)
        hubConnection.on("VaultIncidentOccurred", (incident) -> {
            Log.d(TAG, "Received VaultIncident (Legacy): Door " + incident.doorId);

            if (eventListener != null) {
                eventListener.onVaultIncident(incident);
            }
        }, VaultIncidentData.class);

        // ========== NEW: Listen for Vault Incident Broadcasts (Enhanced) ==========
        hubConnection.on(ApiConstants.SIGNALR_VAULT_INCIDENT, (incident) -> {
            Log.d(TAG, "╔════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ VAULT INCIDENT BROADCAST");
            Log.d(TAG, "╠════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ Vault: " + incident.getVaultName());
            Log.d(TAG, "║ Event: " + incident.getEventType());
            Log.d(TAG, "║ State: " + incident.getDoorState());
            Log.d(TAG, "║ Severity: " + incident.getSeverity());
            Log.d(TAG, "║ Time: " + incident.getOccurredAt());
            Log.d(TAG, "╚════════════════════════════════════════════════════════════");

            if (eventListener != null) {
                eventListener.onVaultIncidentBroadcast(incident);
            }
        }, VaultIncidentBroadcastDto.class);

        // ========== NEW: Listen for Individual KeySwitch Changes ==========
        hubConnection.on(ApiConstants.SIGNALR_KEYSWITCH_CHANGED, (event) -> {
            Log.d(TAG, "KeySwitch: Door " + event.getDoorName() +
                    " - Switch " + event.getKeySwitchNumber() +
                    " - " + (event.isOn() ? "ON" : "OFF"));

            if (eventListener != null) {
                eventListener.onKeySwitchChanged(event);
            }
        }, KeySwitchEventDto.class);

        // ========== NEW: Listen for Aggregate KeySwitch Events (All On/Off) ==========
        hubConnection.on(ApiConstants.SIGNALR_KEYSWITCH_AGGREGATE, (event) -> {
            Log.d(TAG, "KeySwitch Aggregate: Door " + event.getDoorName() +
                    " - " + event.getEventType());

            if (eventListener != null) {
                eventListener.onKeySwitchAggregate(event);
            }
        }, KeySwitchAggregateEventDto.class);

        // ========== Listen for Interim Cutoff Events ==========
        hubConnection.on("InterimCutoffReached", (event) -> {
            Log.d(TAG, "Interim Cutoff Reached: " + event.getCutoffHour() +
                    ":00 | Profiles deactivated: " + event.getProfilesDeactivated() +
                    " | Vaults: " + event.getVaultName());

            // Handle cutoff time deactivation
            handleCutoffTimeDeactivation(event);

            if (eventListener != null) {
                eventListener.onInterimCutoffReached(event);
            }
        }, com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto.class);

        // ========== Listen for Fingerprint Enrollment Completion ==========
        hubConnection.on(ApiConstants.SIGNALR_FINGERPRINT_ENROLLMENT, (event) -> {
            Log.d(TAG, "╔════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ FINGERPRINT ENROLLMENT COMPLETED");
            Log.d(TAG, "╠════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ Employee: " + event.getFullName() + " (" + event.getStaffID() + ")");
            Log.d(TAG, "║ Fingerprints: " + event.getFingerprintCount());
            Log.d(TAG, "║ Message: " + event.getMessage());
            Log.d(TAG, "╚════════════════════════════════════════════════════════════");

            if (eventListener != null) {
                eventListener.onFingerprintEnrollmentCompleted(event);
            }
        }, FingerprintEnrollmentEventDto.class);

        // Connection lifecycle callbacks
        hubConnection.onClosed(error -> {
            if (error != null) {
                Log.e(TAG, "Connection closed with error: " + error.getMessage());
            } else {
                Log.i(TAG, "Connection closed");
            }

            if (eventListener != null) {
                eventListener.onConnectionClosed();
            }
        });
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Map door name to vault name
     * Since each vault has 3 key switches, track which vault each switch belongs to
     */
    public static String mapDoorNameToVaultName(String doorName) {
        if (doorName == null) return "Unknown Vault";

        // Example mapping - adjust based on your vault configuration
        if (doorName.contains("Door A") || doorName.contains("Door B") || doorName.contains("Door C")) {
            return "Main Vault";
        } else if (doorName.contains("Door D") || doorName.contains("Door E")) {
            return "Day Vault";
        } else {
            return "Unknown Vault";
        }
    }

    /**
     * Get key switch number for a specific vault based on door name
     */
    public static int getKeySwitchNumberForVault(String doorName) {
        if (doorName == null) return 0;

        if (doorName.contains("Door A") || doorName.contains("Door D")) return 1;
        if (doorName.contains("Door B") || doorName.contains("Door E")) return 2;
        if (doorName.contains("Door C")) return 3;

        return 0;
    }

    /**
     * Handle interim cutoff time deactivation
     * Deactivates all active manual overrides when 8PM cutoff is reached
     */
    private void handleCutoffTimeDeactivation(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot handle cutoff deactivation");
            return;
        }

        try {
            Log.i(TAG, "Processing cutoff time deactivation at " + event.getCutoffHour() + ":00");

            // 1. Deactivate in-memory overrides (SharedPreferences)
            VaultOverrideManager vaultManager = VaultOverrideManager.getInstance(context);
            if (event.getVaultName() != null && !event.getVaultName().isEmpty()) {
                vaultManager.removeOverridesByVaultNames(event.getVaultName());
                Log.d(TAG, "Cleared " + event.getVaultName().size() + " vault overrides from memory");
            }

            // 2. Deactivate database overrides (SQLite)
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            int deactivatedCount = dbHelper.deactivateAllActiveOverridesAtCutoff();
            Log.i(TAG, "Cutoff deactivation complete: " + deactivatedCount + " database records updated");

        } catch (Exception e) {
            Log.e(TAG, "Error handling cutoff deactivation: " + e.getMessage(), e);
        }
    }

    /**
     * Start the SignalR connection
     */
    public void start(ConnectionCallback callback) {
        if (hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected");
            callback.onConnected();
            return;
        }

        Log.i(TAG, "Starting SignalR connection...");

        hubConnection.start()
                .doOnComplete(() -> {
                    Log.i(TAG, "✓ SignalR connected successfully");
                    callback.onConnected();
                })
                .doOnError(error -> {
                    Log.e(TAG, "✗ SignalR connection failed: " + error.getMessage(), error);
                    callback.onError(error.getMessage());
                })
                .subscribe();
    }

    /**
     * Stop the SignalR connection
     */
    public void stop() {
        if (hubConnection != null &&
                hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {

            Log.i(TAG, "Stopping SignalR connection...");
            hubConnection.stop();
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return hubConnection != null &&
                hubConnection.getConnectionState() == HubConnectionState.CONNECTED;
    }

    /**
     * Get connection state
     */
    public String getConnectionState() {
        if (hubConnection == null) {
            return "NOT_INITIALIZED";
        }
        return hubConnection.getConnectionState().name();
    }

    // ==================== EVENT DATA CLASSES ====================

    /**
     * Manual Override Event Data
     * Received when key switches change state
     */
    public static class ManualOverrideEventData {
        public int doorId;
        public String doorName;
        public String location;
        public String eventType;
        public String timestamp;
        public String message;
        public PhysicalInputState physicalInputState;
        public String timeFrameStart;
        public String timeFrameEnd;
        public boolean wasWithinTimeFrame;

        @Override
        public String toString() {
            return String.format("ManualOverrideEvent{door=%d, type=%s, keySwitchAll=%s}",
                    doorId, eventType,
                    physicalInputState != null ? physicalInputState.keySwitchAll : "null");
        }
    }

    /**
     * Physical Input State (key switches, door contact)
     */
    public static class PhysicalInputState {
        public boolean keySwitchAll;
        public boolean doorContact;
        public boolean allConditionsMet;
        public boolean lockState;
    }

    /**
     * Door State Change Data
     * Received when door locks/unlocks
     */
    public static class DoorStateData {
        public int doorId;
        public String doorName;
        public boolean isOpen;
        public String timestamp;
        public String location;
        public String eventSource;

        @Override
        public String toString() {
            return String.format("DoorState{door=%d, state=%s}",
                    doorId, isOpen ? "OPEN" : "CLOSED");
        }
    }

    /**
     * Vault Incident Data
     */
    public static class VaultIncidentData {
        public int doorId;
        public String doorName;
        public String eventType;
        public String timestamp;
        public boolean isOverride;
    }

    // ==================== CALLBACK INTERFACES ====================

    /**
     * Listener for SignalR events
     */
    public interface SignalREventListener {
        void onManualOverrideEvent(ManualOverrideEventData event);
        void onDoorStateChanged(DoorStateData event);
        void onVaultIncident(VaultIncidentData incident);  // Legacy
        void onVaultIncidentBroadcast(VaultIncidentBroadcastDto incident);  // NEW: Enhanced vault incident
        void onKeySwitchChanged(KeySwitchEventDto event);
        void onKeySwitchAggregate(KeySwitchAggregateEventDto event);
        void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event);
        void onFingerprintEnrollmentCompleted(FingerprintEnrollmentEventDto event);  // NEW: Fingerprint enrollment trigger
        void onConnectionClosed();
    }

    /**
     * Callback for connection start
     */
    public interface ConnectionCallback {
        void onConnected();
        void onError(String errorMessage);
    }
}