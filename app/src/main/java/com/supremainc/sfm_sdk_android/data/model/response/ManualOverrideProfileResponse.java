/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * Response DTO for manual override profile operations
 * Matches PAC_API ManualOverrideProfileResponseDto structure
 */
public class ManualOverrideProfileResponse {

    @SerializedName("id")
    private String profileId;          // UUID string (maps to "id" in PAC API)

    @SerializedName("variableName")
    private String variableName;

    @SerializedName("custodian1")
    private String custodian1;

    @SerializedName("custodian2")
    private String custodian2;

    @SerializedName("custodian3")
    private String custodian3;

    @SerializedName("isManualOverride")
    private boolean isManualOverride;

    @SerializedName("startDateTime")
    private String startDateTime;

    @SerializedName("endDateTime")
    private String endDateTime;

    @SerializedName("requestedBy")
    private String requestedBy;

    @SerializedName("status")
    private String status;             // "Pending", "Active", "Completed", "Deactivated", "Expired"

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    @SerializedName("activatedAt")
    private String activatedAt;

    @SerializedName("completedAt")
    private String completedAt;

    @SerializedName("deactivatedAt")
    private String deactivatedAt;

    // For backward compatibility - doorName is not in PAC API response
    private String doorName;

    public ManualOverrideProfileResponse() {
    }

    // Getters and Setters

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getDoorName() {
        return doorName;
    }

    public void setDoorName(String doorName) {
        this.doorName = doorName;
    }

    public String getCustodian1() {
        return custodian1;
    }

    public void setCustodian1(String custodian1) {
        this.custodian1 = custodian1;
    }

    public String getCustodian2() {
        return custodian2;
    }

    public void setCustodian2(String custodian2) {
        this.custodian2 = custodian2;
    }

    public String getCustodian3() {
        return custodian3;
    }

    public void setCustodian3(String custodian3) {
        this.custodian3 = custodian3;
    }

    public String getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(String startDateTime) {
        this.startDateTime = startDateTime;
    }

    public String getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(String endDateTime) {
        this.endDateTime = endDateTime;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public boolean isManualOverride() {
        return isManualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        isManualOverride = manualOverride;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(String activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(String deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    @Override
    public String toString() {
        return "ManualOverrideProfileResponse{" +
                "profileId='" + profileId + '\'' +
                ", variableName='" + variableName + '\'' +
                ", custodian1='" + custodian1 + '\'' +
                ", custodian2='" + custodian2 + '\'' +
                ", custodian3='" + custodian3 + '\'' +
                ", status='" + status + '\'' +
                ", isManualOverride=" + isManualOverride +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
