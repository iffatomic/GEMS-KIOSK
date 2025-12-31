/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.request;

/**
 * Fingerprint credential DTO
 * Matches PAC_API FingerCredentialDto structure
 *
 * Note: fingerPrintBase64 field now stores Arrays.toString() format (e.g., "[1, 2, 3, 4, ...]")
 * instead of Base64 encoding. This ensures byte-perfect preservation for cross-device compatibility.
 * The field name is kept for backward compatibility with the API.
 */
public class FingerCredential {

    private String fingerPrintBase64;  // Fingerprint template in Arrays.toString() format: "[1, 2, 3, ...]"
    private int leftRight;             // 0 = Left, 1 = Right
    private int fingerIndex;           // 1-10
    private String fingerType;         // "Thumb", "Index", "Middle", "Ring", "Pinky"

    public FingerCredential() {
    }

    public FingerCredential(String fingerPrintBase64, int leftRight, int fingerIndex, String fingerType) {
        this.fingerPrintBase64 = fingerPrintBase64;
        this.leftRight = leftRight;
        this.fingerIndex = fingerIndex;
        this.fingerType = fingerType;
    }

    // Getters and Setters

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

    /**
     * Validate fingerprint credential data
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return fingerPrintBase64 != null && !fingerPrintBase64.isEmpty()
                && (leftRight == 0 || leftRight == 1)
                && (fingerIndex >= 1 && fingerIndex <= 10)
                && fingerType != null && !fingerType.isEmpty();
    }

    @Override
    public String toString() {
        return "FingerCredential{" +
                "leftRight=" + (leftRight == 0 ? "Left" : "Right") +
                ", fingerIndex=" + fingerIndex +
                ", fingerType='" + fingerType + '\'' +
                ", hasTemplate=" + (fingerPrintBase64 != null && !fingerPrintBase64.isEmpty()) +
                '}';
    }
}
