/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

/**
 * Response DTO for GET /api/ManualOverrideConfiguration
 * Mirrors ManualOverrideConfigurationDto from the PAC API.
 * The endpoint returns a List of these objects.
 */
public class ManualOverrideConfigurationResponse {

    private int id;
    private int maxOverrideDurationDays;
    private boolean isDefault;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMaxOverrideDurationDays() { return maxOverrideDurationDays; }
    public void setMaxOverrideDurationDays(int maxOverrideDurationDays) { this.maxOverrideDurationDays = maxOverrideDurationDays; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
