/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.PacApiClient;
import com.supremainc.sfm_sdk_android.data.model.request.EnrollmentRequest;
import com.supremainc.sfm_sdk_android.data.model.request.ValidateFingerprintRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.lang.reflect.Type;
import java.util.List;

/**
 * API client for staff enrollment operations
 * Handles communication with /api/StaffEnrollment endpoints
 */
public class StaffEnrollmentApiClient {

    private static final String TAG = "StaffEnrollApiClient";

    private final PacApiClient baseClient;

    public StaffEnrollmentApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Check if username exists
     * GET /api/StaffEnrollment/check-username/{username}
     * @param username Username to check
     * @param callback Response callback (returns user info if exists, 404 if not)
     */
    public void checkUsername(String username, ApiCallback<ApiResponse<UserListItem>> callback) {
        Log.d(TAG, "Checking username: " + username);

        String endpoint = ApiConstants.ENDPOINT_STAFF_CHECK_USERNAME + username;

        Type responseType = new TypeToken<ApiResponse<UserListItem>>(){}.getType();
        baseClient.get(endpoint, null, responseType, callback);
    }

    /**
     * Check if employee number exists
     * GET /api/StaffEnrollment/check-employee-number/{employeeNumber}
     * @param employeeNumber Employee number to check
     * @param callback Response callback (returns user info if exists, 404 if not)
     */
    public void checkEmployeeNumber(String employeeNumber,
                                   ApiCallback<ApiResponse<UserListItem>> callback) {
        Log.d(TAG, "Checking employee number: " + employeeNumber);

        String endpoint = ApiConstants.ENDPOINT_STAFF_CHECK_EMPLOYEE + employeeNumber;

        Type responseType = new TypeToken<ApiResponse<UserListItem>>(){}.getType();
        baseClient.get(endpoint, null, responseType, callback);
    }

