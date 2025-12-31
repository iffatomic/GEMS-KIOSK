/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.PacApiClient;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.VaultIncidentLog;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API client for vault incident log operations
 * Handles communication with /api/monitoring/VaultIncidentLog endpoints
 */
public class VaultIncidentLogApiClient {

    private static final String TAG = "VaultIncidentLogApi";

    private final PacApiClient baseClient;

    public VaultIncidentLogApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Get all vault incident logs with optional filtering
     * GET /api/monitoring/VaultIncidentLog
     * @param startDate Filter by start date (ISO-8601 format, e.g., "2024-12-01T00:00:00")
     * @param endDate Filter by end date
     * @param vaultName Filter by vault name
     * @param severity Filter by severity (Informational, Low, Medium, High, Critical)
     * @param limit Limit number of results
     * @param callback Response callback
     */
    public void getLogs(String startDate, String endDate, String vaultName, String severity,
                       Integer limit, ApiCallback<ApiResponse<List<VaultIncidentLog>>> callback) {

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ GET VAULT INCIDENT LOGS");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Start Date: " + (startDate != null ? startDate : "null"));
        Log.d(TAG, "║ End Date: " + (endDate != null ? endDate : "null"));
        Log.d(TAG, "║ Vault Name: " + (vaultName != null ? vaultName : "null"));
        Log.d(TAG, "║ Severity: " + (severity != null ? severity : "null"));
        Log.d(TAG, "║ Limit: " + (limit != null ? limit : "null"));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Build query parameters
        Map<String, String> queryParams = new HashMap<>();
        if (startDate != null && !startDate.isEmpty()) {
            queryParams.put("startDate", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryParams.put("endDate", endDate);
        }
        if (vaultName != null && !vaultName.isEmpty()) {
            queryParams.put("vaultName", vaultName);
        }
        if (severity != null && !severity.isEmpty()) {
            queryParams.put("severity", severity);
        }
        if (limit != null) {
            queryParams.put("limit", String.valueOf(limit));
        }

        Type responseType = new TypeToken<ApiResponse<List<VaultIncidentLog>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_VAULT_INCIDENT_LOGS, queryParams, responseType, callback);
    }

    /**
     * Get vault incident log by ID
     * GET /api/monitoring/VaultIncidentLog/{id}
     * @param id Log ID
     * @param callback Response callback
     */
    public void getLogById(String id, ApiCallback<ApiResponse<VaultIncidentLog>> callback) {
        Log.d(TAG, "Getting vault incident log by ID: " + id);

        String endpoint = ApiConstants.ENDPOINT_VAULT_INCIDENT_LOG_BY_ID + id;

        Type responseType = new TypeToken<ApiResponse<VaultIncidentLog>>(){}.getType();
        baseClient.get(endpoint, null, responseType, callback);
    }

    /**
     * Get recent vault incident logs
     * GET /api/monitoring/VaultIncidentLog/recent
     * @param count Number of recent logs to retrieve (default: 50, max: 500)
     * @param callback Response callback
     */
    public void getRecentLogs(Integer count, ApiCallback<ApiResponse<List<VaultIncidentLog>>> callback) {
        Log.d(TAG, "Getting recent vault incident logs (count: " + (count != null ? count : "default") + ")");

        Map<String, String> queryParams = new HashMap<>();
        if (count != null) {
            queryParams.put("count", String.valueOf(count));
        }

        Type responseType = new TypeToken<ApiResponse<List<VaultIncidentLog>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_VAULT_INCIDENT_RECENT, queryParams, responseType, callback);
    }

    /**
     * Get vault incident logs by severity
     * GET /api/monitoring/VaultIncidentLog/severity/{severity}
     * @param severity Severity level (Informational, Low, Medium, High, Critical)
     * @param limit Maximum number of logs to retrieve
     * @param callback Response callback
     */
    public void getLogsBySeverity(String severity, Integer limit,
                                 ApiCallback<ApiResponse<List<VaultIncidentLog>>> callback) {
        Log.d(TAG, "Getting vault incident logs by severity: " + severity);

        String endpoint = ApiConstants.ENDPOINT_VAULT_INCIDENT_BY_SEVERITY + severity;

        Map<String, String> queryParams = new HashMap<>();
        if (limit != null) {
            queryParams.put("limit", String.valueOf(limit));
        }

        Type responseType = new TypeToken<ApiResponse<List<VaultIncidentLog>>>(){}.getType();
        baseClient.get(endpoint, queryParams, responseType, callback);
    }

    /**
     * Get vault incident logs by vault name
     * GET /api/monitoring/VaultIncidentLog/vault/{vaultName}
     * @param vaultName Vault name
     * @param limit Maximum number of logs to retrieve
     * @param callback Response callback
     */
    public void getLogsByVaultName(String vaultName, Integer limit,
                                   ApiCallback<ApiResponse<List<VaultIncidentLog>>> callback) {
        Log.d(TAG, "Getting vault incident logs for vault: " + vaultName);

        // URL encode vault name to handle spaces
        String encodedVaultName = vaultName;
        try {
            encodedVaultName = URLEncoder.encode(vaultName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding vault name", e);
        }

        String endpoint = ApiConstants.ENDPOINT_VAULT_INCIDENT_BY_VAULT + encodedVaultName;

        Map<String, String> queryParams = new HashMap<>();
        if (limit != null) {
            queryParams.put("limit", String.valueOf(limit));
        }

        Type responseType = new TypeToken<ApiResponse<List<VaultIncidentLog>>>(){}.getType();
        baseClient.get(endpoint, queryParams, responseType, callback);
    }
}
