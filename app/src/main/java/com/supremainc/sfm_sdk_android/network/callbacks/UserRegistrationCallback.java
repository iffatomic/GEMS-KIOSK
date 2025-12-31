/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.callbacks;

import com.supremainc.sfm_sdk_android.data.model.response.RegisterUserResponse;

/**
 * Callback interface for user registration operations
 */
public interface UserRegistrationCallback {

    /**
     * Called when user registration is successful
     * @param response Registration response containing user details
     */
    void onRegistrationSuccess(RegisterUserResponse response);

    /**
     * Called when user registration fails
     * @param error Error message describing the failure
     */
    void onRegistrationError(String error);
}