    /**
     * Get all admin users who have NOT enrolled fingerprints yet (pending enrollment list)
     * GET /api/StaffEnrollment/pending-admin
     * @param callback Response callback
     */
    public void getPendingAdminUsers(ApiCallback<ApiResponse<List<UserListItem>>> callback) {
        Log.d(TAG, "Getting pending admin users");

        Type responseType = new TypeToken<ApiResponse<List<UserListItem>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_STAFF_PENDING_ADMIN, null, responseType, callback);
    }

    /**
     * Get all custodian users who have NOT enrolled fingerprints yet (pending enrollment list)
     * GET /api/StaffEnrollment/pending-custodian
     * @param callback Response callback
     */
    public void getPendingCustodianUsers(ApiCallback<ApiResponse<List<UserListItem>>> callback) {
        Log.d(TAG, "Getting pending custodian users");

        Type responseType = new TypeToken<ApiResponse<List<UserListItem>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_STAFF_PENDING_CUSTODIAN, null, responseType, callback);
    }

    /**
     * Get all admin users who HAVE enrolled fingerprints (enrolled admins list)
     * GET /api/StaffEnrollment/enrolled-admin
     * @param callback Response callback
     */
    public void getEnrolledAdminUsers(ApiCallback<ApiResponse<List<UserListItem>>> callback) {
        Log.d(TAG, "Getting enrolled admin users");

        Type responseType = new TypeToken<ApiResponse<List<UserListItem>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_STAFF_ENROLLED_ADMIN, null, responseType, callback);
    }

    /**
     * Get all custodian users who HAVE enrolled fingerprints (enrolled custodians list)
     * GET /api/StaffEnrollment/enrolled-custodian
     * @param callback Response callback
     */
    public void getEnrolledCustodianUsers(ApiCallback<ApiResponse<List<UserListItem>>> callback) {
        Log.d(TAG, "Getting enrolled custodian users");

        Type responseType = new TypeToken<ApiResponse<List<UserListItem>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_STAFF_ENROLLED_CUSTODIAN, null, responseType, callback);
    }

    /**
     * Enroll fingerprints for pre-registered user
     * POST /api/StaffEnrollment/enroll
     * User must already exist (pre-registered via CSV). This adds fingerprints and can update role.
     * @param request Enrollment request with fingerprint data
     * @param callback Response callback
     */
    public void enrollUser(EnrollmentRequest request, ApiCallback<ApiResponse<EnrollUserResponse>> callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ ENROLL USER REQUEST");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + request.getEmployeeNumber());
        Log.d(TAG, "║ Username: " + request.getUsername());
        Log.d(TAG, "║ Name: " + request.getName());
        Log.d(TAG, "║ Role: " + request.getRole());
        Log.d(TAG, "║ Fingerprints: " + (request.getFingerprints() != null ?
                request.getFingerprints().size() : 0));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Validate request
        String validationError = request.validate();
        if (validationError != null) {
            callback.onError("Validation error: " + validationError);
            return;
        }

        baseClient.postWithWrapper(
                ApiConstants.ENDPOINT_STAFF_ENROLL,
                request,
                EnrollUserResponse.class,
                new ApiCallback<ApiResponse<EnrollUserResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<EnrollUserResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                            Log.i(TAG, "✓ USER ENROLLED SUCCESSFULLY");
                            Log.i(TAG, "  - Employee Number: " + response.getData().getEmployeeNumber());
                            Log.i(TAG, "  - Username: " + response.getData().getUsername());
                            Log.i(TAG, "  - Fingerprints Registered: " +
                                    response.getData().getFingerprintsRegistered());
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ ENROLLMENT FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ ENROLLMENT ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Delete user and all associated fingerprints by employee number
     * DELETE /api/StaffEnrollment/{employeeNumber}
     * @param employeeNumber Employee number of user to delete
     * @param callback Response callback
     */
    public void deleteUser(String employeeNumber, ApiCallback<ApiResponse<Boolean>> callback) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ DELETE USER REQUEST");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "╚═══════════════════════════════════════════");

        String endpoint = ApiConstants.ENDPOINT_STAFF_DELETE + employeeNumber;

        Type responseType = new TypeToken<ApiResponse<Boolean>>(){}.getType();
        baseClient.delete(
                endpoint,
                responseType,
                new ApiCallback<ApiResponse<Boolean>>() {
                    @Override
                    public void onSuccess(ApiResponse<Boolean> response) {
                        if (response.isFlag() && response.hasData() && response.getData()) {
                            Log.i(TAG, "✓ USER DELETED SUCCESSFULLY");
                            Log.i(TAG, "  - Employee Number: " + employeeNumber);
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ DELETE FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ DELETE ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Register new user just-in-time (not from CSV) with fingerprints
     * POST /api/StaffEnrollment/register-jit
     * Creates a new user that is not pre-registered via CSV import.
     * All fields required except Department and Branch which are optional.
     * @param request Enrollment request with user data and fingerprints
     * @param callback Response callback
     */
    public void registerJustInTimeUser(EnrollmentRequest request,
                                      ApiCallback<ApiResponse<EnrollUserResponse>> callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ JUST-IN-TIME REGISTRATION REQUEST");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + request.getEmployeeNumber());
        Log.d(TAG, "║ Username: " + request.getUsername());
        Log.d(TAG, "║ Name: " + request.getName());
        Log.d(TAG, "║ Role: " + request.getRole());
        Log.d(TAG, "║ Department: " + request.getDepartment());
        Log.d(TAG, "║ Branch: " + request.getBranch());
        Log.d(TAG, "║ Fingerprints: " + (request.getFingerprints() != null ?
                request.getFingerprints().size() : 0));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Validate request
        String validationError = request.validate();
        if (validationError != null) {
            callback.onError("Validation error: " + validationError);
            return;
        }

        baseClient.postWithWrapper(
                ApiConstants.ENDPOINT_STAFF_REGISTER_JIT,
                request,
                EnrollUserResponse.class,
                new ApiCallback<ApiResponse<EnrollUserResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<EnrollUserResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                            Log.i(TAG, "✓ JIT USER REGISTERED SUCCESSFULLY");
                            Log.i(TAG, "  - Employee Number: " + response.getData().getEmployeeNumber());
                            Log.i(TAG, "  - Username: " + response.getData().getUsername());
                            Log.i(TAG, "  - Fingerprints Registered: " +
                                    response.getData().getFingerprintsRegistered());
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ JIT REGISTRATION FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ JIT REGISTRATION ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Validate fingerprint against existing fingerprints in database
     * POST /api/StaffEnrollment/validate-fingerprint
     * Returns user information if fingerprint matches an existing user
     * @param request Fingerprint validation request
     * @param callback Response callback
     */
    public void validateFingerprint(ValidateFingerprintRequest request,
                                   ApiCallback<ApiResponse<UserListItem>> callback) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ VALIDATE FINGERPRINT REQUEST");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Fingerprint provided: " +
                (request.getFingerPrintBase64() != null && !request.getFingerPrintBase64().isEmpty()));
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // Validate request
        String validationError = request.validate();
        if (validationError != null) {
            callback.onError("Validation error: " + validationError);
            return;
        }

        baseClient.postWithWrapper(
                ApiConstants.ENDPOINT_STAFF_VALIDATE_FINGERPRINT,
                request,
                UserListItem.class,
                new ApiCallback<ApiResponse<UserListItem>>() {
                    @Override
                    public void onSuccess(ApiResponse<UserListItem> response) {
                        if (response.isFlag() && response.hasData()) {
                            Log.i(TAG, "✓ FINGERPRINT VALIDATED SUCCESSFULLY");
                            Log.i(TAG, "  - Username: " + response.getData().getUsername());
                            Log.i(TAG, "  - Name: " + response.getData().getName());
                            Log.i(TAG, "  - Employee Number: " + response.getData().getEmployeeNumber());
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ FINGERPRINT VALIDATION FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ FINGERPRINT VALIDATION ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Get all fingerprint credentials for a user by employee number
     * GET /api/StaffEnrollment/fingerprints/{employeeNumber}
     * Returns user information with all associated fingerprints including their IDs
     * @param employeeNumber Employee number to get fingerprints for
     * @param callback Response callback
     */
    public void getFingerprintsByEmployeeNumber(String employeeNumber,
                                                ApiCallback<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse>> callback) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ GET FINGERPRINTS BY EMPLOYEE REQUEST");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "╚═══════════════════════════════════════════");

        String endpoint = ApiConstants.ENDPOINT_STAFF_GET_FINGERPRINTS + employeeNumber;

        Type responseType = new TypeToken<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse>>(){}.getType();
        baseClient.get(
                endpoint,
                null,
                responseType,
                new ApiCallback<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                            Log.i(TAG, "✓ FINGERPRINTS RETRIEVED SUCCESSFULLY");
                            Log.i(TAG, "  - Employee Number: " + response.getData().getEmployeeNumber());
                            Log.i(TAG, "  - Username: " + response.getData().getUsername());
                            Log.i(TAG, "  - Total Fingerprints: " + response.getData().getTotalFingerprints());
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ GET FINGERPRINTS FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ GET FINGERPRINTS ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }

    /**
     * Get all fingerprint credentials from all users
     * GET /api/FingerprintDownload/employee-fingerprints (NEW optimized endpoint)
     * Returns all employees with their associated fingerprints including their IDs
     * @param callback Response callback
     */
    public void getAllFingerprints(ApiCallback<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.AllFingerprintsResponse>> callback) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ GET ALL FINGERPRINTS REQUEST (NEW ENDPOINT)");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        Type responseType = new TypeToken<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.AllFingerprintsResponse>>(){}.getType();
        baseClient.get(
                ApiConstants.ENDPOINT_FINGERPRINT_DOWNLOAD,
                null,
                responseType,
                new ApiCallback<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.AllFingerprintsResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.AllFingerprintsResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                            Log.i(TAG, "✓ ALL FINGERPRINTS RETRIEVED SUCCESSFULLY");
                            Log.i(TAG, "  - Total Fingerprints: " + response.getData().getTotalFingerprints());
                            Log.i(TAG, "  - Total Users: " +
                                    (response.getData().getUsers() != null ? response.getData().getUsers().size() : 0));
                            callback.onSuccess(response);
                        } else {
                            Log.e(TAG, "✗ GET ALL FINGERPRINTS FAILED: " + response.getMessage());
                            callback.onError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ GET ALL FINGERPRINTS ERROR: " + error);
                        callback.onError(error);
                    }
                }
        );
    }
}
