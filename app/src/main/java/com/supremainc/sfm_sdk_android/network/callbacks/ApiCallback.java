/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.callbacks;

/**
 * Generic callback interface for API operations
 * @param <T> Type of the response data
 */
public interface ApiCallback<T> {

    /**
     * Called when the API request is successful
     * @param response The response data
     */
    void onSuccess(T response);

    /**
     * Called when the API request fails
     * @param error Error message describing the failure
     */
    void onError(String error);
}
