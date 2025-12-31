/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

/**
 * Day Vault Status Activity
 * Extends MainVaultStatusActivity with Day Vault specific configuration
 * Shows the status of Day Vault entry points (Main Grill, Compartments 1-2)
 * Allows deactivation of active overrides with custodian fingerprint verification
 */
public class DayVaultStatusActivity extends MainVaultStatusActivity {

    @Override
    protected String getVaultType() {
        return "DAY";
    }

    @Override
    protected String getVaultDisplayName() {
        return "Day Vault";
    }
}
