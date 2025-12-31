/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.request;

/**
 * Request DTO for creating manual override profile
 * Matches PAC_API CreateManualOverrideProfileRequestDto structure
 */
public class CreateManualOverrideProfileRequest {

    private String variableName;       // Required, max 100 chars (e.g., "MAIN.SOFT_LOCK_A")
    private String vaultName;          // Optional, human-readable vault name (e.g., "Main Vault", "Day Vault")
    private String custodian1;         // Required, max 100 chars
    private String custodian2;         // Required, max 100 chars
    private String custodian3;         // Required, max 100 chars
    private String startDateTime;      // Optional, ISO 8601 UTC format
    private String endDateTime;        // Optional, ISO 8601 UTC format
    private String requestedBy;        // Optional, max 100 chars

    public CreateManualOverrideProfileRequest() {
    }

    // Getters and Setters

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
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

    /**
     * Validate the request
     * @return Error message if invalid, null if valid
     */
    public String validate() {
        if (variableName == null || variableName.trim().isEmpty()) {
            return "Variable name is required";
        }
        if (variableName.length() > 100) {
            return "Variable name must not exceed 100 characters";
        }

        if (custodian1 == null || custodian1.trim().isEmpty()) {
            return "Custodian 1 is required";
        }
        if (custodian1.length() > 100) {
            return "Custodian 1 must not exceed 100 characters";
        }

        if (custodian2 == null || custodian2.trim().isEmpty()) {
            return "Custodian 2 is required";
        }
        if (custodian2.length() > 100) {
            return "Custodian 2 must not exceed 100 characters";
        }

        if (custodian3 == null || custodian3.trim().isEmpty()) {
            return "Custodian 3 is required";
        }
        if (custodian3.length() > 100) {
            return "Custodian 3 must not exceed 100 characters";
        }

        return null; // Valid
    }

    @Override
    public String toString() {
        return "CreateManualOverrideProfileRequest{" +
                "variableName='" + variableName + '\'' +
                ", vaultName='" + vaultName + '\'' +
                ", custodian1='" + custodian1 + '\'' +
                ", custodian2='" + custodian2 + '\'' +
                ", custodian3='" + custodian3 + '\'' +
                ", startDateTime='" + startDateTime + '\'' +
                ", endDateTime='" + endDateTime + '\'' +
                ", requestedBy='" + requestedBy + '\'' +
                '}';
    }
}
