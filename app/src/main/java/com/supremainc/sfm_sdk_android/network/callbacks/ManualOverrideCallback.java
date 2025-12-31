/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.callbacks;

import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;

/**
 * Callback interface for manual override operations
 */
public interface ManualOverrideCallback {

    /**
     * Called when manual override profile is successfully created
     * @param response Profile response containing details
     */
    void onProfileCreated(ManualOverrideProfileResponse response);

    /**
     * Called when manual override profile is successfully deactivated
     * @param response Profile response containing updated details
     */
    void onProfileDeactivated(ManualOverrideProfileResponse response);

    /**
     * Called when manual override operation fails
     * @param error Error message describing the failure
     */
    void onProfileError(String error);
}
