/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_ENROLL_OPTION;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.service.FingerprintSyncService;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SystemSettingsActivity extends AppCompatActivity {

    private static final String TAG = "SystemSettings";

    private ImageButton btnBack;
    private LinearLayout syncFingerprintsCard;
    private DatabaseHelper dbHelper;
    private SFM_SDK_ANDROID sdk;
    private ExecutorService executor;
    private Handler mainHandler;
    private FingerprintSyncService syncService;
    private FingerprintDownloadApiClient fingerprintDownloadApiClient;

    // Settings UI elements
    private android.widget.EditText editBaseUrl;
    private android.widget.EditText editSignalRPath;
    private android.widget.EditText editConnectTimeout;
    private android.widget.EditText editReadTimeout;
    private androidx.appcompat.widget.SwitchCompat switchForceSetup;
    private androidx.appcompat.widget.SwitchCompat switchDisableAdminVerification;
    private androidx.appcompat.widget.SwitchCompat switchResetDatabaseFlag;
    private android.widget.Button btnSaveSettings;
    private android.widget.Button btnSaveAndRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_settings);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Initialize SDK and threading
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeSDK();

        // Initialize sync service
        syncService = new FingerprintSyncService(this, sdk);

        // Initialize fingerprint download API client
        fingerprintDownloadApiClient = new FingerprintDownloadApiClient(this);

        // Initialize views
        initializeViews();

        // Set up click listeners
        setupClickListeners();

    }

    /**
     * Initialize fingerprint scanner SDK
     * Same approach as Interim project - close and reopen port
     */
    private void initializeSDK() {
        try {
            sdk = new SFM_SDK_ANDROID();
            Log.d(TAG, "SDK Version: " + sdk.UF_GetSDKVersion());
            sdk.UF_InitSysParameter();

            // Close existing connection
            try {
                sdk.UF_CloseCommPort();
                Thread.sleep(500);
            } catch (Exception e) {
                Log.d(TAG, "No existing connection to close");
            }

            // Open connection
            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);
            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Thread.sleep(300);
                sdk.UF_Reconnect();
                Log.d(TAG, "Scanner connected successfully");
            } else {
                Log.e(TAG, "Failed to connect scanner: " + ret);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.back_button);
        syncFingerprintsCard = findViewById(R.id.sync_fingerprints_card);

        // Settings input fields
        editBaseUrl = findViewById(R.id.edit_base_url);
        editSignalRPath = findViewById(R.id.edit_signalr_path);
        editConnectTimeout = findViewById(R.id.edit_connect_timeout);
        editReadTimeout = findViewById(R.id.edit_read_timeout);

        // Testing flags switches
        switchForceSetup = findViewById(R.id.switch_force_setup);
        switchDisableAdminVerification = findViewById(R.id.switch_disable_admin_verification);
        switchResetDatabaseFlag = findViewById(R.id.switch_reset_database_flag);

        // Save buttons
        btnSaveSettings = findViewById(R.id.btn_save_settings);
        btnSaveAndRestart = findViewById(R.id.btn_save_and_restart);

        // Load current config values into UI
        loadConfigValues();
    }

    private void setupClickListeners() {
        // Back button
        btnBack.setOnClickListener(v -> {
            onBackPressed();
        });

        // Save Settings button
        btnSaveSettings.setOnClickListener(v -> {
            saveConfigValues();
        });

        // Save & Restart button
        btnSaveAndRestart.setOnClickListener(v -> {
            saveConfigAndRestart();
        });

        // Sync Fingerprints card
        syncFingerprintsCard.setOnClickListener(v -> {
            showSyncFingerprintsConfirmation();
        });
    }

    /**
     * Load configuration values from config.json and populate UI
     */
    private void loadConfigValues() {
        try {
            com.supremainc.sfm_sdk_android.util.AppConfig config = com.supremainc.sfm_sdk_android.util.ConfigManager.getConfig();

            // Load server configuration
            editBaseUrl.setText(config.getBaseUrl());
            editSignalRPath.setText(config.getSignalRHubPath());
            editConnectTimeout.setText(String.valueOf(config.getConnectTimeoutMs()));
            editReadTimeout.setText(String.valueOf(config.getReadTimeoutMs()));

            // Load testing flags
            switchForceSetup.setChecked(config.getTestingFlags().isForceFirstTimeSetup());
            switchDisableAdminVerification.setChecked(config.getTestingFlags().isDisableAdminVerification());
            switchResetDatabaseFlag.setChecked(config.getTestingFlags().isResetDatabase());

            Log.d(TAG, "Config values loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading config values", e);
            Toast.makeText(this, "Error loading configuration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Save configuration values from UI to config.json
     */
    private void saveConfigValues() {
        try {
            // Get current config
            com.supremainc.sfm_sdk_android.util.AppConfig config = com.supremainc.sfm_sdk_android.util.ConfigManager.getConfig();

            // Update server configuration
            String baseUrl = editBaseUrl.getText().toString().trim();
            String signalRPath = editSignalRPath.getText().toString().trim();
            String connectTimeoutStr = editConnectTimeout.getText().toString().trim();
            String readTimeoutStr = editReadTimeout.getText().toString().trim();

            // Validate inputs
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "Base URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (signalRPath.isEmpty()) {
                Toast.makeText(this, "SignalR Hub Path cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            int connectTimeout = 10000; // default
            int readTimeout = 10000; // default

            try {
                if (!connectTimeoutStr.isEmpty()) {
                    connectTimeout = Integer.parseInt(connectTimeoutStr);
                }
                if (!readTimeoutStr.isEmpty()) {
                    readTimeout = Integer.parseInt(readTimeoutStr);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid timeout value. Using defaults.", Toast.LENGTH_SHORT).show();
            }

            // Update config object
            config.setBaseUrl(baseUrl);
            config.setSignalRHubPath(signalRPath);
            config.setConnectTimeoutMs(connectTimeout);
            config.setReadTimeoutMs(readTimeout);

            // Update testing flags
            config.getTestingFlags().setForceFirstTimeSetup(switchForceSetup.isChecked());
            config.getTestingFlags().setDisableAdminVerification(switchDisableAdminVerification.isChecked());
            config.getTestingFlags().setResetDatabase(switchResetDatabaseFlag.isChecked());

            // Save to file
            boolean saved = com.supremainc.sfm_sdk_android.util.ConfigManager.saveConfig();

            if (saved) {
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ CONFIGURATION SAVED");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Base URL: " + baseUrl);
                Log.d(TAG, "║ SignalR Path: " + signalRPath);
                Log.d(TAG, "║ Connect Timeout: " + connectTimeout + "ms");
                Log.d(TAG, "║ Read Timeout: " + readTimeout + "ms");
                Log.d(TAG, "║ Force First Time Setup: " + switchForceSetup.isChecked());
                Log.d(TAG, "║ Disable Admin Verification: " + switchDisableAdminVerification.isChecked());
                Log.d(TAG, "║ Reset Database Flag: " + switchResetDatabaseFlag.isChecked());
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                Toast.makeText(this, "✓ Settings saved successfully!\n\nRestart app for changes to take effect.", Toast.LENGTH_LONG).show();
            } else {
                Log.e(TAG, "Failed to save configuration to file");
                Toast.makeText(this, "Failed to save settings. Check logs for details.", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving config values", e);
            Toast.makeText(this, "Error saving configuration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Save configuration values and restart the app
     */
    private void saveConfigAndRestart() {
        try {
            // Get current config
            com.supremainc.sfm_sdk_android.util.AppConfig config = com.supremainc.sfm_sdk_android.util.ConfigManager.getConfig();

            // Update server configuration
            String baseUrl = editBaseUrl.getText().toString().trim();
            String signalRPath = editSignalRPath.getText().toString().trim();
            String connectTimeoutStr = editConnectTimeout.getText().toString().trim();
            String readTimeoutStr = editReadTimeout.getText().toString().trim();

            // Validate inputs
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "Base URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (signalRPath.isEmpty()) {
                Toast.makeText(this, "SignalR Hub Path cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            int connectTimeout = 10000; // default
            int readTimeout = 10000; // default

            try {
                if (!connectTimeoutStr.isEmpty()) {
                    connectTimeout = Integer.parseInt(connectTimeoutStr);
                }
                if (!readTimeoutStr.isEmpty()) {
                    readTimeout = Integer.parseInt(readTimeoutStr);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid timeout value. Using defaults.", Toast.LENGTH_SHORT).show();
            }

            // Update config object
            config.setBaseUrl(baseUrl);
            config.setSignalRHubPath(signalRPath);
            config.setConnectTimeoutMs(connectTimeout);
            config.setReadTimeoutMs(readTimeout);

            // Update testing flags
            config.getTestingFlags().setForceFirstTimeSetup(switchForceSetup.isChecked());
            config.getTestingFlags().setDisableAdminVerification(switchDisableAdminVerification.isChecked());
            config.getTestingFlags().setResetDatabase(switchResetDatabaseFlag.isChecked());

            // Save to file
            boolean saved = com.supremainc.sfm_sdk_android.util.ConfigManager.saveConfig();

            if (saved) {
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ CONFIGURATION SAVED - RESTARTING APP");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Base URL: " + baseUrl);
                Log.d(TAG, "║ SignalR Path: " + signalRPath);
                Log.d(TAG, "║ Connect Timeout: " + connectTimeout + "ms");
                Log.d(TAG, "║ Read Timeout: " + readTimeout + "ms");
                Log.d(TAG, "║ Force First Time Setup: " + switchForceSetup.isChecked());
                Log.d(TAG, "║ Disable Admin Verification: " + switchDisableAdminVerification.isChecked());
                Log.d(TAG, "║ Reset Database Flag: " + switchResetDatabaseFlag.isChecked());
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                Toast.makeText(this, "✓ Settings saved!\n\nRestarting app...", Toast.LENGTH_SHORT).show();

                // Restart app after short delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    restartApp();
                }, 1000);
            } else {
                Log.e(TAG, "Failed to save configuration to file");
                Toast.makeText(this, "Failed to save settings. Check logs for details.", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving config values", e);
            Toast.makeText(this, "Error saving configuration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Restart the application
     */
    private void restartApp() {
        try {
            android.content.Intent intent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());

            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);

                startActivity(intent);
                finish();

                // Kill the current process to ensure clean restart
                System.exit(0);
            } else {
                Log.e(TAG, "Failed to get launch intent");
                Toast.makeText(this, "Failed to restart app. Please restart manually.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting app", e);
            Toast.makeText(this, "Error restarting app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show confirmation dialog before syncing fingerprints
     */
    private void showSyncFingerprintsConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Reset & Sync Fingerprints from Server")
                .setMessage("This will RESET and sync fingerprints:\n\n" +
                        "⚠️ CLEAR all existing fingerprints from scanner\n" +
                        "⚠️ CLEAR all fingerprint data from database\n" +
                        "✓ Download ALL fingerprints from server\n" +
                        "✓ Save to local database\n" +
                        "✓ Enroll to scanner\n\n" +
                        "⚠️ This action cannot be undone!\n" +
                        "⏱ This may take 1-2 minutes\n\n" +
                        "Are you sure you want to continue?")
                .setPositiveButton("Reset & Sync", (dialog, which) -> {
                    performResetAndSync();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Reset scanner and database, then sync from server
     * Same approach as Interim project - clear everything first, then fresh sync
     */
    private void performResetAndSync() {
        Toast.makeText(this, "Resetting scanner and database...", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ RESET & SYNC INITIATED");
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        executor.execute(() -> {
            try {
                // STEP 1: Clear scanner memory
                Log.d(TAG, "Step 1: Clearing scanner memory...");
                sdk.UF_Reconnect();
                Thread.sleep(200);

                UF_RET_CODE clearRet = sdk.UF_DeleteAll();
                Log.d(TAG, "Scanner clear result: " + clearRet);

                if (clearRet == UF_RET_CODE.UF_RET_SUCCESS) {
                    Log.d(TAG, "✓ Scanner memory cleared successfully");
                } else {
                    Log.e(TAG, "⚠ Scanner clear failed: " + clearRet + " (continuing anyway)");
                }

                // STEP 2: Clear database synced_fingerprints table
                Log.d(TAG, "Step 2: Clearing database synced_fingerprints table...");
                int deletedCount = dbHelper.clearAllSyncedFingerprints();
                Log.d(TAG, "✓ Database cleared: " + deletedCount + " records deleted");

                mainHandler.post(() -> {
                    Toast.makeText(SystemSettingsActivity.this,
                        "Reset complete! Starting sync...",
                        Toast.LENGTH_SHORT).show();
                });

                // STEP 3: Now perform fresh sync
                Log.d(TAG, "Step 3: Starting fresh sync from server...");
                mainHandler.post(() -> {
                    performFingerprintSync();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error during reset", e);
                mainHandler.post(() -> {
                    Toast.makeText(SystemSettingsActivity.this,
                        "Reset failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Perform fingerprint sync from server using NEW endpoint
     * Uses /api/FingerprintDownload/employee-fingerprints (optimized endpoint)
     * Performs incremental sync - only adds NEW fingerprints, preserves existing data
     */
    private void performFingerprintSync() {
        Toast.makeText(this, "Starting fingerprint sync... Please wait", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ MANUAL FINGERPRINT SYNC INITIATED");
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // STEP 1: Download all fingerprints from NEW optimized endpoint
        Log.d(TAG, "Calling FingerprintDownload endpoint to get all employee fingerprints...");

        fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
                new ApiCallback<AllEmployeesFingerprintsResponse>() {
            @Override
            public void onSuccess(AllEmployeesFingerprintsResponse response) {
                Log.i(TAG, "✓ Successfully fetched fingerprint data from server");
                Log.d(TAG, "  → Total Employees: " + response.getTotalEmployees());
                Log.d(TAG, "  → Total Fingerprints: " + response.getTotalFingerprints());

                mainHandler.post(() -> {
                    Toast.makeText(SystemSettingsActivity.this,
                        "Downloaded " + response.getTotalFingerprints() + " fingerprints. Syncing to scanner...",
                        Toast.LENGTH_SHORT).show();
                });

                // STEP 2: Enroll fingerprints directly from downloaded data
                // (Don't use syncService - it calls the OLD interim endpoint)
                Log.d(TAG, "Enrolling fingerprints from downloaded data...");

                enrollFingerprintsFromResponse(response);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Failed to download fingerprints from server: " + error);
                mainHandler.post(() -> {
                    String message = "✗ Sync Failed\n\n" +
                                   "Error: " + error + "\n\n" +
                                   "Please check:\n" +
                                   "• Network connection\n" +
                                   "• Server is running\n" +
                                   "• Base URL in settings";
                    Toast.makeText(SystemSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Enroll fingerprints to scanner from downloaded response
     * Uses ONLY the NEW endpoint data - NO calls to old interim endpoints
     */
    private void enrollFingerprintsFromResponse(AllEmployeesFingerprintsResponse response) {
        executor.execute(() -> {
            int totalProcessed = 0;
            int successCount = 0;
            int failCount = 0;
            int skippedCount = 0;

            try {
                // Reconnect to scanner (same as Interim approach)
                Log.d(TAG, "Connecting to scanner...");
                sdk.UF_Reconnect();
                Log.d(TAG, "UF_Reconnect called");
                Thread.sleep(200);

                // Check scanner status
                int[] numTemplates = new int[1];
                UF_RET_CODE statusRet = sdk.UF_GetNumOfTemplate(numTemplates);
                Log.d(TAG, "UF_GetNumOfTemplate result: " + statusRet + ", count: " + numTemplates[0]);

                // Check if scanner is actually ready
                if (statusRet != UF_RET_CODE.UF_RET_SUCCESS) {
                    Log.e(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.e(TAG, "║ SCANNER NOT READY!");
                    Log.e(TAG, "║ Status: " + statusRet);
                    Log.e(TAG, "╚════════════════════════════════════════════════════════════");
                    mainHandler.post(() -> {
                        Toast.makeText(SystemSettingsActivity.this,
                            "Scanner not ready: " + statusRet,
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ ENROLLING FINGERPRINTS TO SCANNER");
                Log.d(TAG, "║ Scanner Status: " + statusRet + " (Ready!)");
                Log.d(TAG, "║ Existing Templates: " + numTemplates[0]);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                // Loop through all employees
                for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : response.getEmployees()) {

                    // Skip employees with no fingerprints
                    if (employee.getFingerprints() == null || employee.getFingerprints().isEmpty()) {
                        continue;
                    }

                    Log.d(TAG, "Processing: " + employee.getFullName() +
                               " (ID: " + employee.getId() + ", Staff: " + employee.getStaffID() + ") - " +
                               employee.getFingerprints().size() + " fingerprint(s)");

                    // Process each fingerprint for this employee
                    for (AllEmployeesFingerprintsResponse.FingerprintData fingerprint : employee.getFingerprints()) {
                        totalProcessed++;

                        try {
                            // Get raw byte array template (NO Base64 decode needed!)
                            byte[] templateBytes = fingerprint.getTemplateData();

                            if (templateBytes == null || templateBytes.length == 0) {
                                Log.e(TAG, "  ✗ Empty template data - skipping");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  → Raw template size: " + templateBytes.length + " bytes");

                            // STEP 1: Save fingerprint to SQLite database FIRST
                            // This is REQUIRED before enrolling to scanner
                            Log.d(TAG, "  → Saving to database...");

                            // Convert fingerType int to string
                            String fingerTypeStr = getFingerTypeName(fingerprint.getFingerIndex());

                            // Save raw byte[] directly to database (BLOB column)
                            int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                                String.valueOf(fingerprint.getId()),        // API ID
                                employee.getStaffID(),                      // Employee Number
                                employee.getStaffID(),                      // Username (use staffID)
                                employee.getFullName(),                     // Name
                                employee.getDepartment() != null ? employee.getDepartment() : "N/A", // Role
                                templateBytes,                              // Raw byte[] (stored as BLOB)
                                fingerprint.getLeftRight(),                 // 0=Left, 1=Right
                                fingerprint.getFingerIndex(),               // 0-4 (Thumb to Little)
                                fingerTypeStr                               // Finger type name
                            );

                            if (scannerId == -1) {
                                Log.e(TAG, "  ✗ Failed to save to database - skipping scanner enrollment");
                                failCount++;
                                continue;
                            }
                            Log.d(TAG, "  ✓ Saved to database (Scanner ID: " + scannerId + ")");

                            // STEP 2: Read template from database and convert back to byte[]
                            Log.d(TAG, "  → Reading template from database...");
                            String templateString = dbHelper.getTemplateDataByScannerId(scannerId);

                            if (templateString == null) {
                                Log.e(TAG, "  ✗ Failed to read template from database");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  → Converting string to byte array...");
                            byte[] templateBytesFromDb = DatabaseHelper.parseStringToByteArray(templateString);

                            if (templateBytesFromDb == null) {
                                Log.e(TAG, "  ✗ Failed to convert template string to byte array");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  ✓ Template converted from database (size: " + templateBytesFromDb.length + " bytes)");

                            // STEP 3: Now enroll to scanner - split if needed
                            int STANDARD_TEMPLATE_SIZE = 384;
                            int templateCount = templateBytesFromDb.length / STANDARD_TEMPLATE_SIZE;

                            if (templateBytesFromDb.length % STANDARD_TEMPLATE_SIZE != 0) {
                                Log.e(TAG, "  ✗ Invalid template size: " + templateBytesFromDb.length);
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  → Detected " + templateCount + " template(s) of " + STANDARD_TEMPLATE_SIZE + " bytes each");

                            // Process each 384-byte template separately
                            for (int t = 0; t < templateCount; t++) {
                                byte[] singleTemplate = new byte[STANDARD_TEMPLATE_SIZE];
                                System.arraycopy(templateBytesFromDb, t * STANDARD_TEMPLATE_SIZE, singleTemplate, 0, STANDARD_TEMPLATE_SIZE);

                                // Log first 16 bytes
                                StringBuilder hex = new StringBuilder();
                                for (int i = 0; i < Math.min(16, singleTemplate.length); i++) {
                                    hex.append(String.format("%02X ", singleTemplate[i]));
                                }
                                Log.d(TAG, "  → Template #" + (t+1) + " first 16 bytes: " + hex.toString());

                                // Enroll template to scanner using DATABASE scanner ID as USER ID
                                // This ensures when UF_Identify returns a scanner ID, it matches our database
                                int[] enrollId = new int[1];
                                long startTime = System.currentTimeMillis();

                                UF_RET_CODE enrollRet = sdk.UF_EnrollTemplate(
                                    scannerId,                                // USER ID = Database Scanner ID (10001, 10002, etc)
                                    UF_ENROLL_OPTION.UF_ENROLL_NONE,         // Use provided ID, don't auto-generate
                                    STANDARD_TEMPLATE_SIZE,                   // Always 384 bytes
                                    singleTemplate,                           // Single template
                                    enrollId                                   // Output: assigned enroll ID
                                );

                                long elapsed = System.currentTimeMillis() - startTime;
                                Log.d(TAG, "  → UF_EnrollTemplate #" + (t+1) + " completed in " + elapsed + "ms");
                                Log.d(TAG, "  → Result: " + enrollRet);

                                if (enrollRet == UF_RET_CODE.UF_RET_SUCCESS) {
                                    Log.d(TAG, "  ✓ Template #" + (t+1) + " enrolled successfully (Scanner ID: " + enrollId[0] + ")");
                                    successCount++;
                                } else {
                                    Log.e(TAG, "  ✗ Template #" + (t+1) + " enrollment failed: " + enrollRet);
                                    if (enrollRet == UF_RET_CODE.UF_ERR_EXIST_ID) {
                                        skippedCount++;
                                    } else {
                                        failCount++;
                                    }
                                }
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "  ✗ Error: " + e.getMessage());
                            failCount++;
                        }
                    }
                }

                // Fix provisional templates
                sdk.UF_FixProvisionalTemplate();

                // Save templates to persistent flash memory
                // WITHOUT THIS: Templates exist only in RAM and are lost when scanner disconnects
                Log.d(TAG, "════════════════════════════════════════════════════════════");
                Log.d(TAG, "Saving templates to persistent flash memory...");
                long saveStartTime = System.currentTimeMillis();

                UF_RET_CODE saveRet = sdk.UF_Save();

                long saveElapsed = System.currentTimeMillis() - saveStartTime;
                Log.d(TAG, "UF_Save() completed in " + saveElapsed + "ms");
                Log.d(TAG, "Result: " + saveRet);

                if (saveRet == UF_RET_CODE.UF_RET_SUCCESS) {
                    Log.d(TAG, "✓ Templates successfully persisted to flash memory");
                } else {
                    Log.e(TAG, "✗ Failed to save templates to flash: " + saveRet);
                    Log.e(TAG, "⚠ WARNING: Templates may be lost when scanner disconnects!");
                }

                // Final results
                final int finalSuccess = successCount;
                final int finalFail = failCount;
                final int finalSkipped = skippedCount;
                final int finalTotal = totalProcessed;

                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ FINGERPRINT SYNC COMPLETE");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Total Downloaded: " + response.getTotalFingerprints() + " fingerprints");
                Log.d(TAG, "║ Processed: " + finalTotal);
                Log.d(TAG, "║ Successfully Enrolled: " + finalSuccess);
                Log.d(TAG, "║ Skipped (already enrolled): " + finalSkipped);
                Log.d(TAG, "║ Failed: " + finalFail);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                mainHandler.post(() -> {
                    String message = "✓ Fingerprint Sync Complete!\n\n" +
                                   "✓ Downloaded: " + response.getTotalFingerprints() + " fingerprint(s)\n" +
                                   "✓ Enrolled to scanner: " + finalSuccess + "\n";

                    if (finalSkipped > 0) {
                        message += "⊘ Skipped (already enrolled): " + finalSkipped + "\n";
                    }

                    if (finalFail > 0) {
                        message += "⚠ Failed: " + finalFail + "\n";
                    }

                    message += "\n✓ All data synced from NEW endpoint!";
                    Toast.makeText(SystemSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "✗ Enrollment error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    String message = "⚠ Enrollment failed\n\n" +
                                   "Error: " + e.getMessage() + "\n\n" +
                                   "Please check scanner connection.";
                    Toast.makeText(SystemSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Add any custom back navigation logic here
        finish();
    }

    /**
     * Convert finger index to finger type name
     * @param fingerIndex 0-4 (Thumb, Index, Middle, Ring, Little)
     * @return Finger type name string
     */
    private String getFingerTypeName(int fingerIndex) {
        switch (fingerIndex) {
            case 0: return "Thumb";
            case 1: return "Index";
            case 2: return "Middle";
            case 3: return "Ring";
            case 4: return "Little";
            default: return "Unknown";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        // IMPORTANT: DO NOT close scanner port here!
        // Port lifecycle is managed by MainMenuActivity (the parent activity)
        // Closing it here causes UF_ERR_CANNOT_WRITE_SERIAL when returning to MainMenu
        if (sdk != null) {
            try {
                sdk.UF_Cancel(false);
                Log.d(TAG, "Scanner operations cancelled (port kept open for MainMenu)");
            } catch (Exception e) {
                Log.d(TAG, "No active scanner operation to cancel");
            }
        }

        // Close database
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    boolean Enroll(int id, int fingerCount, byte[] template, String employeeNumber)
    {
        final String TAG = "Enroll_2";
        UF_RET_CODE ret = null;


        int userID = id;
        int[] enrollID = new int[1];
        int[] imageQuality = new int[1];
        int[] numOfTemplate = new int[1];
        byte[] templateData = new byte[3840];
        int[] templateSize = new int[1];
        int[] _userID = new int[1];
        byte[] _subID = new byte[1];

        enrollID[0] = 0;
        imageQuality[0] = 0;
        templateSize[0] = template.length;
        templateData = template;

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ ENROLLING TO SCANNER");
        Log.d(TAG, "║ User ID (Scanner ID): " + userID);
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "║ Template Size: " + templateSize[0] + " bytes");
        Log.d(TAG, "║ Finger Count: " + fingerCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        ret = sdk.UF_EnrollTemplate(userID, UF_ENROLL_OPTION.UF_ENROLL_NONE, templateSize[0], templateData, enrollID);

        Log.d(TAG, "UF_EnrollTemplate Result: " + ret);
        Log.d(TAG, "Return Code String: " + (ret != null ? ret.toString() : "NULL"));
        Log.d(TAG, "Enroll ID returned: " + enrollID[0]);

        if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
            Log.d(TAG, "✓ Enroll SUCCESS - enrollID: " + enrollID[0] + ", User ID: " + userID + ", Employee: " + employeeNumber + ", Template: " + Arrays.toString(template));
            return true;
        } else {
            Log.e(TAG, "✗ Enroll FAILED");
            Log.e(TAG, "  Return Code: " + ret);
            Log.e(TAG, "  User ID: " + userID);
            Log.e(TAG, "  Template Size: " + templateSize[0] + " bytes");
            Log.e(TAG, "  Template Data: " + Arrays.toString(templateData));
            Log.e(TAG, "  Expected: 384 bytes (standard) or 768 bytes (concatenated)");

            if (ret == UF_RET_CODE.UF_ERR_TIME_OUT) {
                Log.e(TAG, "  Error Type: TIMEOUT");
            } else if (ret == UF_RET_CODE.UF_ERR_EXIST_ID) {
                Log.e(TAG, "  Error Type: ID ALREADY EXISTS");
            } else if (ret == UF_RET_CODE.UF_ERR_INVALID_PARAMETER) {
                Log.e(TAG, "  Error Type: INVALID PARAMETER");
            } else if (ret == UF_RET_CODE.UF_ERR_DATA_ERROR) {
                Log.e(TAG, "  Error Type: DATA ERROR");
            } else {
                Log.e(TAG, "  Error Type: " + (ret != null ? ret.toString() : "UNKNOWN"));
            }
        }

        return false;
    }
}