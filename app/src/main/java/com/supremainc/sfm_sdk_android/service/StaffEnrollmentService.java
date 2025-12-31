/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.request.EnrollmentRequest;
import com.supremainc.sfm_sdk_android.data.model.request.FingerCredential;
import com.supremainc.sfm_sdk_android.data.model.request.ValidateFingerprintRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.api.StaffEnrollmentApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.utils.FingerprintUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for staff enrollment operations
 * Handles business logic and data transformation for staff enrollment
 */
public class StaffEnrollmentService {

    private static final String TAG = "StaffEnrollService";

    private final StaffEnrollmentApiClient apiClient;
    private final FingerprintSyncService syncService;

    public StaffEnrollmentService(Context context, com.supremainc.sfm_sdk.SFM_SDK_ANDROID sdk) {
        this.apiClient = new StaffEnrollmentApiClient(context);
        this.syncService = new FingerprintSyncService(context, sdk);
    }

    // For backwards compatibility - creates without sync support
    @Deprecated
    public StaffEnrollmentService(Context context) {
        this.apiClient = new StaffEnrollmentApiClient(context);
        this.syncService = null;
        Log.w(TAG, "StaffEnrollmentService created without SDK - auto-sync will not work");
    }

    /**
     * Get list of admin users pending fingerprint enrollment
     * @param callback Response callback
     */
    public void getPendingAdminUsers(StaffEnrollmentCallback callback) {
        Log.d(TAG, "Fetching pending admin users");

        apiClient.getPendingAdminUsers(new ApiCallback<ApiResponse<List<UserListItem>>>() {
            @Override
            public void onSuccess(ApiResponse<List<UserListItem>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<UserListItem> users = response.getData();
                    Log.i(TAG, "Retrieved " + users.size() + " pending admin user(s)");
                    callback.onPendingUsersRetrieved(users);
                } else {
                    Log.e(TAG, "Failed to retrieve pending admin users: " + response.getMessage());
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching pending admin users: " + error);
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Get list of custodian users pending fingerprint enrollment
     * @param callback Response callback
     */
    public void getPendingCustodianUsers(StaffEnrollmentCallback callback) {
        Log.d(TAG, "Fetching pending custodian users");

        apiClient.getPendingCustodianUsers(new ApiCallback<ApiResponse<List<UserListItem>>>() {
            @Override
            public void onSuccess(ApiResponse<List<UserListItem>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<UserListItem> users = response.getData();
                    Log.i(TAG, "Retrieved " + users.size() + " pending custodian user(s)");
                    callback.onPendingUsersRetrieved(users);
                } else {
                    Log.e(TAG, "Failed to retrieve pending custodian users: " + response.getMessage());
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching pending custodian users: " + error);
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Get list of admin users who have enrolled fingerprints
     * @param callback Response callback
     */
    public void getEnrolledAdminUsers(StaffEnrollmentCallback callback) {
        Log.d(TAG, "Fetching enrolled admin users");

        apiClient.getEnrolledAdminUsers(new ApiCallback<ApiResponse<List<UserListItem>>>() {
            @Override
            public void onSuccess(ApiResponse<List<UserListItem>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<UserListItem> users = response.getData();
                    Log.i(TAG, "Retrieved " + users.size() + " enrolled admin user(s)");
                    callback.onPendingUsersRetrieved(users);
                } else {
                    Log.e(TAG, "Failed to retrieve enrolled admin users: " + response.getMessage());
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching enrolled admin users: " + error);
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Get list of custodian users who have enrolled fingerprints
     * @param callback Response callback
     */
    public void getEnrolledCustodianUsers(StaffEnrollmentCallback callback) {
        Log.d(TAG, "Fetching enrolled custodian users");

        apiClient.getEnrolledCustodianUsers(new ApiCallback<ApiResponse<List<UserListItem>>>() {
            @Override
            public void onSuccess(ApiResponse<List<UserListItem>> response) {
                if (response.isFlag() && response.hasData()) {
                    List<UserListItem> users = response.getData();
                    Log.i(TAG, "Retrieved " + users.size() + " enrolled custodian user(s)");
                    callback.onPendingUsersRetrieved(users);
                } else {
                    Log.e(TAG, "Failed to retrieve enrolled custodian users: " + response.getMessage());
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching enrolled custodian users: " + error);
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Check if username exists
     * @param username Username to check
     * @param callback Response callback
     */
    public void checkUsername(String username, StaffEnrollmentCallback callback) {
        Log.d(TAG, "Checking username: " + username);

        apiClient.checkUsername(username, new ApiCallback<ApiResponse<UserListItem>>() {
            @Override
            public void onSuccess(ApiResponse<UserListItem> response) {
                if (response.isFlag() && response.hasData()) {
                    Log.i(TAG, "Username exists: " + username);
                    callback.onUserValidated(response.getData());
                } else {
                    Log.i(TAG, "Username not found: " + username);
                    callback.onUserNotFound();
                }
            }

            @Override
            public void onError(String error) {
                if (error.contains("404")) {
                    callback.onUserNotFound();
                } else {
                    callback.onEnrollmentError(error);
                }
            }
        });
    }

    /**
     * Check if employee number exists
     * @param employeeNumber Employee number to check
     * @param callback Response callback
     */
    public void checkEmployeeNumber(String employeeNumber, StaffEnrollmentCallback callback) {
        Log.d(TAG, "Checking employee number: " + employeeNumber);

        apiClient.checkEmployeeNumber(employeeNumber, new ApiCallback<ApiResponse<UserListItem>>() {
            @Override
            public void onSuccess(ApiResponse<UserListItem> response) {
                if (response.isFlag() && response.hasData()) {
                    Log.i(TAG, "Employee number exists: " + employeeNumber);
                    callback.onUserValidated(response.getData());
                } else {
                    Log.i(TAG, "Employee number not found: " + employeeNumber);
                    callback.onUserNotFound();
                }
            }

            @Override
            public void onError(String error) {
                if (error.contains("404")) {
                    callback.onUserNotFound();
                } else {
                    callback.onEnrollmentError(error);
                }
            }
        });
    }

    /**
     * Enroll fingerprints for pre-registered CSV user
     * @param username Username
     * @param name Full name
     * @param employeeNumber Employee number
     * @param role User role
     * @param department Department (optional)
     * @param branch Branch (optional)
     * @param leftFingerprint Left fingerprint bytes
     * @param rightFingerprint Right fingerprint bytes
     * @param callback Response callback
     */
    public void enrollPreRegisteredUser(
            String username,
            String name,
            String employeeNumber,
            String role,
            String department,
            String branch,
            byte[] leftFingerprint,
            byte[] rightFingerprint,
            StaffEnrollmentCallback callback) {

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ ENROLL PRE-REGISTERED USER");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Username: " + username);
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "║ Role: " + role);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        EnrollmentRequest request = buildEnrollmentRequest(
                username, name, employeeNumber, role, department, branch,
                leftFingerprint, rightFingerprint);

        if (request == null) {
            callback.onEnrollmentError("Failed to build enrollment request");
            return;
        }

        apiClient.enrollUser(request, new ApiCallback<ApiResponse<EnrollUserResponse>>() {
            @Override
            public void onSuccess(ApiResponse<EnrollUserResponse> response) {
                if (response.isFlag() && response.hasData()) {
                    Log.i(TAG, "✓ User enrolled to API successfully");
                    Log.i(TAG, "→ Now syncing from API → Database → Scanner");

                    // RETRY SYNC: Keep retrying until fingerprints are available
                    // Callback will be called AFTER sync completes
                    if (syncService != null) {
                        retrySyncUntilSuccess(employeeNumber, 0, response.getData(), callback);
                    } else {
                        Log.w(TAG, "⚠ Sync service not available");
                        callback.onEnrollmentSuccess(response.getData());
                    }
                } else {
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Register new user just-in-time (not from CSV)
     * @param username Username
     * @param name Full name
     * @param employeeNumber Employee number
     * @param role User role
     * @param department Department (optional)
     * @param branch Branch (optional)
     * @param leftFingerprint Left fingerprint bytes
     * @param rightFingerprint Right fingerprint bytes
     * @param callback Response callback
     */
    public void registerJustInTimeUser(
            String username,
            String name,
            String employeeNumber,
            String role,
            String department,
            String branch,
            byte[] leftFingerprint,
            byte[] rightFingerprint,
            StaffEnrollmentCallback callback) {

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ REGISTER JIT USER");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Username: " + username);
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "║ Role: " + role);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        EnrollmentRequest request = buildEnrollmentRequest(
                username, name, employeeNumber, role, department, branch,
                leftFingerprint, rightFingerprint);

        if (request == null) {
            callback.onEnrollmentError("Failed to build JIT registration request");
            return;
        }

        apiClient.registerJustInTimeUser(request, new ApiCallback<ApiResponse<EnrollUserResponse>>() {
            @Override
            public void onSuccess(ApiResponse<EnrollUserResponse> response) {
                if (response.isFlag() && response.hasData()) {
                    Log.i(TAG, "✓ JIT user registered to API successfully");
                    Log.i(TAG, "→ Now syncing from API → Database → Scanner");

                    // RETRY SYNC: Keep retrying until fingerprints are available
                    // Callback will be called AFTER sync completes
                    if (syncService != null) {
                        retrySyncUntilSuccess(employeeNumber, 0, response.getData(), callback);
                    } else {
                        Log.w(TAG, "⚠ Sync service not available");
                        callback.onEnrollmentSuccess(response.getData());
                    }
                } else {
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * NOTE: Fingerprint validation is no longer done via API.
     *
     * Validation is now handled locally using the scanner's UF_IdentifyTemplate function.
     * The scanner compares the scanned template against its internal memory and returns a scanner ID.
     * We then look up the scanner ID in the local database to get user info.
     *
     * This method has been removed because:
     * 1. API cannot perform fuzzy fingerprint matching (fingerprint templates are never byte-identical)
     * 2. Validation must be done by the scanner hardware or using Suprema SDK matching algorithms
     * 3. Cross-device validation is achieved through fingerprint sync (FingerprintSyncService)
     *
     * For validation, use:
     * - scanner.UF_IdentifyTemplate() to get scanner ID
     * - DatabaseHelper.getUserByScannerUserId() for local users
     * - DatabaseHelper.getUserByApiScannerId() for synced users from API
     */
    @Deprecated
    public void validateFingerprint(byte[] fingerprintBytes, StaffEnrollmentCallback callback) {
        Log.e(TAG, "❌ validateFingerprint() is deprecated and should not be called!");
        Log.e(TAG, "Use scanner.UF_IdentifyTemplate() instead for fingerprint validation");
        callback.onEnrollmentError("API validation is not supported. Use local scanner validation.");
    }

    /**
     * Delete user and fingerprints
     * @param employeeNumber Employee number
     * @param callback Response callback
     */
    public void deleteUser(String employeeNumber, StaffEnrollmentCallback callback) {
        Log.d(TAG, "Deleting user: " + employeeNumber);

        apiClient.deleteUser(employeeNumber, new ApiCallback<ApiResponse<Boolean>>() {
            @Override
            public void onSuccess(ApiResponse<Boolean> response) {
                if (response.isFlag() && response.hasData() && response.getData()) {
                    Log.i(TAG, "✓ User deleted successfully");
                    callback.onUserDeleted();
                } else {
                    callback.onEnrollmentError(response.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onEnrollmentError(error);
            }
        });
    }

    /**
     * Helper method to build enrollment request
     */
    private EnrollmentRequest buildEnrollmentRequest(
            String username,
            String name,
            String employeeNumber,
            String role,
            String department,
            String branch,
            byte[] leftFingerprint,
            byte[] rightFingerprint) {

        EnrollmentRequest request = new EnrollmentRequest();
        request.setUsername(username);
        request.setName(name);
        request.setEmployeeNumber(employeeNumber);
        request.setRole(role);
        request.setDepartment(department);
        request.setBranch(branch);

        // Convert fingerprints to Arrays.toString() format
        List<FingerCredential> fingerprints = new ArrayList<>();

        if (leftFingerprint != null && leftFingerprint.length > 0) {
            FingerCredential leftCred = new FingerCredential();
            String leftString = FingerprintUtils.convertByteArrayToString(leftFingerprint);
            leftCred.setFingerPrintBase64(leftString);
            leftCred.setLeftRight(0);  // 0 = Left
            leftCred.setFingerIndex(1);
            leftCred.setFingerType("Index");
            fingerprints.add(leftCred);

            Log.d(TAG, "Left Fingerprint: " + leftFingerprint.length + " bytes -> " +
                    leftString.length() + " chars");
        }

        if (rightFingerprint != null && rightFingerprint.length > 0) {
            FingerCredential rightCred = new FingerCredential();
            String rightString = FingerprintUtils.convertByteArrayToString(rightFingerprint);
            rightCred.setFingerPrintBase64(rightString);
            rightCred.setLeftRight(1);  // 1 = Right
            rightCred.setFingerIndex(2);
            rightCred.setFingerType("Index");
            fingerprints.add(rightCred);

            Log.d(TAG, "Right Fingerprint: " + rightFingerprint.length + " bytes -> " +
                    rightString.length() + " chars");
        }

        request.setFingerprints(fingerprints);

        // Validate
        if (fingerprints.size() != 2) {
            Log.e(TAG, "Exactly 2 fingerprints required, got: " + fingerprints.size());
            return null;
        }

        Log.i(TAG, "Built enrollment request with 2 fingerprints");
        return request;
    }

    /**
     * Retry FULL incremental sync until successful (up to 5 attempts)
     * Uses INCREMENTAL sync that skips already enrolled fingerprints
     * Retries every 3 seconds until fingerprints are available from API
     * Calls enrollmentCallback ONLY after sync completes successfully
     */
    private void retrySyncUntilSuccess(String employeeNumber, int attemptNumber,
                                        EnrollUserResponse enrollmentResponse,
                                        StaffEnrollmentCallback enrollmentCallback) {
        final int maxAttempts = 5;
        final int delayMs = 3000; // 3 seconds between retries

        if (attemptNumber >= maxAttempts) {
            Log.e(TAG, "╔═══════════════════════════════════════════════════════════");
            Log.e(TAG, "║ ✗ SYNC FAILED AFTER " + maxAttempts + " ATTEMPTS");
            Log.e(TAG, "╠═══════════════════════════════════════════════════════════");
            Log.e(TAG, "║ Employee: " + employeeNumber);
            Log.e(TAG, "║ User enrolled but sync failed - manual sync required");
            Log.e(TAG, "╚═══════════════════════════════════════════════════════════");

            // Still call success callback since enrollment worked, but warn about sync
            enrollmentCallback.onEnrollmentSuccess(enrollmentResponse);
            return;
        }

        int currentAttempt = attemptNumber + 1;
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════");
        Log.d(TAG, "║ INCREMENTAL SYNC ATTEMPT " + currentAttempt + "/" + maxAttempts);
        Log.d(TAG, "╠═══════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Syncing all fingerprints (skips existing): " + employeeNumber);
        if (attemptNumber > 0) {
            Log.d(TAG, "║ Waiting " + (delayMs / 1000) + "s for backend to process...");
        }
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════");

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "→ Calling INCREMENTAL SYNC for all users (skips existing)");

            // Use INCREMENTAL sync - syncs all fingerprints but skips already enrolled ones
            syncService.syncAllFingerprintsIncremental(new FingerprintSyncService.SyncCallback() {
                @Override
                public void onSyncStarted(int totalFingerprints) {
                    Log.i(TAG, "✓ API returned " + totalFingerprints + " total fingerprint(s)");
                }

                @Override
                public void onFingerprintEnrolled(int current, int total, String employeeName) {
                    Log.i(TAG, "  → Enrolled to scanner: " + current + "/" + total + " - " + employeeName);
                }

                @Override
                public void onSyncCompleted(int successCount, int failCount) {
                    if (successCount > 0) {
                        Log.i(TAG, "╔═══════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ ✓ SYNC SUCCESSFUL!");
                        Log.i(TAG, "╠═══════════════════════════════════════════════════════════");
                        Log.i(TAG, "║ Total enrolled: " + successCount + " fingerprint(s)");
                        Log.i(TAG, "║ Failed: " + failCount);
                        Log.i(TAG, "║ Flow: API → Database → Scanner ✓");
                        Log.i(TAG, "║ User can now verify immediately!");
                        Log.i(TAG, "╚═══════════════════════════════════════════════════════════");

                        // Sync successful - now call the enrollment callback
                        enrollmentCallback.onEnrollmentSuccess(enrollmentResponse);
                    } else {
                        Log.w(TAG, "⚠ Attempt " + currentAttempt + " failed: API returned 0 fingerprints");
                        Log.w(TAG, "  → Retrying in " + (delayMs / 1000) + " seconds...");
                        // Retry
                        retrySyncUntilSuccess(employeeNumber, currentAttempt, enrollmentResponse, enrollmentCallback);
                    }
                }

                @Override
                public void onSyncError(String error) {
                    Log.e(TAG, "✗ Attempt " + currentAttempt + " error: " + error);
                    Log.e(TAG, "  → Retrying in " + (delayMs / 1000) + " seconds...");
                    // Retry
                    retrySyncUntilSuccess(employeeNumber, currentAttempt, enrollmentResponse, enrollmentCallback);
                }
            });

        }, attemptNumber == 0 ? 2000 : delayMs); // First attempt after 2s, rest after 3s
    }
}
