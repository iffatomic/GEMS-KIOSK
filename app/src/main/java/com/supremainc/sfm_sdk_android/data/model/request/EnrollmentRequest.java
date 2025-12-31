/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.request;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for staff enrollment
 * Matches PAC_API EnrollmentDto structure
 * Used for enrolling fingerprints for pre-registered users
 */
public class EnrollmentRequest {

    private String username;           // Required, max 50 chars
    private String name;               // Required, max 100 chars
    private String employeeNumber;     // Required, max 50 chars
    private String role;               // Optional, max 50 chars
    private String department;         // Optional, max 100 chars
    private String branch;             // Optional, max 100 chars
    private List<FingerCredential> fingerprints;  // Optional for enrollment

    public EnrollmentRequest() {
        this.fingerprints = new ArrayList<>();
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

    public List<FingerCredential> getFingerprints() {
        return fingerprints;
    }

    public void setFingerprints(List<FingerCredential> fingerprints) {
        this.fingerprints = fingerprints;
    }

    /**
     * Add a fingerprint credential to the list
     * @param fingerprint Fingerprint credential to add
     */
    public void addFingerprint(FingerCredential fingerprint) {
        if (this.fingerprints == null) {
            this.fingerprints = new ArrayList<>();
        }
        this.fingerprints.add(fingerprint);
    }

    /**
     * Validate the enrollment request
     * @return Error message if invalid, null if valid
     */
    public String validate() {
        if (username == null || username.trim().isEmpty()) {
            return "Username is required";
        }
        if (username.length() > 50) {
            return "Username must not exceed 50 characters";
        }

        if (name == null || name.trim().isEmpty()) {
            return "Name is required";
        }
        if (name.length() > 100) {
            return "Name must not exceed 100 characters";
        }

        if (employeeNumber == null || employeeNumber.trim().isEmpty()) {
            return "Employee number is required";
        }
        if (employeeNumber.length() > 50) {
            return "Employee number must not exceed 50 characters";
        }

        // Fingerprints are optional for enrollment endpoint
        if (fingerprints != null) {
            for (FingerCredential fp : fingerprints) {
                if (!fp.isValid()) {
                    return "Invalid fingerprint credential: " + fp.toString();
                }
            }
        }

        return null; // Valid
    }

    @Override
    public String toString() {
        return "EnrollmentRequest{" +
                "username='" + username + '\'' +
                ", name='" + name + '\'' +
                ", employeeNumber='" + employeeNumber + '\'' +
                ", role='" + role + '\'' +
                ", department='" + department + '\'' +
                ", branch='" + branch + '\'' +
                ", fingerprintsCount=" + (fingerprints != null ? fingerprints.size() : 0) +
                '}';
    }
}
