/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * Response DTO for manual override profile list
 * Matches PAC_API ManualOverrideProfileListDto structure
 * Contains essential fields: Id, Custodian1-3, Status, ActivatedAt, DeactivatedAt
 */
public class ManualOverrideProfileListItem {

    @SerializedName("id")
    private String id;

    @SerializedName("custodian1")
    private String custodian1;

    @SerializedName("custodian2")
    private String custodian2;

    @SerializedName("custodian3")
    private String custodian3;

    @SerializedName("status")
    private String status;

    @SerializedName("activatedAt")
    private String activatedAt;

    /**
     * When the profile was deactivated
     * This is the UpdatedAt timestamp when Status = Deactivated
     */
    @SerializedName("deactivatedAt")
    private String deactivatedAt;

    @SerializedName("vaultName")
    private String vaultName;

    @SerializedName("variableName")
    private String variableName;  // PAC API variable name (e.g., "MAIN.SOFT_LOCK_A") to identify specific door

    @SerializedName("createdAt")
    private String createdAt;

    public ManualOverrideProfileListItem() {
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(String activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(String deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ManualOverrideProfileListItem{" +
                "id='" + id + '\'' +
                ", custodian1='" + custodian1 + '\'' +
                ", custodian2='" + custodian2 + '\'' +
                ", custodian3='" + custodian3 + '\'' +
                ", status='" + status + '\'' +
                ", activatedAt='" + activatedAt + '\'' +
                ", deactivatedAt='" + deactivatedAt + '\'' +
                ", vaultName='" + vaultName + '\'' +
                ", variableName='" + variableName + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
