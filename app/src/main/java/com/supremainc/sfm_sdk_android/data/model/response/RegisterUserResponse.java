/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

/**
 * Response DTO for user registration
 * Matches PAC_API RegisterUserResponseDto structure
 */
public class RegisterUserResponse {

    private String username;
    private String name;
    private String employeeNumber;
    private String role;
    private String department;
    private String branch;
    private int fingerprintsRegistered;
    private String registeredAt;  // ISO 8601 DateTime string

    public RegisterUserResponse() {
    }

    // Getters and Setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public int getFingerprintsRegistered() {
        return fingerprintsRegistered;
    }

    public void setFingerprintsRegistered(int fingerprintsRegistered) {
        this.fingerprintsRegistered = fingerprintsRegistered;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public String toString() {
        return "RegisterUserResponse{" +
                "username='" + username + '\'' +
                ", name='" + name + '\'' +
                ", employeeNumber='" + employeeNumber + '\'' +
                ", role='" + role + '\'' +
                ", department='" + department + '\'' +
                ", branch='" + branch + '\'' +
                ", fingerprintsRegistered=" + fingerprintsRegistered +
                ", registeredAt='" + registeredAt + '\'' +
                '}';
    }
}
