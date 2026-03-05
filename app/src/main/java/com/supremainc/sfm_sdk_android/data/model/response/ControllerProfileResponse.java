/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * Response DTO for controller profile
 * Matches PAC_API ControllerProfile entity structure
 */
public class ControllerProfileResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("vaultId")
    private String vaultId;

    @SerializedName(value = "vaultName", alternate = {"doorName"})
    private String vaultName;

    @SerializedName("vaultCode")
    private String vaultCode;

    @SerializedName("controllerName")
    private String controllerName;

    @SerializedName("ipAddress")
    private String ipAddress;

    @SerializedName("controllerVariableId")
    private String controllerVariableId;

    @SerializedName("controllerVariableName")
    private String controllerVariableName;

    @SerializedName("softOverrideVariable")
    private String softOverrideVariable;

    @SerializedName("requiredCustodian")
    private int requiredCustodian;

    @SerializedName("isSentToAws")
    private boolean isSentToAws;

    @SerializedName("sentToAwsAt")
    private String sentToAwsAt;

    @SerializedName("sendAttemptCount")
    private int sendAttemptCount;

    @SerializedName("lastSendError")
    private String lastSendError;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    @SerializedName("vaultInfo")
    private VaultInfo vaultInfo;

    public ControllerProfileResponse() {
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getVaultCode() {
        return vaultCode;
    }

    public void setVaultCode(String vaultCode) {
        this.vaultCode = vaultCode;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getControllerVariableId() {
        return controllerVariableId;
    }

    public void setControllerVariableId(String controllerVariableId) {
        this.controllerVariableId = controllerVariableId;
    }

    public String getControllerVariableName() {
        return controllerVariableName;
    }

    public void setControllerVariableName(String controllerVariableName) {
        this.controllerVariableName = controllerVariableName;
    }

    public String getSoftOverrideVariable() {
        return softOverrideVariable;
    }

    public void setSoftOverrideVariable(String softOverrideVariable) {
        this.softOverrideVariable = softOverrideVariable;
    }

    public int getRequiredCustodian() {
        return requiredCustodian;
    }

    public void setRequiredCustodian(int requiredCustodian) {
        this.requiredCustodian = requiredCustodian;
    }

    public boolean isSentToAws() {
        return isSentToAws;
    }

    public void setSentToAws(boolean sentToAws) {
        isSentToAws = sentToAws;
    }

    public String getSentToAwsAt() {
        return sentToAwsAt;
    }

    public void setSentToAwsAt(String sentToAwsAt) {
        this.sentToAwsAt = sentToAwsAt;
    }

    public int getSendAttemptCount() {
        return sendAttemptCount;
    }

    public void setSendAttemptCount(int sendAttemptCount) {
        this.sendAttemptCount = sendAttemptCount;
    }

    public String getLastSendError() {
        return lastSendError;
    }

    public void setLastSendError(String lastSendError) {
        this.lastSendError = lastSendError;
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

    public VaultInfo getVaultInfo() {
        return vaultInfo;
    }

    public void setVaultInfo(VaultInfo vaultInfo) {
        this.vaultInfo = vaultInfo;
    }

    @Override
    public String toString() {
        return "ControllerProfileResponse{" +
                "id='" + id + '\'' +
                ", vaultName='" + vaultName + '\'' +
                ", vaultCode='" + vaultCode + '\'' +
                ", controllerName='" + controllerName + '\'' +
                ", controllerVariableName='" + controllerVariableName + '\'' +
                ", requiredCustodian=" + requiredCustodian +
                '}';
    }
}
