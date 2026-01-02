/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.examples;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.SignalRService;
import com.supremainc.sfm_sdk_android.data.model.response.VaultIncidentLog;
import com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto;
import com.supremainc.sfm_sdk_android.service.VaultIncidentLogService;

import java.util.List;

/**
 * Comprehensive usage examples for VaultIncidentLogService
 * Demonstrates all query operations and real-time SignalR integration
 */
public class VaultIncidentLogServiceUsageExample {

    private static final String TAG = "VaultIncidentExample";

    // ==================== EXAMPLE 1: Query Logs with Filters ====================

    /**
     * Example 1: Get vault incident logs with date range and filters
     * Use this to query historical incidents with specific criteria
     */
    public static void example1_QueryLogsWithFilters(Context context) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        // Query logs for specific vault, date range, and severity
        String startDate = "2024-12-01T00:00:00";
        String endDate = "2024-12-31T23:59:59";
        String vaultName = "Main Vault";
        String severity = "High";
        Integer limit = 100;

        service.getLogs(startDate, endDate, vaultName, severity, limit,
                new VaultIncidentLogService.VaultIncidentLogCallback() {
                    @Override
                    public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                        Log.i(TAG, "Retrieved " + logs.size() + " incident logs");

                        for (VaultIncidentLog log : logs) {
                            Log.d(TAG, String.format("[%s] %s - %s | %s | Severity: %s",
                                    log.getOccurredAt(),
                                    log.getVaultName(),
                                    log.getEventType(),
                                    log.getDoorState(),
                                    log.getSeverity()));
                        }

                        // Process logs for display in UI
                        displayLogsInRecyclerView(logs);
                    }

                    @Override
                    public void onLogRetrieved(VaultIncidentLog log) {
                        // Not used for this query
                    }

                    @Override
                    public void onLogNotFound() {
                        Log.w(TAG, "No logs found matching criteria");
                        showEmptyState();
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error fetching logs: " + error);
                        showErrorDialog("Failed to fetch logs", error);
                    }
                });
    }

    // ==================== EXAMPLE 2: Get Log by ID ====================

    /**
     * Example 2: Retrieve a specific incident log by ID
     * Useful for displaying detailed incident information
     */
    public static void example2_GetLogById(Context context, String logId) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        service.getLogById(logId, new VaultIncidentLogService.VaultIncidentLogCallback() {
            @Override
            public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                // Not used for this query
            }

            @Override
            public void onLogRetrieved(VaultIncidentLog log) {
                Log.i(TAG, "Retrieved incident log: " + log.getId());

                // Display detailed log information
                displayIncidentDetails(log);

                // Log full details
                Log.d(TAG, "=== Vault Incident Details ===");
                Log.d(TAG, "ID: " + log.getId());
                Log.d(TAG, "Vault: " + log.getVaultName());
                Log.d(TAG, "Location: " + log.getLocation());
                Log.d(TAG, "Event: " + log.getEventType());
                Log.d(TAG, "Door State: " + log.getDoorState());
                Log.d(TAG, "Severity: " + log.getSeverity());
                Log.d(TAG, "Occurred At: " + log.getOccurredAt());
                Log.d(TAG, "Sent to AWS: " + log.getIsSentToAws());
            }

            @Override
            public void onLogNotFound() {
                Log.w(TAG, "Log not found with ID: " + logId);
                showToast("Incident log not found");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching log: " + error);
                showErrorDialog("Failed to fetch log", error);
            }
        });
    }

    // ==================== EXAMPLE 3: Get Recent Logs ====================

    /**
     * Example 3: Get the most recent vault incidents
     * Ideal for dashboard display or quick overview
     */
    public static void example3_GetRecentLogs(Context context) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        // Get last 50 incidents (default)
        service.getRecentLogs(50, new VaultIncidentLogService.VaultIncidentLogCallback() {
            @Override
            public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                Log.i(TAG, "Retrieved " + logs.size() + " recent logs");

                // Display in dashboard
                updateDashboardWithRecentIncidents(logs);

                // Count by severity
                int critical = 0, high = 0, medium = 0, low = 0, info = 0;
                for (VaultIncidentLog log : logs) {
                    switch (log.getSeverity()) {
                        case "Critical":
                            critical++;
                            break;
                        case "High":
                            high++;
                            break;
                        case "Medium":
                            medium++;
                            break;
                        case "Low":
                            low++;
                            break;
                        case "Informational":
                            info++;
                            break;
                    }
                }

                Log.d(TAG, String.format("Severity breakdown - Critical: %d, High: %d, " +
                        "Medium: %d, Low: %d, Info: %d", critical, high, medium, low, info));
            }

            @Override
            public void onLogRetrieved(VaultIncidentLog log) {
                // Not used
            }

            @Override
            public void onLogNotFound() {
                Log.w(TAG, "No recent logs found");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching recent logs: " + error);
            }
        });
    }

    // ==================== EXAMPLE 4: Get Logs by Severity ====================

    /**
     * Example 4: Filter logs by severity level
     * Useful for security monitoring and alert systems
     */
    public static void example4_GetLogsBySeverity(Context context, String severity) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        service.getLogsBySeverity(severity, 100,
                new VaultIncidentLogService.VaultIncidentLogCallback() {
                    @Override
                    public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                        Log.i(TAG, "Found " + logs.size() + " incidents with severity: " + severity);

                        // Highlight critical/high severity incidents
                        if (severity.equals("Critical") || severity.equals("High")) {
                            showAlertDialog("Security Alert",
                                    logs.size() + " " + severity + " severity incidents found");
                        }

                        // Display in filtered view
                        displayFilteredLogs(logs, severity);
                    }

                    @Override
                    public void onLogRetrieved(VaultIncidentLog log) {
                        // Not used
                    }

                    @Override
                    public void onLogNotFound() {
                        Log.i(TAG, "No incidents found with severity: " + severity);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error fetching logs by severity: " + error);
                    }
                });
    }

    // ==================== EXAMPLE 5: Get Logs by Vault Name ====================

    /**
     * Example 5: Query incidents for a specific vault
     * Use this for vault-specific monitoring and reporting
     */
    public static void example5_GetLogsByVaultName(Context context, String vaultName) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        service.getLogsByVaultName(vaultName, null,
                new VaultIncidentLogService.VaultIncidentLogCallback() {
                    @Override
                    public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                        Log.i(TAG, vaultName + " has " + logs.size() + " incidents");

                        // Calculate incident rate for this vault
                        displayVaultIncidentReport(vaultName, logs);

                        // Show timeline
                        if (!logs.isEmpty()) {
                            Log.d(TAG, "First incident: " + logs.get(logs.size() - 1).getOccurredAt());
                            Log.d(TAG, "Latest incident: " + logs.get(0).getOccurredAt());
                        }
                    }

                    @Override
                    public void onLogRetrieved(VaultIncidentLog log) {
                        // Not used
                    }

                    @Override
                    public void onLogNotFound() {
                        Log.i(TAG, "No incidents found for vault: " + vaultName);
                        showToast("No incidents for " + vaultName);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error fetching logs for vault: " + error);
                    }
                });
    }

    // ==================== EXAMPLE 6: Get Today's Logs ====================

    /**
     * Example 6: Convenience method to get all incidents from today
     * Perfect for daily monitoring dashboards
     */
    public static void example6_GetTodaysLogs(Context context) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        service.getTodaysLogs(new VaultIncidentLogService.VaultIncidentLogCallback() {
            @Override
            public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                Log.i(TAG, "Today's incidents: " + logs.size());

                // Display in daily summary
                displayDailySummary(logs);

                // Count by vault
                Log.d(TAG, "=== Today's Incident Summary ===");
                for (VaultIncidentLog log : logs) {
                    Log.d(TAG, String.format("[%s] %s - %s (%s)",
                            log.getOccurredAt().substring(11, 19),  // Time only
                            log.getVaultName(),
                            log.getEventType(),
                            log.getSeverity()));
                }
            }

            @Override
            public void onLogRetrieved(VaultIncidentLog log) {
                // Not used
            }

            @Override
            public void onLogNotFound() {
                Log.i(TAG, "No incidents recorded today");
                showToast("No incidents today");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching today's logs: " + error);
            }
        });
    }

    // ==================== EXAMPLE 7: Get Critical Incidents ====================

    /**
     * Example 7: Get all critical severity incidents
     * Use for security alerts and urgent monitoring
     */
    public static void example7_GetCriticalIncidents(Context context) {
        VaultIncidentLogService service = new VaultIncidentLogService(context);

        service.getCriticalIncidents(50, new VaultIncidentLogService.VaultIncidentLogCallback() {
            @Override
            public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                if (!logs.isEmpty()) {
                    Log.w(TAG, "ALERT: " + logs.size() + " critical incidents found!");

                    // Show urgent notification
                    showUrgentNotification("Critical Security Incidents",
                            logs.size() + " critical incidents require attention");

                    // Display in alert panel
                    displayCriticalIncidents(logs);

                    // Log details
                    for (VaultIncidentLog log : logs) {
                        Log.w(TAG, String.format("CRITICAL: [%s] %s - %s",
                                log.getOccurredAt(),
                                log.getVaultName(),
                                log.getEventType()));
                    }
                } else {
                    Log.i(TAG, "No critical incidents");
                }
            }

            @Override
            public void onLogRetrieved(VaultIncidentLog log) {
                // Not used
            }

            @Override
            public void onLogNotFound() {
                Log.i(TAG, "No critical incidents found");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching critical incidents: " + error);
            }
        });
    }

    // ==================== EXAMPLE 8: Real-time SignalR Integration ====================

    /**
     * Example 8: Listen to real-time vault incident broadcasts via SignalR
     * Ideal for live monitoring and instant security alerts
     */
    public static void example8_RealTimeSignalRIntegration(Context context) {
        // Initialize SignalR service
        SignalRService signalRService = new SignalRService(context);

        // Create event listener
        SignalRService.SignalREventListener eventListener = new SignalRService.SignalREventListener() {
            @Override
            public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {
                // Handle manual override events
            }

            @Override
            public void onDoorStateChanged(SignalRService.DoorStateData event) {
                // Handle door state changes
            }

            @Override
            public void onVaultIncident(SignalRService.VaultIncidentData incident) {
                // Legacy handler - kept for backward compatibility
            }

            @Override
            public void onVaultIncidentBroadcast(VaultIncidentBroadcastDto incident) {
                // NEW: Enhanced vault incident broadcast handler
                Log.w(TAG, "╔════════════════════════════════════════════════════════════");
                Log.w(TAG, "║ REAL-TIME VAULT INCIDENT RECEIVED");
                Log.w(TAG, "╠════════════════════════════════════════════════════════════");
                Log.w(TAG, "║ Vault: " + incident.getVaultName());
                Log.w(TAG, "║ Location: " + incident.getLocation());
                Log.w(TAG, "║ Event: " + incident.getEventType());
                Log.w(TAG, "║ Door State: " + incident.getDoorState());
                Log.w(TAG, "║ Severity: " + incident.getSeverity());
                Log.w(TAG, "║ Time: " + incident.getOccurredAt());
                Log.w(TAG, "╚════════════════════════════════════════════════════════════");

                // Show real-time notification
                if (incident.getSeverity().equals("Critical") ||
                    incident.getSeverity().equals("High")) {
                    showUrgentNotification("Security Alert",
                            incident.getVaultName() + " - " + incident.getEventType());
                }

                // Update UI in real-time
                updateIncidentListUI(incident);

                // Play alert sound for critical incidents
                if (incident.getSeverity().equals("Critical")) {
                    playAlertSound();
                }

                // Store in local database for offline access
                storeIncidentLocally(incident);
            }

            @Override
            public void onKeySwitchChanged(KeySwitchEventDto event) {
                // Handle key switch events
            }

            @Override
            public void onKeySwitchAggregate(KeySwitchAggregateEventDto event) {
                // Handle aggregate key switch events
            }

            @Override
            public void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {
                // Handle cutoff events
            }

            @Override
            public void onFingerprintEnrollmentCompleted(FingerprintEnrollmentEventDto event) {
                // Handle fingerprint enrollment events
                Log.i(TAG, "Fingerprint enrollment completed for: " + event.getFullName());
            }

            @Override
            public void onConnectionClosed() {
                Log.w(TAG, "SignalR connection closed");
                showToast("Real-time monitoring disconnected");

                // Attempt reconnection
                attemptReconnection();
            }
        };

        // Initialize SignalR with listener
        signalRService.initialize(eventListener);

        // Start connection
        signalRService.start(new SignalRService.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "Real-time incident monitoring started");
                showToast("Real-time monitoring active");
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "SignalR connection failed: " + errorMessage);
                showErrorDialog("Connection Failed", errorMessage);
            }
        });
    }

    // ==================== EXAMPLE 9: Complete Activity Integration ====================

    /**
     * Example 9: Complete implementation in an Activity
     * Shows how to integrate all features in a real app
     */
    public static class VaultIncidentMonitorActivity {

        private Context context;
        private VaultIncidentLogService vaultLogService;
        private SignalRService signalRService;

        public void onCreate(Context context) {
            this.context = context;

            // Initialize services
            vaultLogService = new VaultIncidentLogService(context);
            signalRService = new SignalRService(context);

            // Load initial data
            loadRecentIncidents();

            // Start real-time monitoring
            startRealTimeMonitoring();
        }

        private void loadRecentIncidents() {
            // Load recent incidents for initial display
            vaultLogService.getRecentLogs(50, new VaultIncidentLogService.VaultIncidentLogCallback() {
                @Override
                public void onLogsRetrieved(List<VaultIncidentLog> logs) {
                    // Update RecyclerView with logs
                    displayIncidentsInList(logs);
                }

                @Override
                public void onLogRetrieved(VaultIncidentLog log) {
                }

                @Override
                public void onLogNotFound() {
                    showEmptyState();
                }

                @Override
                public void onError(String error) {
                    showErrorState(error);
                }
            });
        }

        private void startRealTimeMonitoring() {
            // Start SignalR for real-time updates
            signalRService.initialize(createEventListener());
            signalRService.start(new SignalRService.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.i(TAG, "Real-time monitoring connected");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Connection error: " + errorMessage);
                }
            });
        }

        private SignalRService.SignalREventListener createEventListener() {
            return new SignalRService.SignalREventListener() {
                // Implement all required methods
                @Override
                public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {
                }

                @Override
                public void onDoorStateChanged(SignalRService.DoorStateData event) {
                }

                @Override
                public void onVaultIncident(SignalRService.VaultIncidentData incident) {
                }

                @Override
                public void onVaultIncidentBroadcast(VaultIncidentBroadcastDto incident) {
                    // Add new incident to top of list
                    addIncidentToList(incident);

                    // Show notification if severe
                    if (incident.getSeverity().equals("Critical") ||
                        incident.getSeverity().equals("High")) {
                        showIncidentNotification(incident);
                    }
                }

                @Override
                public void onKeySwitchChanged(KeySwitchEventDto event) {
                }

                @Override
                public void onKeySwitchAggregate(KeySwitchAggregateEventDto event) {
                }

                @Override
                public void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {
                }

                @Override
                public void onFingerprintEnrollmentCompleted(FingerprintEnrollmentEventDto event) {
                    // Handle fingerprint enrollment events
                }

                @Override
                public void onConnectionClosed() {
                    showConnectionLostIndicator();
                }
            };
        }

        public void onDestroy() {
            // Clean up SignalR connection
            if (signalRService != null) {
                signalRService.stop();
            }
        }

        // UI update methods (implement based on your UI framework)
        private void displayIncidentsInList(List<VaultIncidentLog> logs) {
            // Update RecyclerView adapter
        }

        private void addIncidentToList(VaultIncidentBroadcastDto incident) {
            // Add to top of RecyclerView
        }

        private void showIncidentNotification(VaultIncidentBroadcastDto incident) {
            // Show Android notification
        }

        private void showConnectionLostIndicator() {
            // Show connection status indicator
        }

        private void showEmptyState() {
            // Show empty state UI
        }

        private void showErrorState(String error) {
            // Show error UI
        }
    }

    // ==================== PLACEHOLDER UI METHODS ====================
    // These are placeholder methods - implement based on your UI framework

    private static void displayLogsInRecyclerView(List<VaultIncidentLog> logs) {
        // Implement RecyclerView update
    }

    private static void displayIncidentDetails(VaultIncidentLog log) {
        // Implement detail view
    }

    private static void updateDashboardWithRecentIncidents(List<VaultIncidentLog> logs) {
        // Implement dashboard update
    }

    private static void displayFilteredLogs(List<VaultIncidentLog> logs, String severity) {
        // Implement filtered view
    }

    private static void displayVaultIncidentReport(String vaultName, List<VaultIncidentLog> logs) {
        // Implement report view
    }

    private static void displayDailySummary(List<VaultIncidentLog> logs) {
        // Implement daily summary
    }

    private static void displayCriticalIncidents(List<VaultIncidentLog> logs) {
        // Implement critical incident alert panel
    }

    private static void updateIncidentListUI(VaultIncidentBroadcastDto incident) {
        // Implement real-time list update
    }

    private static void storeIncidentLocally(VaultIncidentBroadcastDto incident) {
        // Implement local database storage
    }

    private static void playAlertSound() {
        // Implement alert sound
    }

    private static void attemptReconnection() {
        // Implement reconnection logic
    }

    private static void showToast(String message) {
        // Implement Toast message
    }

    private static void showAlertDialog(String title, String message) {
        // Implement AlertDialog
    }

    private static void showErrorDialog(String title, String error) {
        // Implement error dialog
    }

    private static void showUrgentNotification(String title, String message) {
        // Implement urgent notification
    }

    private static void showEmptyState() {
        // Implement empty state UI
    }
}
