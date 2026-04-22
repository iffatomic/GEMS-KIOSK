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

import com.supremainc.sfm_sdk_android.util.AppUpdateManager;

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
    private android.widget.SeekBar seekbarInactivityTimeout;
    private android.widget.TextView textInactivityTimeoutValue;
    private android.widget.Button btnSaveSettings;
    private android.widget.Button btnSaveAndRestart;
    private android.widget.EditText editUpdateCheckInterval;
    private LinearLayout checkUpdatesCard;
    private android.widget.TextView tvAppVersion;

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

        // Save buttons
        btnSaveSettings = findViewById(R.id.btn_save_settings);
        btnSaveAndRestart = findViewById(R.id.btn_save_and_restart);

        // Update check interval
        editUpdateCheckInterval = findViewById(R.id.edit_update_check_interval);
        checkUpdatesCard = findViewById(R.id.check_updates_card);

        // Inactivity timeout
        seekbarInactivityTimeout = findViewById(R.id.seekbar_inactivity_timeout);
        textInactivityTimeoutValue = findViewById(R.id.text_inactivity_timeout_value);

        // App version
        tvAppVersion = findViewById(R.id.tv_app_version);
        if (tvAppVersion != null) {
            try {
                android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                tvAppVersion.setText("GEMS Kiosk v" + info.versionName + " (" + info.versionCode + ")");
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                tvAppVersion.setText("GEMS Kiosk");
            }
        }
        if (seekbarInactivityTimeout != null) {
            seekbarInactivityTimeout.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    int minutes = progress + 3;
                    if (textInactivityTimeoutValue != null) {
                        textInactivityTimeoutValue.setText(minutes + " minutes");
                    }
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            });
        }

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

        // Check for Updates Now card
        if (checkUpdatesCard != null) {
            checkUpdatesCard.setOnClickListener(v -> {
                Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
                executor.execute(() -> AppUpdateManager.checkForUpdate(this,
                    new AppUpdateManager.UpdateCheckCallback() {
                        @Override
                        public void onUpdateAvailable(AppUpdateManager.VersionInfo versionInfo, java.io.File apkFile) {
                            runOnUiThread(() -> {
                                if (!isFinishing()) {
                                    AppUpdateManager.showUpdateDialog(SystemSettingsActivity.this, versionInfo, apkFile, null);
                                }
                            });
                        }
                        @Override
                        public void onNoUpdateNeeded() {
                            runOnUiThread(() -> Toast.makeText(SystemSettingsActivity.this,
                                    "App is up to date.", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onCheckFailed(String error) {
                            runOnUiThread(() -> Toast.makeText(SystemSettingsActivity.this,
                                    "Update check failed: " + error, Toast.LENGTH_LONG).show());
                        }
                    }));
            });
        }
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

            // Load inactivity timeout (3-5 minutes, seekbar 0-2)
            int timeoutMinutes = Math.max(3, Math.min(5, config.getInactivityTimeoutMinutes()));
            if (seekbarInactivityTimeout != null) seekbarInactivityTimeout.setProgress(timeoutMinutes - 3);
            if (textInactivityTimeoutValue != null) textInactivityTimeoutValue.setText(timeoutMinutes + " minutes");

            // Load update check interval
            if (editUpdateCheckInterval != null) {
                editUpdateCheckInterval.setText(String.valueOf(config.getUpdateCheckIntervalMinutes()));
            }

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

            // Update inactivity timeout
            if (seekbarInactivityTimeout != null) {
                config.setInactivityTimeoutMinutes(seekbarInactivityTimeout.getProgress() + 3);
            }

            // Update update check interval
            if (editUpdateCheckInterval != null) {
                String intervalStr = editUpdateCheckInterval.getText().toString().trim();
                if (!intervalStr.isEmpty()) {
                    try {
                        config.setUpdateCheckIntervalMinutes(Integer.parseInt(intervalStr));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid update interval, keeping default");
                    }
                }
            }

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
                Log.d(TAG, "║ Inactivity Timeout: " + config.getInactivityTimeoutMinutes() + " min");
                Log.d(TAG, "║ Update Check Interval: " + config.getUpdateCheckIntervalMinutes() + " min");
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

            // Update inactivity timeout
            if (seekbarInactivityTimeout != null) {
                config.setInactivityTimeoutMinutes(seekbarInactivityTimeout.getProgress() + 3);
            }

            // Update update check interval
            if (editUpdateCheckInterval != null) {
                String intervalStr = editUpdateCheckInterval.getText().toString().trim();
                if (!intervalStr.isEmpty()) {
                    try {
                        config.setUpdateCheckIntervalMinutes(Integer.parseInt(intervalStr));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid update interval, keeping default");
                    }
                }
            }

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
                            // ═══════════════════════════════════════════════════════════
                            // DIAGNOSTIC LOGGING: Compare PAC_API vs Hardcoded Working Value
                            // ═══════════════════════════════════════════════════════════

                            String pacApiString = fingerprint.getTemplateDataString();

                            Log.d(TAG, "╔══════════════════════════════════════════════════════════════");
                            Log.d(TAG, "║ DIAGNOSTIC: PAC_API DATA ANALYSIS");
                            Log.d(TAG, "╠══════════════════════════════════════════════════════════════");
                            Log.d(TAG, "║ Employee: " + employee.getFullName() + " (" + employee.getStaffID() + ")");
                            Log.d(TAG, "║ Fingerprint ID: " + fingerprint.getId());
                            Log.d(TAG, "╠══════════════════════════════════════════════════════════════");

                            if (pacApiString != null && !pacApiString.isEmpty()) {
                                Log.d(TAG, "║ PAC_API String Length: " + pacApiString.length() + " chars");
                                Log.d(TAG, "║ First 150 chars from PAC_API:");
                                Log.d(TAG, "║   " + pacApiString.substring(0, Math.min(150, pacApiString.length())));

                                // Parse first 20 bytes from PAC_API
                                try {
                                    String cleaned = pacApiString.replace("[", "").replace("]", "");
                                    String[] parts = cleaned.split(",");
                                    Log.d(TAG, "║ PAC_API Total byte count: " + parts.length);

                                    StringBuilder first20 = new StringBuilder("║ PAC_API First 20 bytes: [");
                                    for (int i = 0; i < Math.min(20, parts.length); i++) {
                                        first20.append(parts[i].trim());
                                        if (i < Math.min(20, parts.length) - 1) first20.append(", ");
                                    }
                                    first20.append("]");
                                    Log.d(TAG, first20.toString());

                                    // Show bytes 6-7 (should be 'U','F' = 85,70 for Suprema)
                                    if (parts.length >= 8) {
                                        int byte6 = Integer.parseInt(parts[6].trim());
                                        int byte7 = Integer.parseInt(parts[7].trim());
                                        Log.d(TAG, "║ PAC_API Bytes[6-7] (Suprema signature): " + byte6 + ", " + byte7 +
                                                   " (expected: 85, 70 for 'UF')");
                                    }

                                } catch (Exception e) {
                                    Log.e(TAG, "║ ERROR parsing PAC_API data: " + e.getMessage());
                                }
                            } else {
                                Log.e(TAG, "║ PAC_API returned NULL or EMPTY string!");
                            }

                            Log.d(TAG, "╠══════════════════════════════════════════════════════════════");
                            Log.d(TAG, "║ HARDCODED (Working) First 20 bytes: [69, 26, 16, 20, 158, 0, 85, 70, 59, 65, 240, 152, 14, 48, 2, 1, 151, 10, 54, 194]");
                            Log.d(TAG, "║ HARDCODED Bytes[6-7]: 85, 70 ('UF' - Suprema signature) ✓");
                            Log.d(TAG, "╚══════════════════════════════════════════════════════════════");

                            // Get string template (Arrays.toString format from PAC_API)
                            String templateString = fingerprint.getTemplateDataString();

                            //testing hardcoded fingerprint templateString (left thumb)

                            //Suprema template format
                            //String templateString = "[69, 26, 16, 20, 158, 0, 85, 70, 59, 65, 240, 152, 14, 48, 2, 1, 151, 10, 54, 194, 17, 69, 137, 59, 66, 176, 72, 138, 61, 67, 96, 165, 136, 30, 3, 225, 157, 133, 53, 195, 240, 160, 9, 53, 196, 176, 165, 12, 44, 6, 49, 163, 9, 32, 135, 128, 163, 137, 56, 135, 209, 174, 134, 19, 200, 1, 161, 6, 41, 8, 96, 80, 133, 27, 201, 33, 76, 134, 15, 74, 33, 162, 129, 56, 11, 96, 1, 133, 15, 203, 225, 162, 8, 56, 12, 209, 179, 132, 53, 142, 177, 4, 134, 25, 15, 177, 169, 134, 13, 15, 192, 76, 140, 46, 15, 209, 3, 8, 36, 143, 240, 0, 9, 24, 81, 208, 175, 135, 16, 82, 208, 169, 10, 28, 211, 145, 177, 131, 255, 255, 255, 255, 68, 85, 95, 255, 255, 255, 255, 244, 68, 69, 85, 255, 255, 255, 255, 85, 68, 68, 85, 79, 255, 255, 244, 68, 68, 68, 68, 79, 255, 255, 68, 68, 51, 51, 51, 51, 255, 255, 67, 51, 51, 34, 33, 15, 255, 255, 51, 51, 50, 34, 17, 16, 255, 255, 51, 50, 34, 33, 17, 14, 255, 255, 51, 50, 34, 33, 16, 0, 255, 255, 51, 34, 34, 33, 0, 0, 255, 255, 51, 34, 34, 17, 0, 0, 255, 243, 34, 34, 33, 17, 0, 0, 255, 243, 34, 34, 33, 17, 0, 14, 255, 242, 34, 34, 17, 16, 14, 238, 255, 242, 34, 17, 17, 16, 238, 238, 255, 242, 17, 17, 0, 0, 238, 238, 255, 243, 17, 16, 0, 238, 238, 238, 255, 243, 17, 0, 14, 238, 238, 238, 255, 243, 16, 0, 14, 238, 238, 223, 255, 243, 16, 0, 14, 238, 237, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]";

                            //Suprema template format  (Ghani)
                            //String templateString = "[68, 31, 18, 25, 168, 0, 83, 66, 53, 36, 16, 9, 89, 38, 114, 6, 99, 42, 242, 8, 34, 47, 155, 3, 122, 49, 227, 131, 77, 53, 120, 6, 65, 55, 15, 139, 65, 59, 127, 14, 44, 64, 151, 8, 98, 71, 236, 9, 116, 71, 228, 128, 134, 74, 225, 135, 115, 77, 97, 130, 93, 78, 104, 9, 26, 84, 45, 133, 91, 89, 237, 138, 58, 99, 45, 141, 61, 104, 158, 145, 97, 104, 90, 135, 78, 106, 10, 159, 67, 111, 147, 154, 84, 113, 253, 34, 121, 125, 86, 10, 73, 131, 51, 23, 77, 131, 68, 174, 50, 133, 181, 130, 55, 133, 58, 132, 76, 137, 67, 144, 71, 139, 183, 136, 39, 141, 55, 135, 95, 160, 74, 133, 255, 255, 255, 254, 0, 17, 255, 255, 255, 255, 255, 253, 238, 0, 17, 35, 255, 255, 255, 255, 205, 238, 0, 17, 35, 63, 255, 255, 252, 205, 222, 0, 17, 35, 52, 255, 255, 252, 205, 238, 0, 17, 35, 52, 79, 255, 188, 204, 222, 0, 17, 35, 52, 79, 248, 187, 204, 222, 0, 18, 35, 52, 68, 136, 171, 188, 222, 1, 18, 35, 52, 68, 136, 171, 188, 205, 224, 18, 51, 51, 68, 120, 171, 187, 205, 224, 18, 51, 51, 68, 136, 170, 187, 188, 224, 18, 51, 68, 68, 137, 170, 170, 188, 208, 35, 52, 68, 68, 136, 153, 170, 171, 208, 35, 68, 68, 68, 153, 153, 153, 154, 192, 35, 69, 85, 68, 153, 153, 153, 153, 190, 52, 85, 85, 68, 153, 153, 153, 153, 170, 69, 102, 101, 84, 153, 153, 153, 136, 152, 102, 102, 101, 84, 169, 153, 152, 136, 135, 102, 102, 101, 85, 186, 136, 136, 135, 119, 118, 102, 101, 85, 187, 136, 136, 135, 119, 118, 102, 101, 85, 169, 136, 136, 119, 119, 118, 102, 101, 85, 170, 151, 119, 119, 119, 118, 102, 101, 95, 255, 255, 247, 119, 119, 102, 102, 85, 255, 255, 255, 255, 247, 118, 102, 101, 255, 255, 255, 255, 255, 255, 246, 102, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]";

                            //iso19794_2 template format
                            //String templateString = "[70, 77, 82, 0, 32, 50, 48, 0, 0, 0, 1, 37, 0, 0, 1, 44, 1, 144, 0, 197, 0, 197, 1, 0, 0, 0, 100, 43, 128, 193, 0, 55, 13, 0, 128, 163, 0, 77, 12, 0, 128, 153, 0, 111, 9, 0, 64, 110, 0, 120, 19, 0, 64, 143, 0, 136, 10, 0, 64, 30, 0, 154, 26, 0, 129, 13, 0, 159, 111, 0, 128, 36, 0, 172, 23, 0, 64, 215, 0, 176, 116, 0, 64, 38, 0, 179, 156, 0, 64, 246, 0, 188, 234, 0, 128, 53, 0, 196, 25, 0, 64, 146, 0, 197, 0, 0, 128, 192, 0, 214, 240, 0, 64, 111, 0, 225, 10, 0, 64, 150, 0, 240, 239, 0, 128, 53, 0, 245, 30, 0, 129, 1, 0, 250, 229, 0, 64, 149, 1, 3, 228, 0, 128, 112, 1, 7, 4, 0, 128, 115, 1, 17, 249, 0, 64, 153, 1, 29, 105, 0, 128, 129, 1, 30, 227, 0, 128, 18, 1, 31, 31, 0, 64, 37, 1, 34, 164, 0, 64, 177, 1, 34, 218, 0, 65, 9, 1, 38, 215, 0, 128, 102, 1, 44, 9, 0, 128, 117, 1, 44, 222, 0, 64, 106, 1, 50, 166, 0, 64, 109, 1, 54, 173, 0, 128, 206, 1, 59, 215, 0, 64, 122, 1, 61, 194, 0, 64, 248, 1, 64, 215, 0, 64, 167, 1, 71, 202, 0, 65, 19, 1, 84, 85, 0, 128, 206, 1, 85, 204, 0, 64, 59, 1, 90, 169, 0, 128, 148, 1, 92, 184, 0, 64, 72, 1, 94, 169, 0, 64, 26, 1, 98, 36, 0, 64, 187, 1, 99, 192, 0, 128, 89, 1, 117, 174, 0, 0, 5, 1, 68, 0, 5, 163, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]";

                            //ansi378 template format
                            //String templateString = "[70, 77, 82, 0, 32, 50, 48, 0, 0, 247, 0, 68, 1, 0, 0, 0, 1, 44, 1, 144, 0, 197, 0, 197, 1, 0, 0, 0, 60, 35, 128, 110, 0, 64, 12, 0, 64, 225, 0, 74, 179, 0, 128, 190, 0, 78, 5, 0, 129, 24, 0, 81, 172, 0, 64, 161, 0, 93, 7, 0, 128, 148, 0, 128, 4, 0, 64, 103, 0, 134, 11, 0, 64, 140, 0, 150, 3, 0, 129, 8, 0, 191, 73, 0, 64, 211, 0, 196, 79, 0, 128, 40, 0, 205, 14, 0, 128, 140, 0, 206, 0, 0, 64, 240, 0, 213, 160, 0, 128, 189, 0, 229, 164, 0, 64, 107, 0, 232, 5, 0, 64, 147, 0, 249, 166, 0, 128, 39, 0, 251, 18, 0, 128, 143, 1, 5, 160, 0, 64, 113, 1, 6, 176, 0, 128, 244, 1, 18, 154, 0, 128, 110, 1, 21, 175, 0, 128, 124, 1, 34, 157, 0, 64, 147, 1, 34, 73, 0, 128, 97, 1, 43, 0, 0, 64, 170, 1, 44, 149, 0, 64, 98, 1, 49, 108, 0, 64, 102, 1, 59, 115, 0, 64, 110, 1, 62, 127, 0, 64, 246, 1, 64, 147, 0, 128, 191, 1, 73, 142, 0, 64, 153, 1, 76, 134, 0, 64, 226, 1, 86, 146, 0, 128, 125, 1, 98, 120, 0, 128, 184, 1, 99, 136, 0, 64, 162, 1, 108, 130, 0, 0, 5, 1, 68, 0, 5, 165, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]";

                            if (templateString == null || templateString.isEmpty()) {
                                Log.e(TAG, "  ✗ Empty template data - skipping");
                                failCount++;
                                continue;
                            }

                            templateString = templateString.replace("[", "").replace("]", "");
                            String[] parts = templateString.split(",");

                            int maxLen = Math.min(parts.length, 384);
                            byte[] signedBytes = new byte[maxLen];

                            for (int i = 0; i < maxLen; i++) {
                                int value = Integer.parseInt(parts[i].trim()); // 0–255
                                signedBytes[i] = (byte) value;                 // SIGNED byte
                            }
                            Log.d(TAG, "  ┌─ STEP 1: Received from API ──────────────");
                            Log.d(TAG, "  │ Format: Arrays.toString() - DIRECT from PAC_API!");
                            Log.d(TAG, "  │ String length: " + templateString.length() + " chars");
                            Log.d(TAG, "  │ First 50 chars: " + templateString.substring(0, Math.min(50, templateString.length())));

                            // Convert string to byte[] immediately
                            byte[] templateBytes = DatabaseHelper.parseStringToByteArray(templateString);

                            if (templateBytes == null || templateBytes.length == 0) {
                                Log.e(TAG, "  ✗ Failed to parse template string - skipping");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  │ Parsed to: " + templateBytes.length + " bytes");
                            StringBuilder hexReceived = new StringBuilder();
                            for (int i = 0; i < Math.min(20, templateBytes.length); i++) {
                                hexReceived.append(String.format("%02X ", templateBytes[i]));
                            }
                            Log.d(TAG, "  │ First 20 bytes (HEX): " + hexReceived.toString());
                            Log.d(TAG, "  └────────────────────────────────────────");

                            // STEP 1: Save fingerprint to SQLite database FIRST
                            // This is REQUIRED before enrolling to scanner
                            Log.d(TAG, "  → Saving to database...");

                            // Convert fingerType int to string
                            String fingerTypeStr = getFingerTypeName(fingerprint.getFingerIndex());

                            // Save original string directly to database (TEXT column)
                            // IMPORTANT: Store the ORIGINAL unsigned format from API, not the converted signed format!
                            int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                                String.valueOf(fingerprint.getId()),        // API ID
                                employee.getStaffID(),                      // Employee Number
                                employee.getStaffID(),                      // Username (use staffID)
                                employee.getFullName(),                     // Name
                                employee.getRole() != null ? employee.getRole() : "N/A", // FIXED: Role (not department!)
                                employee.isAllowedOverride(),               // Override permission from server
                                templateString,                             // Original string from API (unsigned: "[69, 145, ...]")
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

                            Log.d(TAG, "  ┌─ STEP 2: Database Round-Trip ───────────");
                            Log.d(TAG, "  │ Reading from database...");
                            String templateStringFromDb = dbHelper.getTemplateDataByScannerId(scannerId);

                            if (templateStringFromDb == null) {
                                Log.e(TAG, "  ✗ Failed to read template from database");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  │ DB string length: " + templateStringFromDb.length() + " chars");
                            Log.d(TAG, "  │ First 50 chars: " + templateStringFromDb.substring(0, Math.min(50, templateStringFromDb.length())));

                            byte[] templateBytesFromDb = DatabaseHelper.parseStringToByteArray(templateStringFromDb);

                            if (templateBytesFromDb == null) {
                                Log.e(TAG, "  ✗ Failed to parse DB template string");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  │ Converted size: " + templateBytesFromDb.length + " bytes");
                            StringBuilder hexFromDb = new StringBuilder();
                            for (int i = 0; i < Math.min(20, templateBytesFromDb.length); i++) {
                                hexFromDb.append(String.format("%02X ", templateBytesFromDb[i]));
                            }
                            Log.d(TAG, "  │ First 20 bytes (HEX): " + hexFromDb.toString());

                            // CRITICAL: Compare with original
                            boolean dataMatches = true;
                            if (templateBytes.length == templateBytesFromDb.length) {
                                for (int i = 0; i < Math.min(20, templateBytes.length); i++) {
                                    if (templateBytes[i] != templateBytesFromDb[i]) {
                                        dataMatches = false;
                                        Log.e(TAG, "  │ ⚠ MISMATCH at byte " + i + ": Original=" +
                                            String.format("%02X", templateBytes[i]) + " vs DB=" +
                                            String.format("%02X", templateBytesFromDb[i]));
                                        break;
                                    }
                                }
                            } else {
                                dataMatches = false;
                                Log.e(TAG, "  │ ⚠ SIZE MISMATCH: Original=" + templateBytes.length +
                                    " vs DB=" + templateBytesFromDb.length);
                            }
                            Log.d(TAG, "  │ Data integrity: " + (dataMatches ? "✓ MATCH" : "✗ CORRUPTED"));
                            Log.d(TAG, "  └────────────────────────────────────────");

                            // STEP 3: Enroll to scanner - SPLIT 768 bytes into TWO 384-byte templates
                            // VEMS2 Desktop sends 768 bytes (2 templates of same finger)
                            // We enroll BOTH templates with different Scanner IDs for better matching
                            int STANDARD_TEMPLATE_SIZE = 384;
                            int templateCount = templateBytesFromDb.length / STANDARD_TEMPLATE_SIZE;

                            if (templateBytesFromDb.length % STANDARD_TEMPLATE_SIZE != 0) {
                                Log.e(TAG, "  ✗ Invalid template size: " + templateBytesFromDb.length + " bytes");
                                failCount++;
                                continue;
                            }

                            Log.d(TAG, "  → Detected " + templateCount + " template(s) of " + STANDARD_TEMPLATE_SIZE + " bytes each");
                            Log.d(TAG, "  → Enrolling ALL " + templateCount + " templates with separate Scanner IDs");

                            // Enroll each template separately
                            int enrolledCount = 0;
                            for (int t = 0; t < 1; t++) {
                                // Extract this template
                                byte[] template = new byte[STANDARD_TEMPLATE_SIZE];
                                System.arraycopy(templateBytesFromDb, t * STANDARD_TEMPLATE_SIZE, template, 0, STANDARD_TEMPLATE_SIZE);

                                // Calculate Scanner ID (offset by 50000 for each template)
                                // Template 1: scannerId (e.g., 10001)
                                // Template 2: scannerId + 50000 (e.g., 60001)
                                int enrollUserId = scannerId + (t * 50000);

                                Log.d(TAG, "  ┌─ STEP 3." + (t+1) + ": Enrolling Template " + (t+1) + " ───");
                                Log.d(TAG, "  │ Scanner ID: " + enrollUserId);
                                Log.d(TAG, "  │ Template size: " + STANDARD_TEMPLATE_SIZE + " bytes");

                                // Log first 20 bytes
                                StringBuilder hexEnroll = new StringBuilder();
                                for (int i = 0; i < Math.min(20, template.length); i++) {
                                    hexEnroll.append(String.format("%02X ", template[i]));
                                }
                                Log.d(TAG, "  │ First 20 bytes (HEX): " + hexEnroll.toString());
                                Log.d(TAG, "  │ Calling UF_EnrollTemplate...");

                                int[] enrollId = new int[1];
                                int[] templateSize = new int[1];
                                enrollId[0] = 0;
                                templateSize[0] = 384;
                                long startTime = System.currentTimeMillis();

                                UF_RET_CODE enrollRet = sdk.UF_EnrollTemplate(
                                    enrollUserId,                         // Different ID for each template
                                    UF_ENROLL_OPTION.UF_ENROLL_NONE,     // Use provided ID
                                        templateSize[0],               // 384 bytes
                                        signedBytes,                             // This template
                                        enrollId
                                );

                                long elapsed = System.currentTimeMillis() - startTime;
                                Log.d(TAG, "  │ Completed in " + elapsed + "ms");
                                Log.d(TAG, "  │ Result: " + enrollRet);
                                Log.d(TAG, "  │ Returned enrollID: " + enrollId[0]);

                                if (enrollRet == UF_RET_CODE.UF_RET_SUCCESS) {
                                    Log.d(TAG, "  │ Status: ✓ SUCCESS");
                                    Log.d(TAG, "  └────────────────────────────────────────");
                                    enrolledCount++;
                                } else if (enrollRet == UF_RET_CODE.UF_ERR_EXIST_ID) {
                                    Log.d(TAG, "  │ Status: ⊙ ALREADY EXISTS");
                                    Log.d(TAG, "  └────────────────────────────────────────");
                                    enrolledCount++;
                                } else {
                                    Log.e(TAG, "  │ Status: ✗ FAILED");
                                    Log.e(TAG, "  │ Error: " + enrollRet);
                                    Log.e(TAG, "  └────────────────────────────────────────");
                                }
                            }

                            // Count as success if at least one template enrolled
                            if (enrolledCount > 0) {
                                Log.d(TAG, "  ✓ Enrolled " + enrolledCount + "/" + templateCount + " templates");
                                successCount++;
                                // Mark as enrolled in database
                                dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                            } else {
                                Log.e(TAG, "  ✗ Failed to enroll any templates");
                                failCount++;
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "  ✗ Error: " + e.getMessage());
                            failCount++;
                        }
                    }
                }

                // Fix provisional templates
                //sdk.UF_FixProvisionalTemplate();

                // Save templates to persistent flash memory
                // WITHOUT THIS: Templates exist only in RAM and are lost when scanner disconnects
                Log.d(TAG, "════════════════════════════════════════════════════════════");
                Log.d(TAG, "Saving templates to persistent flash memory...");
                long saveStartTime = System.currentTimeMillis();

//                UF_RET_CODE saveRet = sdk.UF_Save();
//
//                long saveElapsed = System.currentTimeMillis() - saveStartTime;
//                Log.d(TAG, "UF_Save() completed in " + saveElapsed + "ms");
//                Log.d(TAG, "Result: " + saveRet);
//
//                if (saveRet == UF_RET_CODE.UF_RET_SUCCESS) {
//                    Log.d(TAG, "✓ Templates successfully persisted to flash memory");
//                } else {
//                    Log.e(TAG, "✗ Failed to save templates to flash: " + saveRet);
//                    Log.e(TAG, "⚠ WARNING: Templates may be lost when scanner disconnects!");
//                }

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

        int STANDARD_TEMPLATE_SIZE = 384;
        int templateCount = template.length / STANDARD_TEMPLATE_SIZE;

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ ENROLLING TO SCANNER (Split Mode)");
        Log.d(TAG, "║ User ID (Scanner ID): " + id);
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "║ Template Size: " + template.length + " bytes");
        Log.d(TAG, "║ Template Count: " + templateCount);
        Log.d(TAG, "║ Finger Count: " + fingerCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        if (template.length % STANDARD_TEMPLATE_SIZE != 0) {
            Log.e(TAG, "✗ Invalid template size: " + template.length + " bytes");
            return false;
        }

        int enrolledCount = 0;

        // Enroll each 384-byte template separately
        for (int t = 0; t < templateCount; t++) {
            // Extract single 384-byte template
            byte[] singleTemplate = new byte[STANDARD_TEMPLATE_SIZE];
            System.arraycopy(template, t * STANDARD_TEMPLATE_SIZE, singleTemplate, 0, STANDARD_TEMPLATE_SIZE);

            // Use offset ID for second template to avoid collision
            // Template 1: id (e.g., 10001)
            // Template 2: id + 50000 (e.g., 60001)
            int enrollUserId = id + (t * 50000);

            Log.d(TAG, "→ Enrolling template #" + (t+1) + " with Scanner ID: " + enrollUserId);

            int[] enrollID = new int[1];
            UF_RET_CODE ret = sdk.UF_EnrollTemplate(
                enrollUserId,
                UF_ENROLL_OPTION.UF_ENROLL_NONE,
                STANDARD_TEMPLATE_SIZE,
                singleTemplate,
                enrollID
            );

            Log.d(TAG, "  UF_EnrollTemplate Result: " + ret);
            Log.d(TAG, "  Enroll ID returned: " + enrollID[0]);

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "  ✓ Template #" + (t+1) + " enrolled successfully");
                enrolledCount++;
            } else if (ret == UF_RET_CODE.UF_ERR_EXIST_ID) {
                Log.w(TAG, "  ⊙ Template #" + (t+1) + " already exists");
            } else {
                Log.e(TAG, "  ✗ Template #" + (t+1) + " enrollment failed: " + ret);

                if (ret == UF_RET_CODE.UF_ERR_TIME_OUT) {
                    Log.e(TAG, "    Error Type: TIMEOUT");
                } else if (ret == UF_RET_CODE.UF_ERR_INVALID_PARAMETER) {
                    Log.e(TAG, "    Error Type: INVALID PARAMETER");
                } else if (ret == UF_RET_CODE.UF_ERR_DATA_ERROR) {
                    Log.e(TAG, "    Error Type: DATA ERROR");
                } else {
                    Log.e(TAG, "    Error Type: " + (ret != null ? ret.toString() : "UNKNOWN"));
                }
            }
        }

        boolean success = enrolledCount > 0;
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ ENROLLMENT COMPLETE: " + (success ? "SUCCESS" : "FAILED"));
        Log.d(TAG, "║ Enrolled: " + enrolledCount + " / " + templateCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        return success;
    }
}