/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.PacApiClient;
import com.supremainc.sfm_sdk_android.data.model.request.CreateManualOverrideProfileRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileListItem;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.ManualOverrideCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API client for manual override profile operations
 * Handles communication with /api/ManualOverride endpoints
 */
public class ManualOverrideApiClient {

    private static final String TAG = "ManualOverrideApiClient";

    private final PacApiClient baseClient;

    public ManualOverrideApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Create manual override profile
     * POST /api/ManualOverride/profiles
     * @param request Profile creation request
     * @param callback Response callback
     */
    public void createProfile(CreateManualOverrideProfileRequest request,
                             ManualOverrideCallback callback) {
        Log.d(TAG, "Creating override profile for: " + request.getVariableName());

        // Validate request
        String validationError = request.validate();
        if (validationError != null) {
            callback.onProfileError("Validation error: " + validationError);
            return;
        }

        baseClient.post(
                ApiConstants.ENDPOINT_MANUAL_OVERRIDE,
                request,
                ManualOverrideProfileResponse.class,
                new ApiCallback<ManualOverrideProfileResponse>() {
                    @Override
                    public void onSuccess(ManualOverrideProfileResponse response) {
                        Log.i(TAG, "Profile created: " + response.getProfileId());
                        callback.onProfileCreated(response);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Profile creation error: " + error);
                        callback.onProfileError(error);
                    }
                }
        );
    }

    /**
     * Deactivate manual override profile
     * POST /api/ManualOverride/deactivate/{profileId}
     * @param profileId Profile ID to deactivate
     * @param callback Response callback
     */
    public void deactivateProfile(String profileId, ManualOverrideCallback callback) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ DEACTIVATE PROFILE REQUEST");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Profile ID: " + profileId);

        String endpoint = ApiConstants.ENDPOINT_DEACTIVATE_OVERRIDE + profileId;
        Log.d(TAG, "║ Endpoint: " + endpoint);
        Log.d(TAG, "║ Expected URL: {base}" + endpoint);
        Log.d(TAG, "╚═══════════════════════════════════════════");

        baseClient.post(
                endpoint,
                null,  // Empty body
                ManualOverrideProfileResponse.class,
                new ApiCallback<ManualOverrideProfileResponse>() {
                    @Override
                    public void onSuccess(ManualOverrideProfileResponse response) {
                        Log.i(TAG, "✓ DEACTIVATION SUCCESS");
                        Log.i(TAG, "  - Profile ID: " + response.getProfileId());
                        Log.i(TAG, "  - Door: " + response.getDoorName());
                        Log.i(TAG, "  - Status: " + response.getStatus());
                        callback.onProfileDeactivated(response);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ DEACTIVATION FAILED");
                        Log.e(TAG, "  - Profile ID attempted: " + profileId);
                        Log.e(TAG, "  - Error: " + error);
                        callback.onProfileError(error);
                    }
                }
        );
    }

    /**
     * Get all profiles with optional filtering
     * GET /api/ManualOverride/all-profiles?status={status}&variableName={name}
     * @param status Profile status (0-4), null for all
     * @param variableName Variable name to filter by, null for all
     * @param callback Response callback
     */
    public void getAllProfiles(Integer status, String variableName,
                              ApiCallback<List<ManualOverrideProfileResponse>> callback) {
        Log.d(TAG, "Getting all profiles (status: " + status + ", variable: " + variableName + ")");

        Map<String, String> queryParams = new HashMap<>();
        if (status != null) {
            queryParams.put("status", String.valueOf(status));
        }
        if (variableName != null && !variableName.isEmpty()) {
            queryParams.put("variableName", variableName);
        }

        Type responseType = new TypeToken<ApiResponse<List<ManualOverrideProfileResponse>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_PROFILES_LIST , queryParams, responseType,
            new ApiCallback<ApiResponse<List<ManualOverrideProfileResponse>>>() {
                @Override
                public void onSuccess(ApiResponse<List<ManualOverrideProfileResponse>> response) {
                    if (response.isSuccess() && response.hasData()) {
                        callback.onSuccess(response.getData());
                    } else {
                        callback.onError(response.getMessage() != null ? response.getMessage() : "No data returned");
                    }
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
    }

    /**
     * Get valid/active profiles
     * GET /api/ManualOverride/valid-profile
     * @param callback Response callback
     */
    public void getValidProfiles(ApiCallback<List<ManualOverrideProfileResponse>> callback) {
        Log.d(TAG, "Getting valid profiles");

        Type responseType = new TypeToken<ApiResponse<List<ManualOverrideProfileResponse>>>(){}.getType();
        baseClient.get(ApiConstants.ENDPOINT_VALID_PROFILE, null, responseType,
            new ApiCallback<ApiResponse<List<ManualOverrideProfileResponse>>>() {
                @Override
                public void onSuccess(ApiResponse<List<ManualOverrideProfileResponse>> response) {
                    if (response.isSuccess() && response.hasData()) {
                        callback.onSuccess(response.getData());
                    } else {
                        callback.onError(response.getMessage() != null ? response.getMessage() : "No data returned");
                    }
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
    }

    /**
     * Get all profiles list with essential fields only
     * GET /api/ManualOverride/profiles-list
     * Returns: Id, Custodian1-3, Status, ActivatedAt, DeactivatedAt, VaultName, CreatedAt
     * @param callback Response callback
     */
    public void getProfilesList(ApiCallback<List<ManualOverrideProfileListItem>> callback) {
        Log.d(TAG, "Getting profiles list with essential fields");

        // Define the response type for nested ApiResponse structure
        Type responseType = new TypeToken<ApiResponse<List<ManualOverrideProfileListItem>>>(){}.getType();

        baseClient.get(
            ApiConstants.ENDPOINT_PROFILES_LIST,
            null,  // No query parameters
            responseType,
            new ApiCallback<ApiResponse<List<ManualOverrideProfileListItem>>>() {
                @Override
                public void onSuccess(ApiResponse<List<ManualOverrideProfileListItem>> response) {
                    if (response.isSuccess() && response.hasData()) {
                        Log.i(TAG, "Retrieved " + response.getData().size() + " profiles");
                        callback.onSuccess(response.getData());
                    } else {
                        String errorMsg = response.getMessage() != null ? response.getMessage() : "No data returned";
                        Log.w(TAG, "Profiles list response unsuccessful: " + errorMsg);
                        callback.onError(errorMsg);
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error fetching profiles list: " + error);
                    callback.onError(error);
                }
            }
        );
    }
}
