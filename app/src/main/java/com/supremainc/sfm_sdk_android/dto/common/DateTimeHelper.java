/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Helper class for DateTime operations
 * Handles ISO 8601 formatting for PAC_API communication
 */
public class DateTimeHelper {

    private final SimpleDateFormat iso8601Format;
    private final SimpleDateFormat iso8601FormatWithoutZ; // For timestamps without 'Z' suffix
    private final SimpleDateFormat displayFormat; // For displaying to user in local time

    public DateTimeHelper() {
        // ISO 8601 UTC format with 'Z': "yyyy-MM-dd'T'HH:mm:ss'Z'"
        this.iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));

        // ISO 8601 UTC format without 'Z': "yyyy-MM-dd'T'HH:mm:ss"
        this.iso8601FormatWithoutZ = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        this.iso8601FormatWithoutZ.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Display format in local timezone
        this.displayFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        this.displayFormat.setTimeZone(TimeZone.getDefault());
    }

    /**
     * Convert milliseconds to ISO 8601 UTC string
     * @param timestampMillis Timestamp in milliseconds since epoch
     * @return ISO 8601 formatted string (e.g., "2025-01-15T10:30:00Z")
     */
    public String toIso8601Utc(long timestampMillis) {
        return iso8601Format.format(new Date(timestampMillis));
    }

    /**
     * Convert Date to ISO 8601 UTC string
     * @param date Date object
     * @return ISO 8601 formatted string
     */
    public String toIso8601Utc(Date date) {
        if (date == null) {
            return null;
        }
        return iso8601Format.format(date);
    }

    /**
     * Parse ISO 8601 string to milliseconds
     * @param iso8601String ISO 8601 formatted string
     * @return Timestamp in milliseconds, or 0 if parsing fails
     */
    public long fromIso8601Utc(String iso8601String) {
        if (iso8601String == null || iso8601String.isEmpty()) {
            return 0;
        }

        try {
            Date date = iso8601Format.parse(iso8601String);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * Parse ISO 8601 string to Date object
     * @param iso8601String ISO 8601 formatted string
     * @return Date object, or null if parsing fails
     */
    public Date fromIso8601UtcToDate(String iso8601String) {
        if (iso8601String == null || iso8601String.isEmpty()) {
            return null;
        }

        try {
            return iso8601Format.parse(iso8601String);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Get current time as ISO 8601 UTC string
     * @return Current time in ISO 8601 format
     */
    public String nowAsIso8601Utc() {
        return iso8601Format.format(new Date());
    }

    /**
     * Check if a string is a valid ISO 8601 date
     * @param iso8601String String to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidIso8601(String iso8601String) {
        if (iso8601String == null || iso8601String.isEmpty()) {
            return false;
        }

        try {
            iso8601Format.parse(iso8601String);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
