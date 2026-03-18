/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Response containing all employees with their fingerprints
 * Matches PAC_API AllEmployeesFingerprintsResponseDto structure
 * Used by /api/FingerprintDownload/employee-fingerprints endpoint
 */
public class AllEmployeesFingerprintsResponse {

    @SerializedName("totalEmployees")
    private int totalEmployees;

    @SerializedName("totalFingerprints")
    private int totalFingerprints;

    @SerializedName("employees")
    private List<EmployeeFingerprintData> employees;

    @SerializedName("retrievedAt")
    private String retrievedAt;  // ISO 8601 datetime string

    public AllEmployeesFingerprintsResponse() {
        this.employees = new ArrayList<>();
    }

    // Getters and Setters

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(int totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public int getTotalFingerprints() {
        return totalFingerprints;
    }

    public void setTotalFingerprints(int totalFingerprints) {
        this.totalFingerprints = totalFingerprints;
    }

    public List<EmployeeFingerprintData> getEmployees() {
        return employees;
    }

    public void setEmployees(List<EmployeeFingerprintData> employees) {
        this.employees = employees;
    }

    public String getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(String retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    /**
     * Employee with fingerprint data
     */
    public static class EmployeeFingerprintData {

        @SerializedName("id")
        private int id;

        @SerializedName("icNumber")
        private String icNumber;

        @SerializedName("staffID")
        private String staffID;

        @SerializedName("fullName")
        private String fullName;

        @SerializedName("role")
        private String role;

        @SerializedName("department")
        private String department;

        @SerializedName("shortName")
        private String shortName;

        @SerializedName("cardNumber")
        private String cardNumber;

        @SerializedName("enrollmentDate")
        private String enrollmentDate;  // ISO 8601 datetime string

        @SerializedName("isActive")
        private boolean isActive;

        @SerializedName("isAllowedOverride")
        private boolean isAllowedOverride;

        @SerializedName("fingerprints")
        private List<FingerprintData> fingerprints;

        public EmployeeFingerprintData() {
            this.fingerprints = new ArrayList<>();
        }

        // Getters and Setters

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getIcNumber() {
            return icNumber;
        }

        public void setIcNumber(String icNumber) {
            this.icNumber = icNumber;
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

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getCardNumber() {
            return cardNumber;
        }

        public void setCardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
        }

        public String getEnrollmentDate() {
            return enrollmentDate;
        }

        public void setEnrollmentDate(String enrollmentDate) {
            this.enrollmentDate = enrollmentDate;
        }

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean active) {
            isActive = active;
        }

        public boolean isAllowedOverride() {
            return isAllowedOverride;
        }

        public void setAllowedOverride(boolean allowedOverride) {
            isAllowedOverride = allowedOverride;
        }

        public List<FingerprintData> getFingerprints() {
            return fingerprints;
        }

        public void setFingerprints(List<FingerprintData> fingerprints) {
            this.fingerprints = fingerprints;
        }
    }

    /**
     * Fingerprint data
     */
    public static class FingerprintData {

        @SerializedName("id")
        private int id;

        @SerializedName("employeeId")
        private int employeeId;

        @SerializedName("templateData")
        private String templateDataBase64;  // Base64 format (old, not used - kept for backwards compatibility)

        @SerializedName("templateDataByteArraysString")
        private String templateDataString;  // "[1, 2, 3, ...]" format from PAC_API - USE THIS!

        @SerializedName("leftRight")
        private int leftRight;  // 0 = Left, 1 = Right

        @SerializedName("fingerIndex")
        private int fingerIndex;  // 0 = Thumb, 1 = Index, 2 = Middle, 3 = Ring, 4 = Little

        @SerializedName("fingerType")
        private int fingerType;

        @SerializedName("quality")
        private int quality;  // Quality score (0-100)

        @SerializedName("enrollmentDate")
        private String enrollmentDate;  // ISO 8601 datetime string

        public FingerprintData() {
        }

        // Getters and Setters

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(int employeeId) {
            this.employeeId = employeeId;
        }

        public String getTemplateDataBase64() {
            return templateDataBase64;
        }

        public void setTemplateDataBase64(String templateDataBase64) {
            this.templateDataBase64 = templateDataBase64;
        }

        public String getTemplateDataString() {
            return templateDataString;
        }

        public void setTemplateDataString(String templateDataString) {
            this.templateDataString = templateDataString;
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

        public int getFingerType() {
            return fingerType;
        }

        public void setFingerType(int fingerType) {
            this.fingerType = fingerType;
        }

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }

        public String getEnrollmentDate() {
            return enrollmentDate;
        }

        public void setEnrollmentDate(String enrollmentDate) {
            this.enrollmentDate = enrollmentDate;
        }
    }

    @Override
    public String toString() {
        return "AllEmployeesFingerprintsResponse{" +
                "totalEmployees=" + totalEmployees +
                ", totalFingerprints=" + totalFingerprints +
                ", employees=" + (employees != null ? employees.size() : 0) +
                ", retrievedAt='" + retrievedAt + '\'' +
                '}';
    }
}
