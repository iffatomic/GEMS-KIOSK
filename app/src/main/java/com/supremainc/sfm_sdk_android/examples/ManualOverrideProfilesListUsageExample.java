/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.examples;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.service.ManualOverrideService;

import java.util.List;

/**
 * Example usage of ManualOverrideService.getAllProfilesList()
 * Demonstrates how to fetch all manual override profiles with essential fields
 */
public class ManualOverrideProfilesListUsageExample {

    private static final String TAG = "ProfilesListExample";

    /**
     * Fetch all manual override profiles
     * @param context Android context
     */
    public static void fetchAllProfiles(Context context) {
        // Create service instance
        ManualOverrideService service = new ManualOverrideService(context);

        // Fetch all profiles
        service.getAllProfilesList(new ApiCallback<List<ManualOverrideProfileListItem>>() {
            @Override
            public void onSuccess(List<ManualOverrideProfileListItem> profiles) {
                Log.i(TAG, "✓ Successfully fetched " + profiles.size() + " profiles");
                Log.i(TAG, "═══════════════════════════════════════════");

                // Process each profile
                for (ManualOverrideProfileListItem profile : profiles) {
                    Log.i(TAG, "Profile ID: " + profile.getId());
                    Log.i(TAG, "  Vault: " + profile.getVaultName());
                    Log.i(TAG, "  Custodian 1: " + profile.getCustodian1());
                    Log.i(TAG, "  Custodian 2: " + profile.getCustodian2());
                    Log.i(TAG, "  Custodian 3: " + profile.getCustodian3());
                    Log.i(TAG, "  Status: " + profile.getStatus());
                    Log.i(TAG, "  Created At: " + profile.getCreatedAt());
                    Log.i(TAG, "  Activated At: " + profile.getActivatedAt());
                    Log.i(TAG, "  Deactivated At: " + profile.getDeactivatedAt());
                    Log.i(TAG, "───────────────────────────────────────────");
                }

                // Example: Filter by status
                filterByStatus(profiles, "Active");
                filterByStatus(profiles, "Deactivated");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Error fetching profiles: " + error);
            }
        });
    }

    /**
     * Filter profiles by status
     * @param profiles List of all profiles
     * @param status Status to filter by (e.g., "Active", "Pending", "Deactivated")
     */
    private static void filterByStatus(List<ManualOverrideProfileListItem> profiles, String status) {
        Log.i(TAG, "\nFiltering profiles by status: " + status);

        int count = 0;
        for (ManualOverrideProfileListItem profile : profiles) {
            if (status.equalsIgnoreCase(profile.getStatus())) {
                count++;
                Log.i(TAG, "  - " + profile.getVaultName() + " (" + profile.getId() + ")");
            }
        }

        Log.i(TAG, "Found " + count + " profile(s) with status: " + status);
    }

    /**
     * Example: Get profiles that were activated (have ActivatedAt timestamp)
     * @param profiles List of all profiles
     */
    private static void getActivatedProfiles(List<ManualOverrideProfileListItem> profiles) {
        Log.i(TAG, "\nProfiles that were activated:");

        int count = 0;
        for (ManualOverrideProfileListItem profile : profiles) {
            if (profile.getActivatedAt() != null && !profile.getActivatedAt().isEmpty()) {
                count++;
                Log.i(TAG, "  - " + profile.getVaultName() +
                           " | Activated: " + profile.getActivatedAt() +
                           " | Status: " + profile.getStatus());
            }
        }

        Log.i(TAG, "Total activated profiles: " + count);
    }

    /**
     * Example: Get profiles that were deactivated (have DeactivatedAt timestamp)
     * @param profiles List of all profiles
     */
    private static void getDeactivatedProfiles(List<ManualOverrideProfileListItem> profiles) {
        Log.i(TAG, "\nProfiles that were deactivated:");

        int count = 0;
        for (ManualOverrideProfileListItem profile : profiles) {
            if (profile.getDeactivatedAt() != null && !profile.getDeactivatedAt().isEmpty()) {
                count++;
                Log.i(TAG, "  - " + profile.getVaultName() +
                           " | Deactivated: " + profile.getDeactivatedAt() +
                           " | Custodians: " + profile.getCustodian1() + ", " +
                           profile.getCustodian2() + ", " + profile.getCustodian3());
            }
        }

        Log.i(TAG, "Total deactivated profiles: " + count);
    }

    /**
     * Example: Usage in an Activity
     */
    public static class ActivityExample {

        private ManualOverrideService manualOverrideService;

        public void onCreate(Context context) {
            // Initialize service
            manualOverrideService = new ManualOverrideService(context);

            // Fetch profiles
            loadProfiles();
        }

        private void loadProfiles() {
            Log.d(TAG, "Loading manual override profiles...");

            manualOverrideService.getAllProfilesList(new ApiCallback<List<ManualOverrideProfileListItem>>() {
                @Override
                public void onSuccess(List<ManualOverrideProfileListItem> profiles) {
                    // Update UI on success
                    onProfilesLoaded(profiles);
                }

                @Override
                public void onError(String error) {
                    // Show error to user
                    onProfilesLoadError(error);
                }
            });
        }

        private void onProfilesLoaded(List<ManualOverrideProfileListItem> profiles) {
            Log.i(TAG, "Profiles loaded successfully: " + profiles.size() + " items");

            // Example: Update RecyclerView adapter
            // adapter.setProfiles(profiles);
            // adapter.notifyDataSetChanged();

            // Example: Display in UI
            for (ManualOverrideProfileListItem profile : profiles) {
                // Add to list, update UI, etc.
                displayProfile(profile);
            }
        }

        private void onProfilesLoadError(String error) {
            Log.e(TAG, "Failed to load profiles: " + error);

            // Example: Show error message to user
            // Toast.makeText(context, "Error: " + error, Toast.LENGTH_LONG).show();
        }

        private void displayProfile(ManualOverrideProfileListItem profile) {
            // Example display logic
            String displayText = String.format(
                "Vault: %s | Status: %s | Custodians: %s, %s, %s",
                profile.getVaultName(),
                profile.getStatus(),
                profile.getCustodian1(),
                profile.getCustodian2(),
                profile.getCustodian3()
            );

            Log.d(TAG, displayText);
        }
    }
}
