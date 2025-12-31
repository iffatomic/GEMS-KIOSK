/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.supremainc.sfm_sdk_android.data.model.request.FingerCredential;

import java.util.List;

/**
 * Response containing all fingerprint credentials from all users
 * Matches PAC_API AllFingerprintsResponseDto structure
 */
public class AllFingerprintsResponse {

    private int totalFingerprints;
    private List<UserFingerprintData> users;

    public AllFingerprintsResponse() {
    }

    // Getters and Setters

    public int getTotalFingerprints() {
        return totalFingerprints;
    }

    public void setTotalFingerprints(int totalFingerprints) {
        this.totalFingerprints = totalFingerprints;
    }

    public List<UserFingerprintData> getUsers() {
        return users;
    }

    public void setUsers(List<UserFingerprintData> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return "AllFingerprintsResponse{" +
                "totalFingerprints=" + totalFingerprints +
                ", userCount=" + (users != null ? users.size() : 0) +
                '}';
    }

    /**
     * User information with their fingerprint credentials
     */
    public static class UserFingerprintData {
        private String employeeNumber;
        private String username;
        private String name;
        private String role;
        private String department;
        private String branch;
        private List<FingerCredentialWithId> fingerprints;

        public UserFingerprintData() {
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

        public List<FingerCredentialWithId> getFingerprints() {
            return fingerprints;
        }

        public void setFingerprints(List<FingerCredentialWithId> fingerprints) {
            this.fingerprints = fingerprints;
        }

        @Override
        public String toString() {
            return "UserFingerprintData{" +
                    "employeeNumber='" + employeeNumber + '\'' +
                    ", username='" + username + '\'' +
                    ", name='" + name + '\'' +
                    ", role='" + role + '\'' +
                    ", fingerprintCount=" + (fingerprints != null ? fingerprints.size() : 0) +
                    '}';
        }
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
