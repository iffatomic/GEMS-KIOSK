/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages manual override states for all vaults
 * Tracks which vaults are currently under manual override and their expiration times
 */
public class VaultOverrideManager {

    private static VaultOverrideManager instance;
    private SharedPreferences prefs;
    private static final String PREF_NAME = "vault_override_prefs";
    private static final String KEY_ACTIVE_OVERRIDES = "active_overrides";

    private VaultOverrideManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized VaultOverrideManager getInstance(Context context) {
        if (instance == null) {
            instance = new VaultOverrideManager(context);
        }
        return instance;
    }

    /**
     * Represents a vault override entry
     */
    public static class VaultOverride {
        public String vaultName;
        public String supervisorId;
        public long startTimeMillis;
        public long endTimeMillis;
        public int durationHours;

        public VaultOverride(String vaultName, String supervisorId, long startTimeMillis, int durationHours) {
            this.vaultName = vaultName;
            this.supervisorId = supervisorId;
            this.startTimeMillis = startTimeMillis;
            this.durationHours = durationHours;
            this.endTimeMillis = startTimeMillis + (durationHours * 3600000L); // Convert hours to milliseconds
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= endTimeMillis;
        }

        public long getRemainingTimeMillis() {
            long remaining = endTimeMillis - System.currentTimeMillis();
            return Math.max(0, remaining);
        }

        public String getRemainingTimeFormatted() {
            long remainingMillis = getRemainingTimeMillis();
            if (remainingMillis <= 0) {
                return "EXPIRED";
            }

            long hours = remainingMillis / 3600000;
            long minutes = (remainingMillis % 3600000) / 60000;
            long seconds = (remainingMillis % 60000) / 1000;

            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        // Serialize to string for storage
        public String serialize() {
            return vaultName + "|" + supervisorId + "|" + startTimeMillis + "|" + durationHours;
        }

        // Deserialize from string
        public static VaultOverride deserialize(String data) {
            try {
                String[] parts = data.split("\\|");
                if (parts.length == 4) {
                    return new VaultOverride(
                            parts[0],
                            parts[1],
                            Long.parseLong(parts[2]),
                            Integer.parseInt(parts[3])
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Add a new vault override
     */
    public void addOverride(String vaultName, String supervisorId, int durationHours) {
        VaultOverride override = new VaultOverride(
                vaultName,
                supervisorId,
                System.currentTimeMillis(),
                durationHours
        );

        List<VaultOverride> overrides = getActiveOverrides();

        // Remove any existing override for this vault
        overrides.removeIf(o -> o.vaultName.equals(vaultName));

        // Add new override
        overrides.add(override);

        // Save to preferences
        saveOverrides(overrides);
    }

    /**
     * Remove a vault override
     */
    public void removeOverride(String vaultName) {
        List<VaultOverride> overrides = getActiveOverrides();
        overrides.removeIf(o -> o.vaultName.equals(vaultName));
        saveOverrides(overrides);
    }

    /**
     * Remove multiple vault overrides by vault names
     * Used for cutoff time deactivation
     */
    public void removeOverridesByVaultNames(List<String> vaultNames) {
        if (vaultNames == null || vaultNames.isEmpty()) {
            return;
        }

        List<VaultOverride> overrides = getActiveOverrides();
        overrides.removeIf(o -> vaultNames.contains(o.vaultName));
        saveOverrides(overrides);
    }

    /**
     * Get all active (non-expired) overrides
     */
    public List<VaultOverride> getActiveOverrides() {
        List<VaultOverride> overrides = new ArrayList<>();

        Set<String> savedOverrides = prefs.getStringSet(KEY_ACTIVE_OVERRIDES, new HashSet<>());

        for (String overrideData : savedOverrides) {
            VaultOverride override = VaultOverride.deserialize(overrideData);
            if (override != null && !override.isExpired()) {
                overrides.add(override);
            }
        }

        // Clean up expired overrides
        if (overrides.size() != savedOverrides.size()) {
            saveOverrides(overrides);
        }

        return overrides;
    }

    /**
     * Check if a specific vault is under override
     */
    public boolean isVaultOverridden(String vaultName) {
        List<VaultOverride> overrides = getActiveOverrides();
        for (VaultOverride override : overrides) {
            if (override.vaultName.equals(vaultName) && !override.isExpired()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get override info for a specific vault
     */
    public VaultOverride getVaultOverride(String vaultName) {
        List<VaultOverride> overrides = getActiveOverrides();
        for (VaultOverride override : overrides) {
            if (override.vaultName.equals(vaultName) && !override.isExpired()) {
                return override;
            }
        }
        return null;
    }

    /**
     * Save overrides to preferences
     */
    private void saveOverrides(List<VaultOverride> overrides) {
        Set<String> serializedOverrides = new HashSet<>();
        for (VaultOverride override : overrides) {
            serializedOverrides.add(override.serialize());
        }

        prefs.edit()
                .putStringSet(KEY_ACTIVE_OVERRIDES, serializedOverrides)
                .apply();
    }

    /**
     * Clear all overrides (for testing/reset)
     */
    public void clearAllOverrides() {
        prefs.edit()
                .remove(KEY_ACTIVE_OVERRIDES)
                .apply();
    }

    /**
     * Get count of active overrides
     */
    public int getActiveOverrideCount() {
        return getActiveOverrides().size();
    }
}