/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

/**
 * Response DTO for fingerprint sync
 * Used when downloading all fingerprints from API to enroll in local scanner
 */
public class FingerprintSyncItem {

    private int id;                    // Database ID
    private String username;           // Username
    private String employeeNumber;     // Employee number
    private String name;               // Full name
    private String fingerPrintBase64;  // Template in Arrays.toString() format
    private int leftRight;             // 0 = Left, 1 = Right
    private int fingerIndex;           // 1-10
    private String fingerType;         // "Thumb", "Index", etc.

    public FingerprintSyncItem() {
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFingerPrintBase64() {
        return fingerPrintBase64;
    }

    public void setFingerPrintBase64(String fingerPrintBase64) {
        this.fingerPrintBase64 = fingerPrintBase64;
    }

    public int getLeftRight() {
        return leftRight;
    }

    public void setLeftRight(int leftRight) {
        this.leftRight = leftRight;
    }

    public int getFingerIndex() {
        return fingerIndex;
    }

    public void setFingerIndex(int fingerIndex) {
        this.fingerIndex = fingerIndex;
    }

    public String getFingerType() {
        return fingerType;
    }

    public void setFingerType(String fingerType) {
        this.fingerType = fingerType;
    }

    @Override
    public String toString() {
        return "FingerprintSyncItem{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", employeeNumber='" + employeeNumber + '\'' +
                ", name='" + name + '\'' +
                ", leftRight=" + leftRight +
                ", fingerIndex=" + fingerIndex +
                ", fingerType='" + fingerType + '\'' +
                '}';
    }
}
