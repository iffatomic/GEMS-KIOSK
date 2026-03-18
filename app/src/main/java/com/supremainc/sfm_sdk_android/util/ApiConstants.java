/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralized API constants for PAC_API communication
 * Configurable via SharedPreferences for future flexibility
 */
public class ApiConstants {

    // SharedPreferences keys
    private static final String PREFS_NAME = "PAC_API_CONFIG";
    private static final String KEY_BASE_URL = "PAC_API_BASE_URL";
    private static final String KEY_CONNECT_TIMEOUT = "connect_timeout_ms";
    private static final String KEY_READ_TIMEOUT = "read_timeout_ms";

    // Default values (these are only used if ConfigManager hasn't synced from JSON yet)
    private static final String DEFAULT_BASE_URL = "http://192.2.2.117:7000";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;    // 10 seconds

    // Endpoints - User Registration
    public static final String ENDPOINT_USER_REGISTRATION = "/api/UserRegistration/register";
    public static final String ENDPOINT_CHECK_USERNAME = "/api/UserRegistration/check-username/";
    public static final String ENDPOINT_CHECK_EMPLOYEE = "/api/UserRegistration/check-employee-number/";

    // Endpoints - Staff Enrollment
    public static final String ENDPOINT_STAFF_CHECK_USERNAME = "/api/StaffEnrollment/check-username/";
    public static final String ENDPOINT_STAFF_CHECK_EMPLOYEE = "/api/StaffEnrollment/check-employee-number/";
    public static final String ENDPOINT_STAFF_PENDING_ADMIN = "/api/StaffEnrollment/pending-admin";
    public static final String ENDPOINT_STAFF_PENDING_CUSTODIAN = "/api/StaffEnrollment/pending-custodian";
    public static final String ENDPOINT_STAFF_ENROLLED_ADMIN = "/api/StaffEnrollment/enrolled-admin";
    public static final String ENDPOINT_STAFF_ENROLLED_CUSTODIAN = "/api/StaffEnrollment/enrolled-custodian";
    public static final String ENDPOINT_STAFF_ENROLL = "/api/StaffEnrollment/enroll";
    public static final String ENDPOINT_STAFF_REGISTER_JIT = "/api/StaffEnrollment/register-jit";
    public static final String ENDPOINT_STAFF_VALIDATE_FINGERPRINT = "/api/StaffEnrollment/validate-fingerprint";
    public static final String ENDPOINT_STAFF_DELETE = "/api/StaffEnrollment/";
    public static final String ENDPOINT_STAFF_GET_FINGERPRINTS = "/api/StaffEnrollment/fingerprints/";
    public static final String ENDPOINT_STAFF_GET_ALL_FINGERPRINTS = "/api/StaffEnrollment/fingerprints";

    // Endpoints - Fingerprint Download (NEW - optimized endpoint for kiosk fingerprint sync)
    public static final String ENDPOINT_FINGERPRINT_DOWNLOAD = "/api/FingerprintDownload/employee-fingerprints";
    public static final String ENDPOINT_FINGERPRINT_EMPLOYEE = "/api/FingerprintDownload/employee-fingerprints/";

    // Endpoints - Manual Override
    public static final String ENDPOINT_MANUAL_OVERRIDE = "/api/ManualOverride/profiles";
    public static final String ENDPOINT_DEACTIVATE_OVERRIDE = "/api/ManualOverride/deactivate/";
    public static final String ENDPOINT_ALL_PROFILES = "/api/ManualOverride/all-profiles";
    public static final String ENDPOINT_VALID_PROFILE = "/api/ManualOverride/valid-profile";
    public static final String ENDPOINT_PROFILES_LIST = "/api/ManualOverride/profiles-list";
    public static final String ENDPOINT_MANUAL_OVERRIDE_CONFIG = "/api/ManualOverrideConfiguration";

    // Endpoints - Controller Profile (AWS Integration)
    public static final String ENDPOINT_CONTROLLER_PROFILES = "/api/twincat/aws/controller-profiles";

