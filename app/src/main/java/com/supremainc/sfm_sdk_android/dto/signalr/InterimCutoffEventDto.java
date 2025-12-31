/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

import java.util.List;

/**
 * DTO for interim mode cutoff time reached events
 * Received when the daily cutoff time (e.g., 8PM) is reached
 * All active interim profiles are automatically deactivated at this time
 * Matches PAC_API InterimCutoffEventDto structure
 */
public class InterimCutoffEventDto {

    private String cutoffTime;           // Cutoff time that was reached (ISO 8601)
    private int profilesDeactivated;     // Number of profiles deactivated
    private List<String> vaultName;      // List of vault names that were deactivated
    private String timestamp;            // Event timestamp (ISO 8601)
    private int cutoffHour;              // Cutoff hour in 24-hour format (e.g., 20 for 8PM)
    private String message;              // Display message

    public InterimCutoffEventDto() {
    }

    // Getters and Setters

    public String getCutoffTime() {
        return cutoffTime;
    }

    public void setCutoffTime(String cutoffTime) {
        this.cutoffTime = cutoffTime;
    }

    public int getProfilesDeactivated() {
        return profilesDeactivated;
    }

    public void setProfilesDeactivated(int profilesDeactivated) {
        this.profilesDeactivated = profilesDeactivated;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getCutoffHour() {
        return cutoffHour;
    }

    public void setCutoffHour(int cutoffHour) {
        this.cutoffHour = cutoffHour;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getVaultName() {
        return vaultName;
    }

    public void setVaultName(List<String> vaultName) {
        this.vaultName = vaultName;
    }

    @Override
    public String toString() {
        return "InterimCutoffEvent{" +
                "cutoffHour=" + cutoffHour +
                ":00, profilesDeactivated=" + profilesDeactivated +
                ", vaultName=" + vaultName +
                ", timestamp='" + timestamp + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
