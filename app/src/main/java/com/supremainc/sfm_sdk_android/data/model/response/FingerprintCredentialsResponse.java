/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.supremainc.sfm_sdk_android.data.model.request.FingerCredential;

import java.util.List;

/**
 * Response containing user information with all associated fingerprint credentials
 * Matches PAC_API FingerprintCredentialsResponseDto structure
 */
public class FingerprintCredentialsResponse {

    private String employeeNumber;
    private String username;
    private String name;
    private String role;
    private String department;
    private String branch;
    private int totalFingerprints;
    private List<FingerCredentialWithId> fingerprints;

    public FingerprintCredentialsResponse() {
    }

    // Getters and Setters

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

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

    public int getTotalFingerprints() {
        return totalFingerprints;
    }

    public void setTotalFingerprints(int totalFingerprints) {
        this.totalFingerprints = totalFingerprints;
    }

    public List<FingerCredentialWithId> getFingerprints() {
        return fingerprints;
    }

    public void setFingerprints(List<FingerCredentialWithId> fingerprints) {
        this.fingerprints = fingerprints;
    }

    @Override
    public String toString() {
        return "FingerprintCredentialsResponse{" +
                "employeeNumber='" + employeeNumber + '\'' +
                ", username='" + username + '\'' +
                ", name='" + name + '\'' +
                ", role='" + role + '\'' +
                ", totalFingerprints=" + totalFingerprints +
                '}';
    }

    /**
     * Fingerprint credential with ID (ULID)
     */
    public static class FingerCredentialWithId extends FingerCredential {
        private String id;  // ULID string

        public FingerCredentialWithId() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "FingerCredentialWithId{" +
                    "id='" + id + '\'' +
                    ", " + super.toString() +
                    '}';
        }
    }
}
