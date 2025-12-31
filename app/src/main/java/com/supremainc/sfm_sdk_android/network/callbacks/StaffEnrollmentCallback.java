/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.network.callbacks;

import com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;

import java.util.List;

/**
 * Callback interface for staff enrollment operations
 */
public interface StaffEnrollmentCallback {

    /**
     * Called when enrollment is successful
     * @param response Enrollment response containing user details
     */
    void onEnrollmentSuccess(EnrollUserResponse response);

    /**
     * Called when enrollment fails
     * @param error Error message describing the failure
     */
    void onEnrollmentError(String error);

    /**
     * Called when pending users list is retrieved successfully
     * @param users List of users pending enrollment
     */
    void onPendingUsersRetrieved(List<UserListItem> users);

    /**
     * Called when user validation is successful
     * @param user User information
     */
    void onUserValidated(UserListItem user);

    /**
     * Called when user validation fails (user not found)
     */
    void onUserNotFound();

    /**
     * Called when user deletion is successful
     */
    void onUserDeleted();

    /**
     * Called when fingerprint validation is successful
     * @param user User information matched with the fingerprint
     */
    void onFingerprintValidated(UserListItem user);

    /**
     * Called when fingerprint validation fails (no match found)
     */
    void onFingerprintNotFound();

    /**
     * Called when fingerprint sync starts after enrollment
     * @param totalFingerprints Total number of fingerprints to sync
     */
    void onSyncStarted(int totalFingerprints);

    /**
     * Called during sync to report progress
     * @param current Current fingerprint being processed
     * @param total Total fingerprints
     * @param userName Name of user being processed
     */
    void onSyncProgress(int current, int total, String userName);

    /**
     * Called when fingerprint sync completes successfully
     * @param successCount Number of successfully synced fingerprints
     * @param failCount Number of failed fingerprints
     */
    void onSyncCompleted(int successCount, int failCount);
}
