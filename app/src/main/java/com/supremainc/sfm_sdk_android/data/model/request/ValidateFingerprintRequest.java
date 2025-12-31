/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.request;

/**
 * Request DTO for fingerprint validation
 * Matches PAC_API ValidateFingerprintDto structure
 *
 * Note: fingerPrintBase64 field now stores Arrays.toString() format (e.g., "[1, 2, 3, 4, ...]")
 * instead of Base64 encoding. This ensures byte-perfect preservation for cross-device compatibility.
 * The field name is kept for backward compatibility with the API.
 */
public class ValidateFingerprintRequest {

    private String fingerPrintBase64;  // Fingerprint template in Arrays.toString() format: "[1, 2, 3, ...]"

    public ValidateFingerprintRequest() {
    }

    public ValidateFingerprintRequest(String fingerPrintBase64) {
        this.fingerPrintBase64 = fingerPrintBase64;
    }

    // Getters and Setters

    public String getFingerPrintBase64() {
        return fingerPrintBase64;
    }

    public void setFingerPrintBase64(String fingerPrintBase64) {
        this.fingerPrintBase64 = fingerPrintBase64;
    }

    /**
     * Validate the request
     * @return Error message if invalid, null if valid
     */
    public String validate() {
        if (fingerPrintBase64 == null || fingerPrintBase64.trim().isEmpty()) {
            return "Fingerprint data is required";
        }
        return null; // Valid
    }

    @Override
    public String toString() {
        return "ValidateFingerprintRequest{" +
                "hasFingerprint=" + (fingerPrintBase64 != null && !fingerPrintBase64.isEmpty()) +
                '}';
    }
}