    // Endpoints - Vault Incident Log (Monitoring)
    public static final String ENDPOINT_VAULT_INCIDENT_LOGS = "/api/monitoring/VaultIncidentLog";
    public static final String ENDPOINT_VAULT_INCIDENT_LOG_BY_ID = "/api/monitoring/VaultIncidentLog/";
    public static final String ENDPOINT_VAULT_INCIDENT_RECENT = "/api/monitoring/VaultIncidentLog/recent";
    public static final String ENDPOINT_VAULT_INCIDENT_BY_SEVERITY = "/api/monitoring/VaultIncidentLog/severity/";
    public static final String ENDPOINT_VAULT_INCIDENT_BY_VAULT = "/api/monitoring/VaultIncidentLog/vault/";

    // HTTP Headers
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    public static final String HEADER_ACCEPT = "Accept";

    // HTTP Methods
    public static final String HTTP_GET = "GET";
    public static final String HTTP_POST = "POST";
    public static final String HTTP_PUT = "PUT";
    public static final String HTTP_DELETE = "DELETE";

    // Response Codes
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_INTERNAL_ERROR = 500;

    // Door/Variable Name Mapping
    public static final String VARIABLE_DOOR_A = "MAIN.SOFT_LOCK_A";
    public static final String VARIABLE_DOOR_B = "MAIN.SOFT_LOCK_B";
    public static final String VARIABLE_DOOR_C = "MAIN.SOFT_LOCK_C";
    public static final String VARIABLE_DOOR_D = "MAIN.SOFT_LOCK_D";
    public static final String VARIABLE_DOOR_E = "MAIN.SOFT_LOCK_E";
    public static final String VARIABLE_DOOR_F = "MAIN.SOFT_LOCK_F";
    public static final String VARIABLE_DOOR_G = "MAIN.SOFT_LOCK_G";

    // SignalR Event Names
    public static final String SIGNALR_KEYSWITCH_CHANGED = "KeySwitchChanged";
    public static final String SIGNALR_KEYSWITCH_AGGREGATE = "KeySwitchAggregate";
    public static final String SIGNALR_MANUAL_OVERRIDE = "ManualOverrideEvent";
    public static final String SIGNALR_DOOR_STATE_CHANGED = "DoorStateChanged";
    public static final String SIGNALR_VAULT_INCIDENT = "VaultIncidentOccurred";
    public static final String SIGNALR_FINGERPRINT_ENROLLMENT = "FingerprintEnrollmentCompleted";

    /**
     * Get PAC_API base URL from SharedPreferences or use default
     * @param context Android context
     * @return Base URL string
     */
    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    /**
     * Set PAC_API base URL in SharedPreferences
     * @param context Android context
     * @param baseUrl New base URL
     */
    public static void setBaseUrl(Context context, String baseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
    }

    /**
     * Get SignalR hub URL
     * @param context Android context
     * @return Hub URL string
     */
    public static String getSignalRHubUrl(Context context) {
        return getBaseUrl(context) + "/eventHub";
    }

    /**
     * Get connection timeout in milliseconds
     * @param context Android context
     * @return Timeout in milliseconds
     */
    public static int getConnectTimeout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    /**
     * Set connection timeout
     * @param context Android context
     * @param timeoutMs Timeout in milliseconds
     */
    public static void setConnectTimeout(Context context, int timeoutMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CONNECT_TIMEOUT, timeoutMs).apply();
    }

    /**
     * Get read timeout in milliseconds
     * @param context Android context
     * @return Timeout in milliseconds
     */
    public static int getReadTimeout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_READ_TIMEOUT, DEFAULT_READ_TIMEOUT_MS);
    }

    /**
     * Set read timeout
     * @param context Android context
     * @param timeoutMs Timeout in milliseconds
     */
    public static void setReadTimeout(Context context, int timeoutMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_READ_TIMEOUT, timeoutMs).apply();
    }

    /**
     * Reset all settings to default values
     * @param context Android context
     */
    public static void resetToDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear()
                .putString(KEY_BASE_URL, DEFAULT_BASE_URL)
                .putInt(KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS)
                .putInt(KEY_READ_TIMEOUT, DEFAULT_READ_TIMEOUT_MS)
                .apply();
    }

    // Prevent instantiation
    private ApiConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
