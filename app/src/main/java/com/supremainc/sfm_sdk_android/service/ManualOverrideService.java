/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.request.CreateManualOverrideProfileRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileListItem;
import com.supremainc.sfm_sdk_android.dto.common.DateTimeHelper;
import com.supremainc.sfm_sdk_android.network.api.ManualOverrideApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.ManualOverrideCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.util.List;

/**
 * Service layer for manual override operations
 * Handles business logic, validation, and data transformation
 */
public class ManualOverrideService {

    private static final String TAG = "ManualOverrideService";

    private final ManualOverrideApiClient apiClient;
    private final DateTimeHelper dateTimeHelper;

    public ManualOverrideService(Context context) {
        this.apiClient = new ManualOverrideApiClient(context);
        this.dateTimeHelper = new DateTimeHelper();
    }

    /**
     * Create manual override profile with time range
     * @param doorId Door ID (1-7)
     * @param vaultName Human-readable vault name (e.g., "Main Vault", "Day Vault")
     * @param username Username of custodian
     * @param name Full name
     * @param employeeNumber Employee number
     * @param department Department
     * @param startTimeMillis Start time in milliseconds
     * @param endTimeMillis End time in milliseconds
     * @param requestedBy Who requested the override
     * @param callback Response callback
     */
    public void createOverrideProfile(
            int doorId,
            String vaultName,
            String username,
            String name,
            String employeeNumber,
            String department,
            long startTimeMillis,
            long endTimeMillis,
            String requestedBy,
            ManualOverrideCallback callback) {

        Log.d(TAG, "Creating override profile for door: " + doorId + " (" + vaultName + ")");

        // Map doorId to variableName
        String variableName = mapDoorIdToVariableName(doorId);
        if (variableName == null) {
            Log.e(TAG, "Invalid door ID: " + doorId);
            callback.onProfileError("Invalid door ID: " + doorId);
            return;
        }

        // Create request
        CreateManualOverrideProfileRequest request = new CreateManualOverrideProfileRequest();
        request.setVariableName(variableName);
        request.setVaultName(vaultName);
        // Map individual fields to custodian1, custodian2, custodian3
        request.setCustodian1(username);
        request.setCustodian2(name);
        request.setCustodian3(employeeNumber + (department != null && !department.isEmpty() ? " - " + department : ""));
        request.setRequestedBy(requestedBy);

        // Convert timestamps to ISO 8601 UTC format
        request.setStartDateTime(dateTimeHelper.toIso8601Utc(startTimeMillis));
        request.setEndDateTime(dateTimeHelper.toIso8601Utc(endTimeMillis));

        Log.i(TAG, "Profile request: " + variableName + " (" +
                request.getStartDateTime() + " to " + request.getEndDateTime() + ")");
        Log.d(TAG, "Request details:");
        Log.d(TAG, "  - variableName: " + request.getVariableName());
        Log.d(TAG, "  - vaultName: " + request.getVaultName());
        Log.d(TAG, "  - custodian1: " + request.getCustodian1());
        Log.d(TAG, "  - custodian2: " + request.getCustodian2());
        Log.d(TAG, "  - custodian3: " + request.getCustodian3());
        Log.d(TAG, "  - requestedBy: " + request.getRequestedBy());
        Log.d(TAG, "  - startDateTime: " + request.getStartDateTime());
        Log.d(TAG, "  - endDateTime: " + request.getEndDateTime());

        // Send to API
        apiClient.createProfile(request, callback);
    }

    /**
     * Create indefinite override profile (no end time)
     * @param doorId Door ID
     * @param vaultName Human-readable vault name (e.g., "Main Vault", "Day Vault")
     * @param custodian1Name First custodian's full name
     * @param custodian2Name Second custodian's full name
     * @param custodian3Name Third custodian's full name
     * @param startTimeMillis Start time in milliseconds (when override was activated)
     * @param requestedBy Requester
     * @param callback Response callback
     */
    public void createIndefiniteOverrideProfile(
            int doorId,
            String vaultName,
            String custodian1Name,
            String custodian2Name,
            String custodian3Name,
            long startTimeMillis,
            String requestedBy,
            ManualOverrideCallback callback) {

        Log.d(TAG, "Creating indefinite override profile for door: " + doorId + " (" + vaultName + ")");
        Log.d(TAG, "Custodians: " + custodian1Name + ", " + custodian2Name + ", " + custodian3Name);

        String variableName = mapDoorIdToVariableName(doorId);
        if (variableName == null) {
            callback.onProfileError("Invalid door ID: " + doorId);
            return;
        }

        CreateManualOverrideProfileRequest request = new CreateManualOverrideProfileRequest();
        request.setVariableName(variableName);
        request.setVaultName(vaultName);
        // Set the 3 custodian names who verified their fingerprints
        request.setCustodian1(custodian1Name);
        request.setCustodian2(custodian2Name);
        request.setCustodian3(custodian3Name);
        request.setRequestedBy(requestedBy);

        // Set start time but no end time (indefinite)
        request.setStartDateTime(dateTimeHelper.toIso8601Utc(startTimeMillis));
        // endDateTime is null = no end time

        Log.i(TAG, "Creating indefinite profile for: " + variableName + " starting at " + request.getStartDateTime());

        apiClient.createProfile(request, callback);
    }

    /**
     * Deactivate existing override profile
     * @param profileId Profile ID to deactivate
     * @param callback Response callback
     */
    public void deactivateProfile(String profileId, ManualOverrideCallback callback) {
        Log.d(TAG, "Deactivating profile: " + profileId);
        apiClient.deactivateProfile(profileId, callback);
    }

    /**
     * Map door ID to PLC variable name
     * @param doorId Door ID (1-7)
     * @return Variable name (e.g., "MAIN.SOFT_LOCK_A")
     */
    private String mapDoorIdToVariableName(int doorId) {
        switch (doorId) {
            case 1: return ApiConstants.VARIABLE_DOOR_A;
            case 2: return ApiConstants.VARIABLE_DOOR_B;
            case 3: return ApiConstants.VARIABLE_DOOR_C;
            case 4: return ApiConstants.VARIABLE_DOOR_D;
            case 5: return ApiConstants.VARIABLE_DOOR_E;
            case 6: return ApiConstants.VARIABLE_DOOR_F;
            case 7: return ApiConstants.VARIABLE_DOOR_G;
            default:
                Log.w(TAG, "Unknown door ID: " + doorId);
                return null;
        }
    }

    /**
     * Get door name from door ID
     * @param doorId Door ID
     * @return Human-readable door name
     */
    public static String getDoorName(int doorId) {
        switch (doorId) {
            case 1: return "Vault Door A";
            case 2: return "Vault Door B";
            case 3: return "Vault Door C";
            case 4: return "Vault Door D";
            case 5: return "Vault Door E";
            case 6: return "Vault Door F";
            case 7: return "Vault Door G";
            default: return "Unknown Door";
        }
    }

    /**
     * Get all profiles list with essential fields
     * Returns: Id, Custodian1-3, Status, ActivatedAt, DeactivatedAt, VaultName, CreatedAt
     * @param callback Response callback with list of profile items
     */
    public void getAllProfilesList(ApiCallback<List<ManualOverrideProfileListItem>> callback) {
        Log.d(TAG, "Fetching all profiles list");
        apiClient.getProfilesList(callback);
    }
}
