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
import com.supremainc.sfm_sdk_android.data.model.response.ControllerProfileResponse;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API client for controller profile operations
 * Handles communication with /api/twincat/aws/controller-profiles endpoints
 */
public class ControllerProfileApiClient {

    private static final String TAG = "ControllerProfileApi";

    private final PacApiClient baseClient;

    public ControllerProfileApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Get all controller profiles with interimMode=false
     * GET /api/twincat/aws/controller-profiles?interimMode=false
     * @param callback Response callback
     */
    public void getAllControllerProfiles(ApiCallback<List<ControllerProfileResponse>> callback) {
        Log.d(TAG, "Getting all controller profiles (interimMode=false)");

        // Define the response type for nested ApiResponse structure
        // The API returns: { "success": true, "count": N, "data": [...], "interimMode": false, "timestamp": "..." }
        Type responseType = new TypeToken<ApiResponse<List<ControllerProfileResponse>>>(){}.getType();

        // Add query parameter for interimMode=false
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("interimMode", "false");

        baseClient.get(
            ApiConstants.ENDPOINT_CONTROLLER_PROFILES,
            queryParams,
            responseType,
            new ApiCallback<ApiResponse<List<ControllerProfileResponse>>>() {
                @Override
                public void onSuccess(ApiResponse<List<ControllerProfileResponse>> response) {
                    if (response.isSuccess() && response.hasData()) {
                        Log.i(TAG, "Retrieved " + response.getData().size() + " controller profiles");
                        callback.onSuccess(response.getData());
                    } else {
                        String errorMsg = response.getMessage() != null ? response.getMessage() : "No data returned";
                        Log.w(TAG, "Controller profiles response unsuccessful: " + errorMsg);
                        callback.onError(errorMsg);
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error fetching controller profiles: " + error);
                    callback.onError(error);
                }
            }
        );
    }
}
