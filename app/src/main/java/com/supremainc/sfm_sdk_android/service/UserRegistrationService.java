/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.request.FingerCredential;
import com.supremainc.sfm_sdk_android.data.model.request.RegisterUserRequest;
import com.supremainc.sfm_sdk_android.network.api.UserRegistrationApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.UserRegistrationCallback;
import com.supremainc.sfm_sdk_android.utils.FingerprintUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for user registration
 * Handles business logic and data transformation
 */
public class UserRegistrationService {

    private static final String TAG = "UserRegService";

    private final UserRegistrationApiClient apiClient;

    public UserRegistrationService(Context context) {
        this.apiClient = new UserRegistrationApiClient(context);
    }

    /**
     * Register user with fingerprint data from local database
     * Converts byte[] fingerprint templates to Arrays.toString() format
     * This format preserves exact byte values for cross-device compatibility
     * @param username Username/IC number
     * @param name Full name
     * @param employeeNumber Employee/staff ID
     * @param role User role (SUPERVISOR, CUSTODIAN, etc.)
     * @param department Department name
     * @param branch Branch name
     * @param leftFingerprint Left fingerprint template bytes
     * @param rightFingerprint Right fingerprint template bytes
     * @param callback Response callback
     */
    public void registerUserWithFingerprints(
            String username,
            String name,
            String employeeNumber,
            String role,
            String department,
            String branch,
            byte[] leftFingerprint,
            byte[] rightFingerprint,
            UserRegistrationCallback callback) {

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ USER REGISTRATION SERVICE");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Username: " + username);
        Log.d(TAG, "║ Name: " + name);
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "║ Role: " + role);
        Log.d(TAG, "║ Department: " + department);
        Log.d(TAG, "║ Branch: " + branch);
        Log.d(TAG, "║ Left Fingerprint: " + (leftFingerprint != null ? leftFingerprint.length + " bytes" : "null"));
        Log.d(TAG, "║ Right Fingerprint: " + (rightFingerprint != null ? rightFingerprint.length + " bytes" : "null"));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Create request
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername(username);
        request.setName(name);
        request.setEmployeeNumber(employeeNumber);
        request.setRole(role);
        request.setDepartment(department);
        request.setBranch(branch);

        // Convert fingerprints to Arrays.toString() format and create credentials
        List<FingerCredential> fingerprints = new ArrayList<>();

        if (leftFingerprint != null && leftFingerprint.length > 0) {
            FingerCredential leftCred = new FingerCredential();
            String leftString = FingerprintUtils.convertByteArrayToString(leftFingerprint);
            leftCred.setFingerPrintBase64(leftString);
            leftCred.setLeftRight(0);  // 0 = Left
            leftCred.setFingerIndex(1); // Index finger (can be made configurable)
            leftCred.setFingerType("Index");
            fingerprints.add(leftCred);

            Log.d(TAG, "Left Fingerprint Credential:");
            Log.d(TAG, "  - LeftRight: 0");
            Log.d(TAG, "  - FingerIndex: 1");
            Log.d(TAG, "  - FingerType: Index");
            Log.d(TAG, "  - Template Size: " + leftFingerprint.length + " bytes");
            Log.d(TAG, "  - String Length: " + leftString.length() + " chars");
            Log.d(TAG, "  - String Preview: " + leftString.substring(0, Math.min(50, leftString.length())) + "...");
        }

        if (rightFingerprint != null && rightFingerprint.length > 0) {
            FingerCredential rightCred = new FingerCredential();
            String rightString = FingerprintUtils.convertByteArrayToString(rightFingerprint);
            rightCred.setFingerPrintBase64(rightString);
            rightCred.setLeftRight(1);  // 1 = Right
            rightCred.setFingerIndex(2);
            rightCred.setFingerType("Index");
            fingerprints.add(rightCred);

            Log.d(TAG, "Right Fingerprint Credential:");
            Log.d(TAG, "  - LeftRight: 1");
            Log.d(TAG, "  - FingerIndex: 2");
            Log.d(TAG, "  - FingerType: Index");
            Log.d(TAG, "  - Template Size: " + rightFingerprint.length + " bytes");
            Log.d(TAG, "  - String Length: " + rightString.length() + " chars");
            Log.d(TAG, "  - String Preview: " + rightString.substring(0, Math.min(50, rightString.length())) + "...");
        }

        request.setFingerprints(fingerprints);

        // Validate
        if (fingerprints.isEmpty()) {
            Log.e(TAG, "No fingerprints provided");
            callback.onRegistrationError("At least one fingerprint is required");
            return;
        }

        Log.i(TAG, "Registering user with " + fingerprints.size() + " fingerprint(s)");

        // Send to API
        apiClient.registerUser(request, callback);
    }

    /**
     * Register user with multiple fingerprints
     * @param username Username
     * @param name Full name
     * @param employeeNumber Employee number
     * @param role User role
     * @param department Department
     * @param branch Branch
     * @param fingerprintData List of fingerprint data (byte arrays)
     * @param fingerprintMetadata List of metadata (left/right, finger type, index)
     * @param callback Response callback
     */
    public void registerUserWithMultipleFingerprints(
            String username,
            String name,
            String employeeNumber,
            String role,
            String department,
            String branch,
            List<byte[]> fingerprintData,
            List<FingerprintMetadata> fingerprintMetadata,
            UserRegistrationCallback callback) {

        Log.d(TAG, "Preparing registration for: " + username + " with " + fingerprintData.size() + " fingerprints");

        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername(username);
        request.setName(name);
        request.setEmployeeNumber(employeeNumber);
        request.setRole(role);
        request.setDepartment(department);
        request.setBranch(branch);

        List<FingerCredential> fingerprints = new ArrayList<>();

        for (int i = 0; i < fingerprintData.size() && i < fingerprintMetadata.size(); i++) {
            byte[] fpData = fingerprintData.get(i);
            FingerprintMetadata metadata = fingerprintMetadata.get(i);

            if (fpData != null && fpData.length > 0) {
                FingerCredential cred = new FingerCredential();
                cred.setFingerPrintBase64(
                        FingerprintUtils.convertByteArrayToString(fpData)
                );
                cred.setLeftRight(metadata.leftRight);
                cred.setFingerIndex(metadata.fingerIndex);
                cred.setFingerType(metadata.fingerType);
                fingerprints.add(cred);

                Log.d(TAG, "Added fingerprint: " + metadata.fingerType + " " +
                        (metadata.leftRight == 0 ? "Left" : "Right"));
            }
        }

        request.setFingerprints(fingerprints);

        if (fingerprints.isEmpty()) {
            callback.onRegistrationError("At least one fingerprint is required");
            return;
        }

        apiClient.registerUser(request, callback);
    }

    /**
     * Fingerprint metadata class
     */
    public static class FingerprintMetadata {
        public int leftRight;      // 0 = Left, 1 = Right
        public int fingerIndex;    // 1-10
        public String fingerType;  // "Thumb", "Index", etc.

        public FingerprintMetadata(int leftRight, int fingerIndex, String fingerType) {
            this.leftRight = leftRight;
            this.fingerIndex = fingerIndex;
            this.fingerType = fingerType;
        }
    }
}
