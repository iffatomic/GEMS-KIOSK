/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.PacApiClient;
import com.supremainc.sfm_sdk_android.data.model.request.RegisterUserRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.RegisterUserResponse;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.UserRegistrationCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;

import java.lang.reflect.Type;

/**
 * API client for user registration operations
 * Handles communication with /api/UserRegistration endpoints
 */
public class UserRegistrationApiClient {

    private static final String TAG = "UserRegApiClient";

    private final PacApiClient baseClient;

    public UserRegistrationApiClient(Context context) {
        this.baseClient = new PacApiClient(context);
    }

    /**
     * Register a new user with fingerprint credentials
     * POST /api/UserRegistration/register
     * @param request Registration request with fingerprint data
     * @param callback Response callback
     */
    public void registerUser(RegisterUserRequest request, UserRegistrationCallback callback) {
        Log.d(TAG, "Registering user: " + request.getUsername());

        // Validate request
        String validationError = request.validate();
        if (validationError != null) {
            callback.onRegistrationError("Validation error: " + validationError);
            return;
        }

        baseClient.postWithWrapper(
                ApiConstants.ENDPOINT_USER_REGISTRATION,
                request,
                RegisterUserResponse.class,
                new ApiCallback<ApiResponse<RegisterUserResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<RegisterUserResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                        Log.i(TAG, "User registered successfully: " + response.getData().getUsername());
                            callback.onRegistrationSuccess(response.getData());
                        } else {
                            Log.e(TAG, "Registration failed: " + response.getMessage());
                            callback.onRegistrationError(response.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Registration error: " + error);
                        callback.onRegistrationError(error);
                    }
                }
        );
    }

    /**
     * Check if username exists
     * GET /api/UserRegistration/check-username/{username}
     * @param username Username to check
     * @param callback Response callback (true if exists, false if available)
     */
    public void checkUsernameExists(String username, ApiCallback<ApiResponse<Boolean>> callback) {
        Log.d(TAG, "Checking username: " + username);

        String endpoint = ApiConstants.ENDPOINT_CHECK_USERNAME + username;

        Type responseType = new TypeToken<ApiResponse<Boolean>>(){}.getType();
        baseClient.get(endpoint, null, responseType, callback);
    }

    /**
     * Check if employee number exists
     * GET /api/UserRegistration/check-employee-number/{employeeNumber}
     * @param employeeNumber Employee number to check
     * @param callback Response callback (true if exists, false if available)
     */
    public void checkEmployeeNumberExists(String employeeNumber,
                                         ApiCallback<ApiResponse<Boolean>> callback) {
        Log.d(TAG, "Checking employee number: " + employeeNumber);

        String endpoint = ApiConstants.ENDPOINT_CHECK_EMPLOYEE + employeeNumber;

        Type responseType = new TypeToken<ApiResponse<Boolean>>(){}.getType();
        baseClient.get(endpoint, null, responseType, callback);
    }
}
