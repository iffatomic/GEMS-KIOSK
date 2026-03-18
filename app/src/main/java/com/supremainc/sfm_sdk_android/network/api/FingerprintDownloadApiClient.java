/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.PacApiClient;
import com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.lang.reflect.Type;

/**
 * API client for fingerprint download operations
 * Handles communication with /api/FingerprintDownload endpoints
 *
 * PURPOSE:
 * This endpoint is optimized for kiosk fingerprint synchronization.
 * It returns ALL employees with their fingerprints in a single call,
 * which is more efficient than fetching fingerprints one employee at a time.
 *
 * USAGE:
 * Used by FingerprintAutoSyncService when SignalR event is received,
 * or manually triggered from System Settings screen.
 */
public class FingerprintDownloadApiClient {

    private static final String TAG = "FingerprintDownloadApi";

    private final PacApiClient baseClient;

    public FingerprintDownloadApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Get all employees with their fingerprints
     * GET /api/FingerprintDownload/employee-fingerprints
     *
     * Returns:
     * - All employees from Employee table
     * - All fingerprints for each employee (up to 4 per employee)
     * - Fingerprint template data as Base64 string
     * - Employee metadata (StaffID, FullName, CardNumber, etc.)
     *
     * This is the RECOMMENDED method for kiosk synchronization because:
     * 1. Single API call instead of multiple calls
     * 2. Optimized database query with eager loading
     * 3. Returns everything needed for scanner enrollment
     *
     * @param callback Response callback with all employees and fingerprints
     */
    public void getAllEmployeesWithFingerprints(
            ApiCallback<AllEmployeesFingerprintsResponse> callback) {

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ GET ALL EMPLOYEES WITH FINGERPRINTS");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Endpoint: " + ApiConstants.ENDPOINT_FINGERPRINT_DOWNLOAD);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        Type responseType = new TypeToken<AllEmployeesFingerprintsResponse>(){}.getType();

        baseClient.get(
                ApiConstants.ENDPOINT_FINGERPRINT_DOWNLOAD,
                null,
                responseType,
                new ApiCallback<AllEmployeesFingerprintsResponse>() {
                    @Override
                    public void onSuccess(AllEmployeesFingerprintsResponse response) {
                        if (response != null) {
                            Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                            Log.d(TAG, "║ API RESPONSE RECEIVED");
                            Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                            Log.d(TAG, "║ Total Employees: " + response.getTotalEmployees());
                            Log.d(TAG, "║ Total Fingerprints: " + response.getTotalFingerprints());
                            Log.d(TAG, "║ Retrieved At: " + response.getRetrievedAt());

                            // Debug: Check if fingerprints have template data
                            if (response.getEmployees() != null && !response.getEmployees().isEmpty()) {
                                AllEmployeesFingerprintsResponse.EmployeeFingerprintData firstEmployee = response.getEmployees().get(0);
                                Log.d(TAG, "║ First Employee: " + firstEmployee.getFullName());
                                if (firstEmployee.getFingerprints() != null && !firstEmployee.getFingerprints().isEmpty()) {
                                    AllEmployeesFingerprintsResponse.FingerprintData firstFp = firstEmployee.getFingerprints().get(0);
                                    String templateString = firstFp.getTemplateDataString();
                                    Log.d(TAG, "║ First Fingerprint Template String: " +
                                        (templateString != null ? templateString.length() + " chars" : "NULL!"));
                                    if (templateString != null && !templateString.isEmpty()) {
                                        // Show first 50 chars
                                        Log.d(TAG, "║ First 50 chars: " +
                                            templateString.substring(0, Math.min(50, templateString.length())));
                                    }
                                } else {
                                    Log.e(TAG, "║ First employee has NO fingerprints!");
                                }
                            }
                            Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                            callback.onSuccess(response);
                        } else {
                            String error = "Response is null";
                            Log.e(TAG, "✗ " + error);
                            callback.onError(error);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "╔════════════════════════════════════════════════════════════");
                        Log.e(TAG, "║ API ERROR");
                        Log.e(TAG, "╠════════════════════════════════════════════════════════════");
                        Log.e(TAG, "║ Error: " + error);
                        Log.e(TAG, "╚════════════════════════════════════════════════════════════");

                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Get a single employee's fingerprint data by employee number.
     * GET /api/FingerprintDownload/employee-fingerprints/{employeeNumber}
     *
     * Used for a live isAllowedOverride check just before vault selection,
     * so the app always uses the latest value from the server.
     *
     * @param employeeNumber Staff ID / employee number to look up
     * @param callback Response callback with the employee's data
     */
    public void getEmployeeByNumber(String employeeNumber,
            ApiCallback<AllEmployeesFingerprintsResponse.EmployeeFingerprintData> callback) {

        Log.d(TAG, "Live override-permission check for employee: " + employeeNumber);

        java.lang.reflect.Type responseType =
            new TypeToken<AllEmployeesFingerprintsResponse.EmployeeFingerprintData>(){}.getType();

        baseClient.get(
            ApiConstants.ENDPOINT_FINGERPRINT_EMPLOYEE + employeeNumber,
            null,
            responseType,
            new ApiCallback<AllEmployeesFingerprintsResponse.EmployeeFingerprintData>() {
                @Override
                public void onSuccess(AllEmployeesFingerprintsResponse.EmployeeFingerprintData response) {
                    Log.d(TAG, "Live check result for " + employeeNumber
                            + ": isAllowedOverride=" + response.isAllowedOverride());
                    callback.onSuccess(response);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Live override check failed for " + employeeNumber + ": " + error);
                    callback.onError(error);
                }
            }
        );
    }

    /**
     * Convenience method that returns the response wrapped in ApiResponse format
     * This matches the pattern used by other API clients in the app
     *
     * NOTE: The backend endpoint does NOT wrap the response in ApiResponse{flag, message, data}
     * It returns AllEmployeesFingerprintsResponseDto directly.
     * This method is provided for consistency with other API clients.
     *
     * @param callback Response callback
     */
    public void getAllEmployeesWithFingerprintsWrapped(
            ApiCallback<AllEmployeesFingerprintsResponse> callback) {

        getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
            @Override
            public void onSuccess(AllEmployeesFingerprintsResponse response) {
                // Response is already in the correct format
                callback.onSuccess(response);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
