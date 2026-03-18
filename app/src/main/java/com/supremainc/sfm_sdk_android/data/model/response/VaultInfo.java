/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * Nested vault information within ControllerProfileResponse
 * Contains additional details about the vault/door
 */
public class VaultInfo {

    @SerializedName("vaultName")
    private String vaultName;

    @SerializedName("vaultCode")
    private String vaultCode;

    @SerializedName("vaultCategory")
    private String vaultCategory;

    @SerializedName("vaultType")
    private String vaultType;

    @SerializedName("authenticationMode")
    private String authenticationMode;

    public VaultInfo() {
    }

    // Getters and Setters

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

    public String getVaultCategory() {
        return vaultCategory;
    }

    public void setVaultCategory(String vaultCategory) {
        this.vaultCategory = vaultCategory;
    }

    public String getVaultType() {
        return vaultType;
    }

    public void setVaultType(String vaultType) {
        this.vaultType = vaultType;
    }

    public String getAuthenticationMode() {
        return authenticationMode;
    }

    public void setAuthenticationMode(String authenticationMode) {
        this.authenticationMode = authenticationMode;
    }

    @Override
    public String toString() {
        return "VaultInfo{" +
                "vaultName='" + vaultName + '\'' +
                ", vaultCode='" + vaultCode + '\'' +
                ", vaultCategory='" + vaultCategory + '\'' +
                ", vaultType='" + vaultType + '\'' +
                ", authenticationMode='" + authenticationMode + '\'' +
                '}';
    }
}
