/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.utils;

import android.util.Base64;

import java.util.Arrays;

/**
 * Utility class for fingerprint template conversion
 * Handles conversion between byte arrays and string representations
 * Based on reference implementation from PMOTamsNormal project
 */
public class FingerprintUtils {

    /**
     * Convert byte array to string representation using Arrays.toString()
     * Example: [1, 2, 3, 4] -> "[1, 2, 3, 4]"
     *
     * This format preserves exact byte values and is easily reversible
     * Suitable for TEXT/VARCHAR database storage and API transmission
     *
     * @param template Fingerprint template byte array
     * @return String representation of the byte array
     */
    public static String convertByteArrayToString(byte[] template) {
        if (template == null) {
            return null;
        }
        return Arrays.toString(template);
    }

    /**
     * Convert string representation back to byte array
     * Example: "[1, 2, 3, 4]" -> [1, 2, 3, 4]
     *
     * Process:
     * 1. Remove brackets: "[1, 2, 3, 4]" -> "1, 2, 3, 4"
     * 2. Remove spaces: "1, 2, 3, 4" -> "1,2,3,4"
     * 3. Split by comma: ["1", "2", "3", "4"]
     * 4. Parse to bytes: [1, 2, 3, 4]
     *
     * @param templateString String representation from Arrays.toString()
     * @return Byte array containing fingerprint template data
     */
    public static byte[] convertStringToByteArray(String templateString) {
        if (templateString == null || templateString.trim().isEmpty()) {
            return null;
        }

        try {
            // Remove brackets and spaces, then split by comma
            String cleaned = templateString
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "");

            if (cleaned.isEmpty()) {
                return new byte[0];
            }

            String[] parts = cleaned.split(",");
            byte[] result = new byte[parts.length];

            for (int i = 0; i < parts.length; i++) {
                result[i] = (byte) Integer.parseInt(parts[i]);
            }

            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fingerprint string format: " + templateString, e);
        }
    }

    /**
     * Convert Base64 string to byte array
     * Used for new FingerprintDownload endpoint that returns Base64 encoded templates
     * Example: "RRsOFZEAU0IoA5ClhC..." -> byte[384]
     *
     * @param base64String Base64 encoded fingerprint template
     * @return Byte array containing fingerprint template data
     */
    public static byte[] convertBase64ToByteArray(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            return null;
        }

        try {
            return Base64.decode(base64String, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 fingerprint format: " + base64String, e);
        }
    }

    /**
     * Convert byte array to Base64 string
     * Example: byte[384] -> "RRsOFZEAU0IoA5ClhC..."
     *
     * @param template Fingerprint template byte array
     * @return Base64 encoded string
     */
    public static String convertByteArrayToBase64(byte[] template) {
        if (template == null) {
            return null;
        }
        return Base64.encodeToString(template, Base64.DEFAULT);
    }

    /**
     * Validate if a fingerprint template byte array is valid
     *
     * @param template Fingerprint template to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidTemplate(byte[] template) {
        return template != null && template.length > 0 && template.length <= 3840;
    }

    /**
     * Validate if a fingerprint string representation is valid
     *
     * @param templateString String representation to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidTemplateString(String templateString) {
        if (templateString == null || templateString.trim().isEmpty()) {
            return false;
        }

        // Should start with '[' and end with ']'
        if (!templateString.startsWith("[") || !templateString.endsWith("]")) {
            return false;
        }

        // Try to convert and validate
        try {
            byte[] template = convertStringToByteArray(templateString);
            return isValidTemplate(template);
        } catch (Exception e) {
            return false;
        }
    }
}
