/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

/**
 * DTO for fingerprint enrollment completion events
 * Received when a new employee fingerprint is enrolled to BioStar devices
 * Triggers kiosks to refresh fingerprint data from API
 * Matches PAC_API FingerprintEnrollmentEventDto structure
 */
public class FingerprintEnrollmentEventDto {

    private int employeeId;              // Employee ID who was enrolled
    private String staffID;              // Employee Staff ID
    private String fullName;             // Employee Full Name
    private int fingerprintCount;        // Number of fingerprints enrolled for this employee
    private String enrollmentCompletedAt;// Enrollment completion timestamp (ISO 8601)
    private String message;              // Display message

    public FingerprintEnrollmentEventDto() {
    }

    // Getters and Setters

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public String getStaffID() {
        return staffID;
    }

    public void setStaffID(String staffID) {
        this.staffID = staffID;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getFingerprintCount() {
        return fingerprintCount;
    }

    public void setFingerprintCount(int fingerprintCount) {
        this.fingerprintCount = fingerprintCount;
    }

    public String getEnrollmentCompletedAt() {
        return enrollmentCompletedAt;
    }

    public void setEnrollmentCompletedAt(String enrollmentCompletedAt) {
        this.enrollmentCompletedAt = enrollmentCompletedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "FingerprintEnrollmentEvent{" +
                "employeeId=" + employeeId +
                ", staffID='" + staffID + '\'' +
                ", fullName='" + fullName + '\'' +
                ", fingerprintCount=" + fingerprintCount +
                ", enrollmentCompletedAt='" + enrollmentCompletedAt + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
