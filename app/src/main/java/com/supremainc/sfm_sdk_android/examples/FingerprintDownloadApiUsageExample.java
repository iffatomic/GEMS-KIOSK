/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.examples;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;

/**
 * Usage example for FingerprintDownloadApiClient
 *
 * This demonstrates how to call the new optimized endpoint
 * /api/FingerprintDownload/employee-fingerprints
 *
 * WHEN TO USE:
 * - Kiosk startup/initialization - sync all fingerprints once
 * - SignalR event received - new employee enrolled, refresh data
 * - Manual refresh button - user triggers sync from System Settings
 * - Scheduled background sync - periodic updates
 *
 * ADVANTAGES OVER OLD ENDPOINT:
 * - Single API call instead of multiple calls per employee
 * - Optimized database query with eager loading (Include())
 * - Returns all data needed for scanner enrollment
 * - Less network overhead and faster sync times
 */
public class FingerprintDownloadApiUsageExample {

    private static final String TAG = "FingerprintDownloadExample";

    /**
     * Example: Fetch all employees with fingerprints
     */
    public static void fetchAllEmployeesWithFingerprints(Context context) {

        // Initialize API client
        FingerprintDownloadApiClient apiClient = new FingerprintDownloadApiClient(context);

        // Call the endpoint
        apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
            @Override
            public void onSuccess(AllEmployeesFingerprintsResponse response) {
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ SUCCESS - Received employee fingerprint data");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Total Employees: " + response.getTotalEmployees());
                Log.d(TAG, "║ Total Fingerprints: " + response.getTotalFingerprints());
                Log.d(TAG, "║ Retrieved At: " + response.getRetrievedAt());
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                // Process each employee
                if (response.getEmployees() != null) {
                    for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : response.getEmployees()) {
                        processEmployee(employee);
                    }
                }

                Log.i(TAG, "✓ Finished processing all employees");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "╔════════════════════════════════════════════════════════════");
                Log.e(TAG, "║ ERROR - Failed to fetch fingerprint data");
                Log.e(TAG, "╠════════════════════════════════════════════════════════════");
                Log.e(TAG, "║ Error: " + error);
                Log.e(TAG, "╚════════════════════════════════════════════════════════════");
            }
        });
    }

    /**
     * Process a single employee with their fingerprints
     */
    private static void processEmployee(AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee) {
        Log.d(TAG, "");
        Log.d(TAG, "Employee: " + employee.getFullName() + " (" + employee.getStaffID() + ")");
        Log.d(TAG, "  → IC Number: " + employee.getIcNumber());
        Log.d(TAG, "  → Department: " + employee.getDepartment());
        Log.d(TAG, "  → Card Number: " + employee.getCardNumber());
        Log.d(TAG, "  → Is Active: " + employee.isActive());
        Log.d(TAG, "  → Enrollment Date: " + employee.getEnrollmentDate());

        // Process fingerprints
        if (employee.getFingerprints() != null && !employee.getFingerprints().isEmpty()) {
            Log.d(TAG, "  → Fingerprints: " + employee.getFingerprints().size());

            for (AllEmployeesFingerprintsResponse.FingerprintData fingerprint : employee.getFingerprints()) {
                processFingerprint(employee, fingerprint);
            }
        } else {
            Log.d(TAG, "  → No fingerprints enrolled");
        }
    }

    /**
     * Process a single fingerprint
     */
    private static void processFingerprint(
            AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee,
            AllEmployeesFingerprintsResponse.FingerprintData fingerprint) {

        // Determine hand
        String hand = fingerprint.getLeftRight() == 0 ? "Left" : "Right";

        // Determine finger
        String[] fingers = {"Thumb", "Index", "Middle", "Ring", "Little"};
        String finger = fingerprint.getFingerIndex() >= 0 && fingerprint.getFingerIndex() < fingers.length
                ? fingers[fingerprint.getFingerIndex()]
                : "Unknown";

        Log.d(TAG, "    • Fingerprint ID: " + fingerprint.getId());
        Log.d(TAG, "      - Position: " + hand + " " + finger);
        Log.d(TAG, "      - Quality: " + fingerprint.getQuality() + "%");
        Log.d(TAG, "      - Template Size: " + (fingerprint.getTemplateData() != null
                ? fingerprint.getTemplateData().length + " bytes (Raw)"
                : "null"));
        Log.d(TAG, "      - Finger Type: " + fingerprint.getFingerType());
        Log.d(TAG, "      - Enrollment Date: " + fingerprint.getEnrollmentDate());

        // Here you would typically:
        // 1. Use raw byte[] template data directly (NO Base64 decode needed!)
        // 2. Enroll to local fingerprint scanner using SDK
        // 3. Store mapping in local database
        // Example:
        // byte[] templateBytes = Base64.decode(fingerprint.getTemplateData(), Base64.DEFAULT);
        // int scannerId = enrollToScanner(templateBytes);
        // saveToLocalDatabase(employee, fingerprint, scannerId);
    }

    /**
     * Example: Simple usage - just count statistics
     */
    public static void getStatistics(Context context) {
        FingerprintDownloadApiClient apiClient = new FingerprintDownloadApiClient(context);

        apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
            @Override
            public void onSuccess(AllEmployeesFingerprintsResponse response) {
                // Quick statistics
                int totalEmployees = response.getTotalEmployees();
                int totalFingerprints = response.getTotalFingerprints();
                double avgFingerprintsPerEmployee = totalEmployees > 0
                        ? (double) totalFingerprints / totalEmployees
                        : 0;

                Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ FINGERPRINT STATISTICS");
                Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ Total Employees: " + totalEmployees);
                Log.i(TAG, "║ Total Fingerprints: " + totalFingerprints);
                Log.i(TAG, "║ Average per Employee: " + String.format("%.2f", avgFingerprintsPerEmployee));
                Log.i(TAG, "╚════════════════════════════════════════════════════════════");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to get statistics: " + error);
            }
        });
    }

    /**
     * Example: Filter active employees only
     */
    public static void getActiveEmployeesOnly(Context context) {
        FingerprintDownloadApiClient apiClient = new FingerprintDownloadApiClient(context);

        apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
            @Override
            public void onSuccess(AllEmployeesFingerprintsResponse response) {
                int activeCount = 0;
                int inactiveCount = 0;

                if (response.getEmployees() != null) {
                    for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : response.getEmployees()) {
                        if (employee.isActive()) {
                            activeCount++;
                            Log.d(TAG, "Active: " + employee.getFullName() +
                                    " (" + employee.getFingerprints().size() + " fingerprints)");
                        } else {
                            inactiveCount++;
                        }
                    }
                }

                Log.i(TAG, "Active employees: " + activeCount + ", Inactive: " + inactiveCount);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }
}
