/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.VaultIncidentLog;
import com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto;
import com.supremainc.sfm_sdk_android.network.api.VaultIncidentLogApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Service layer for vault incident log operations
 * Handles business logic for querying and receiving vault security incidents
 */
public class VaultIncidentLogService {

    private static final String TAG = "VaultIncidentLogSvc";

    private final VaultIncidentLogApiClient apiClient;

    public VaultIncidentLogService(Context context) {
        this.apiClient = new VaultIncidentLogApiClient(context);
    }

    /**
     * Get all vault incident logs with filters
     * @param startDate Start date in ISO-8601 format (e.g., "2024-12-01T00:00:00")
     * @param endDate End date in ISO-8601 format
     * @param vaultName Filter by vault name
     * @param severity Filter by severity (Informational, Low, Medium, High, Critical)
     * @param limit Limit number of results
     * @param callback Response callback
     */
    public void getLogs(String startDate, String endDate, String vaultName, String severity,
                       Integer limit, VaultIncidentLogCallback callback) {

        Log.d(TAG, "Fetching vault incident logs");

        apiClient.getLogs(startDate, endDate, vaultName, severity, limit,
                new ApiCallback<ApiResponse<List<VaultIncidentLog>>>() {
                    @Override
                    public void onSuccess(ApiResponse<List<VaultIncidentLog>> response) {
                        if (response.isFlag() && response.hasData()) {
                            List<VaultIncidentLog> logs = response.getData();
                            Log.i(TAG, "Retrieved " + logs.size() + " vault incident log(s)");
                            callback.onLogsRetrieved(logs);
                        } else {
                            Log.e(TAG, "Failed to retrieve logs: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error fetching logs: " + error);
                        callback.onError(error);
                    }
                });
    }

    /**
     * Get vault incident log by ID
     * @param logId Log ID
     * @param callback Response callback
     */
    public void getLogById(String logId, VaultIncidentLogCallback callback) {
        Log.d(TAG, "Fetching vault incident log: " + logId);

        apiClient.getLogById(logId, new ApiCallback<ApiResponse<VaultIncidentLog>>() {
            @Override
            public void onSuccess(ApiResponse<VaultIncidentLog> response) {
                if (response.isFlag() && response.hasData()) {
                    Log.i(TAG, "Retrieved vault incident log: " + logId);
                    callback.onLogRetrieved(response.getData());
                } else {
                    Log.e(TAG, "Failed to retrieve log: " + response.getMessage());
                    callback.onError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                if (error.contains("404")) {
                    callback.onLogNotFound();
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    /**
     * Get recent vault incident logs
     * @param count Number of recent logs (default: 50, max: 500)
     * @param callback Response callback
     */
    public void getRecentLogs(Integer count, VaultIncidentLogCallback callback) {
        Log.d(TAG, "Fetching recent vault incident logs");

        apiClient.getRecentLogs(count, new ApiCallback<ApiResponse<List<VaultIncidentLog>>>() {
            @Override
            public void onSuccess(ApiResponse<List<VaultIncidentLog>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<VaultIncidentLog> logs = response.getData();
                    Log.i(TAG, "Retrieved " + logs.size() + " recent log(s)");
                    callback.onLogsRetrieved(logs);
                } else {
                    callback.onError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get vault incident logs by severity
     * @param severity Severity level (Informational, Low, Medium, High, Critical)
     * @param limit Maximum number of logs
     * @param callback Response callback
     */
    public void getLogsBySeverity(String severity, Integer limit, VaultIncidentLogCallback callback) {
        Log.d(TAG, "Fetching logs by severity: " + severity);

        apiClient.getLogsBySeverity(severity, limit, new ApiCallback<ApiResponse<List<VaultIncidentLog>>>() {
            @Override
            public void onSuccess(ApiResponse<List<VaultIncidentLog>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<VaultIncidentLog> logs = response.getData();
                    Log.i(TAG, "Retrieved " + logs.size() + " log(s) with severity: " + severity);
                    callback.onLogsRetrieved(logs);
                } else {
                    callback.onError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get vault incident logs by vault name
     * @param vaultName Vault name
     * @param limit Maximum number of logs
     * @param callback Response callback
     */
    public void getLogsByVaultName(String vaultName, Integer limit, VaultIncidentLogCallback callback) {
        Log.d(TAG, "Fetching logs for vault: " + vaultName);

        apiClient.getLogsByVaultName(vaultName, limit, new ApiCallback<ApiResponse<List<VaultIncidentLog>>>() {
            @Override
            public void onSuccess(ApiResponse<List<VaultIncidentLog>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<VaultIncidentLog> logs = response.getData();
                    Log.i(TAG, "Retrieved " + logs.size() + " log(s) for vault: " + vaultName);
                    callback.onLogsRetrieved(logs);
                } else {
                    callback.onError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get logs for today
     * Convenience method to get all logs from midnight today
     * @param callback Response callback
     */
    public void getTodaysLogs(VaultIncidentLogCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00", Locale.US);
        String startDate = sdf.format(new Date());

        Log.d(TAG, "Fetching today's logs (from: " + startDate + ")");

        getLogs(startDate, null, null, null, null, callback);
    }

    /**
     * Get critical incidents only
     * Convenience method to get High and Critical severity incidents
     * @param limit Maximum number of logs
     * @param callback Response callback
     */
    public void getCriticalIncidents(Integer limit, VaultIncidentLogCallback callback) {
        Log.d(TAG, "Fetching critical incidents");

        getLogsBySeverity("Critical", limit, callback);
    }

    /**
     * Callback interface for vault incident log operations
     */
    public interface VaultIncidentLogCallback {
        /**
         * Called when multiple logs are retrieved
         * @param logs List of vault incident logs
         */
        void onLogsRetrieved(List<VaultIncidentLog> logs);

        /**
         * Called when a single log is retrieved
         * @param log Vault incident log
         */
        void onLogRetrieved(VaultIncidentLog log);

        /**
         * Called when log is not found (404)
         */
        void onLogNotFound();

        /**
         * Called when an error occurs
         * @param error Error message
         */
        void onError(String error);
    }

    /**
     * Callback interface for real-time SignalR incident broadcasts
     */
    public interface VaultIncidentBroadcastListener {
        /**
         * Called when a vault incident is broadcast via SignalR
         * @param incident Vault incident broadcast DTO
         */
        void onVaultIncidentReceived(VaultIncidentBroadcastDto incident);

        /**
         * Called when connection is closed
         */
        void onConnectionClosed();
    }
}
