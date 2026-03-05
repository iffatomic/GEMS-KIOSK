/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // SDK and background processing
    private SFM_SDK_ANDROID sdk;
    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isScanning = false;
    private boolean isFirstResume = true; // Track if this is first onResume after onCreate

    // UI Components
    private EditText staffIdEditText, passwordEditText;
    private Button loginButton, fingerprintLoginButton;
    private ProgressBar progressBar;
    private Spinner languageSpinner;
    private android.widget.ImageView appLogo;

    // Double-tap tracking for logo
    private long lastLogoTapTime = 0;
    private static final long DOUBLE_TAP_INTERVAL = 500; // ms

    // Database and Preferences
    private DatabaseHelper dbHelper;
    private SharedPreferences languagePrefs;
    private SharedPreferences loginPrefs;

    private static final String LANGUAGE_PREF = "language_pref";
    private static final String LOGIN_PREF = "login_pref";
    private static final String SELECTED_LANGUAGE = "selected_language";
    private static final String IS_LOGGED_IN = "is_logged_in";
    private static final String LOGGED_IN_USER = "logged_in_user";

    // Language options
    private final String[] languageCodes = {"en", "ms"};
    private final String[] languageNames = {"English", "Bahasa Malaysia"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load saved language preference
        loadLanguagePreference();



        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Hide system navigation bar for kiosk mode
        hideSystemUI();

        // Initialize preferences and database
        languagePrefs = getSharedPreferences(LANGUAGE_PREF, MODE_PRIVATE);
        loginPrefs = getSharedPreferences(LOGIN_PREF, MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);

        // Initialize admin user in database
        initializeAdminUser();

        // Check if already logged in
        if (isUserLoggedIn()) {
            navigateToMainMenu();
            return;
        }

        // Initialize UI components
        initializeViews();

        // In onCreate(), after existing code:
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeSDK();

        // TEMPORARY: Clear scanner on startup
        //clearScannerMemory();

        // Setup language spinner
        setupLanguageSpinner();

        // Setup click listeners
        setupClickListeners();

        // START FINGERPRINT AUTO-SYNC SERVICE
        // This ensures SignalR connection is active and listening for enrollment events
        // Service will continue running in background even when navigating to other activities
        Intent fingerprintSyncIntent = new Intent(this, com.supremainc.sfm_sdk_android.service.FingerprintAutoSyncService.class);
        startService(fingerprintSyncIntent);
        Log.d(TAG, "✓ Fingerprint Auto-Sync Service started - SignalR now listening for enrollment events");
    }

    // Add this method temporarily
    private void clearScannerMemory() {
        new Thread(() -> {
            try {
                if (sdk != null) {
                    sdk.UF_Reconnect();

                    Log.d(TAG, "Clearing scanner memory...");
                    UF_RET_CODE ret = sdk.UF_DeleteAll();

                    runOnUiThread(() -> {
                        if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                            Toast.makeText(this, "Scanner memory cleared!", Toast.LENGTH_LONG).show();
                            Log.d(TAG, "✅ Scanner memory cleared successfully");
                        } else {
                            Toast.makeText(this, "Failed to clear: " + ret, Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Failed to clear scanner: " + ret);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing scanner", e);
            }
        }).start();
    }

    private void initializeViews() {
        staffIdEditText = findViewById(R.id.staffIdEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        fingerprintLoginButton = findViewById(R.id.fingerprintLoginButton);
        progressBar = findViewById(R.id.progressBar);
        languageSpinner = findViewById(R.id.language_spinner);
        appLogo = findViewById(R.id.app_logo);

        // Initially hide progress bar
        progressBar.setVisibility(View.GONE);
    }

    /**
     * Initialize the fingerprint scanner SDK
     */
    private void initializeSDK() {
        try {
            sdk = new SFM_SDK_ANDROID();
            Log.d(TAG, "SDK Version: " + sdk.UF_GetSDKVersion());
            sdk.UF_InitSysParameter();

            // Try to close any existing connection first
            try {
                Log.d(TAG, "Closing any existing connections...");
                sdk.UF_CloseCommPort();
                Thread.sleep(500);
                Log.d(TAG, "Port closed successfully");
            } catch (Exception e) {
                Log.d(TAG, "No existing connection to close: " + e.getMessage());
            }

            // Open fresh connection
            Log.d(TAG, "Opening fresh connection...");
            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);

            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                Log.d(TAG, "Trying /dev/ttyACM1...");
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Thread.sleep(300);
                sdk.UF_Reconnect();
                Log.d(TAG, "Scanner ready for login");
            } else {
                Log.e(TAG, "Failed to connect scanner: " + ret);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
        }
    }

    private void initializeAdminUser() {
        // Initialize admin user if not exists
        //dbHelper.initializeAdminUser("admin123", "admin123");
    }


    private void setupLanguageSpinner() {
        if (languageSpinner == null) {
            return;
        }

        // Create adapter with proper styling
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, languageNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(getResources().getColor(R.color.buttonText));
                textView.setTextSize(14);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(getResources().getColor(android.R.color.black));
                textView.setTextSize(16);
                textView.setPadding(16, 16, 16, 16);
                return view;
            }
        };

        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);

        // Set current language selection
        String currentLanguage = languagePrefs.getString(SELECTED_LANGUAGE, "en");
        int currentPosition = getLanguagePosition(currentLanguage);
        languageSpinner.setSelection(currentPosition);

        // Handle language selection
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLanguageCode = languageCodes[position];
                String currentLanguage = languagePrefs.getString(SELECTED_LANGUAGE, "en");

                if (!selectedLanguageCode.equals(currentLanguage)) {
                    changeLanguage(selectedLanguageCode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private int getLanguagePosition(String languageCode) {
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(languageCode)) {
                return i;
            }
        }
        return 0; // Default to English
    }

    private void changeLanguage(String languageCode) {
        // Save language preference
        SharedPreferences.Editor editor = languagePrefs.edit();
        editor.putString(SELECTED_LANGUAGE, languageCode);
        editor.apply();

        // Set locale
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.locale = locale;
        resources.updateConfiguration(config, dm);

        // Recreate activity to apply language changes
        recreate();
    }

    private void loadLanguagePreference() {
        SharedPreferences prefs = getSharedPreferences(LANGUAGE_PREF, MODE_PRIVATE);
        String languageCode = prefs.getString(SELECTED_LANGUAGE, "en");

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.locale = locale;
        resources.updateConfiguration(config, dm);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> performLogin());

        // Enable fingerprint authentication
        fingerprintLoginButton.setOnClickListener(v -> {
            showFingerprintDialog();
        });

        // Double-tap logo to access SystemSettings (same as MainActivity)
        appLogo.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogoTapTime < DOUBLE_TAP_INTERVAL) {
                // Double-tap detected!
                Log.d(TAG, "Double-tap on logo detected - Opening System Settings");
                Intent intent = new Intent(LoginActivity.this, SystemSettingsActivity.class);
                startActivity(intent);
            }
            lastLogoTapTime = currentTime;
        });
    }

    private void performLogin() {
        String staffId = staffIdEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validate input
        if (TextUtils.isEmpty(staffId)) {
            staffIdEditText.setError(getString(R.string.enter_staff_id));
            staffIdEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.enter_password));
            passwordEditText.requestFocus();
            return;
        }

        // Show progress
        showProgress(true);

        // Simulate network delay for better UX
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 1 second delay

                runOnUiThread(() -> {
                    // Authenticate user
                    if (dbHelper.authenticateAdmin(staffId, password)) {
                        // Login successful
                        saveLoginState(staffId);
                        showToast(getString(R.string.login_successful));
                        navigateToMainMenu();
                    } else {
                        // Login failed
                        showProgress(false);
                        showToast(getString(R.string.invalid_credentials));
                        passwordEditText.setText("");
                        passwordEditText.requestFocus();
                    }
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    showToast(getString(R.string.login_error));
                });
            }
        }).start();
    }

    /**
     * Show fingerprint login dialog with REAL SDK integration
     */
    private void showFingerprintDialog() {
        Log.d(TAG, "=== Fingerprint login initiated ===");

        // Inflate the dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fingerprint_scanner, null);

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();

        // Make dialog background transparent to show rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Get dialog views
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
        TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

        // Handle cancel button
        cancelButton.setOnClickListener(v -> {
            if (isScanning && sdk != null) {
                try {
                    sdk.UF_Cancel(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling scan", e);
                }
                isScanning = false;
            }
            dialog.dismiss();
        });

        // Check if SDK is ready
        if (sdk == null) {
            fingerprintInstruction.setText("Scanner not initialized. Please restart the app.");
            fingerprintProgress.setVisibility(View.GONE);
            Log.e(TAG, "SDK is null!");
            return;
        }

        // Start real fingerprint scanning
        isScanning = true;
        fingerprintInstruction.setText("Place your finger on the scanner...");
        fingerprintProgress.setVisibility(View.VISIBLE);

        Log.d(TAG, "Starting fingerprint identification for login...");

        // Run fingerprint identification in background
        executor.execute(() -> {
            try {
                // Cancel any previous operations first (PMOTamsNormal pattern)
                sdk.UF_Cancel(false);
                Thread.sleep(100); // Small delay after cancel

                // Reconnect to scanner
                sdk.UF_Reconnect();
                Thread.sleep(200); // Delay for scanner to be ready
                Log.d(TAG, "Scanner reconnected and ready");

                // Diagnostic: Check how many templates are in scanner memory
                int[] numTemplates = new int[1];
                UF_RET_CODE checkRet = sdk.UF_GetNumOfTemplate(numTemplates);
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ SCANNER TEMPLATE CHECK (Before Identify)");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ UF_GetNumTemplate result: " + checkRet);
                Log.d(TAG, "║ Templates in scanner: " + numTemplates[0]);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                if (numTemplates[0] == 0) {
                    Log.e(TAG, "⚠ WARNING: Scanner has 0 templates!");
                    Log.e(TAG, "⚠ No fingerprints enrolled or scanner memory cleared.");
                }

                mainHandler.post(() -> {
                    fingerprintInstruction.setText("Scanning fingerprint...");
                });

                // Identify fingerprint (scans once)
                int[] userID = new int[1];
                byte[] subID = new byte[1];

                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ SCANNING FINGERPRINT FOR VERIFICATION");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Place finger on scanner...");
                Log.d(TAG, "║ Calling UF_Identify (single scan)...");
                int[] numOfTemplate = new int[1];
                byte[] templateData = new byte[3840];
                int[] imageQuality = new int[1];
                int[] templateSize = new int[1];
                //ret = sdk.UF_ScanTemplate(templateData, templateSize, imageQuality);
                //ret = sdk.UF_ReadTemplate(1, numOfTemplate, templateData);
                //Log.d(TAG, "Test_Identify: Read Template : " + ret.toString());

                UF_RET_CODE ret = sdk.UF_ScanTemplate(templateData, templateSize, imageQuality);
                if (ret == UF_RET_CODE.UF_RET_SUCCESS){
                    ret = sdk.UF_IdentifyTemplate(384, templateData, userID, subID);
                    //ret = sdk.UF_IdentifyTemplate(384, templateData, userID, subID);
                    //ret = sdk.UF_Identify(userID, subID);
                    Log.d(TAG, "Test_Identify: IdentifyTemplate : " + ret.toString() + " User ID : " + userID[0] + " Sub ID : " + subID[0]);
                }

                //UF_RET_CODE ret = sdk.UF_Identify(userID, subID);

                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ IDENTIFICATION RESULT");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ Result: " + ret);
                Log.d(TAG, "║ Scanner ID found: " + userID[0]);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                mainHandler.post(() -> {
                    fingerprintInstruction.setText("Verifying identity...");
                });

                // Check if identification succeeded
                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    int scannedScannerID = userID[0];
                    Log.d(TAG, "Fingerprint identified with scanner ID: " + scannedScannerID);

                    // Try finding user in TABLE_USERS first
                    final DatabaseHelper.User user = dbHelper.getUserByScannerUserId(scannedScannerID);

                    // If not found in users, check synced fingerprints table
                    final DatabaseHelper.SyncedFingerprint syncedUser;
                    if (user == null) {
                        Log.d(TAG, "Not found in users table, checking synced_fingerprints...");
                        syncedUser = dbHelper.getUserByScannerId(scannedScannerID);
                    } else {
                        syncedUser = null;
                    }

                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);

                        // Cancel scanner operation before marking as not scanning
                        if (isScanning && sdk != null) {
                            try {
                                sdk.UF_Cancel(false);
                                Log.d(TAG, "Scanner cancelled after identification");
                            } catch (Exception e) {
                                Log.e(TAG, "Error cancelling scanner", e);
                            }
                        }
                        isScanning = false;

                        // Handle regular user authenticatio
                        if (user != null) {
                            // SUCCESS! Login with this user (ignore role check for now)
                            Log.d(TAG, "User login successful: " + user.getName());

                            fingerprintInstruction.setText("✓ Login Successful!\nWelcome, " + user.getName());
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                            // Delay to show success message
                            new Handler().postDelayed(() -> {
                                dialog.dismiss();
                                saveLoginState(user.getStaffId());
                                showToast("Welcome, " + user.getName() + "!");
                                navigateToMainMenu();
                            }, 1000);

                        // Handle synced user authentication (from server)
                        } else if (syncedUser != null) {
                            // SUCCESS! Login with synced user (ignore role check for now)
                            Log.d(TAG, "Synced user login successful: " + syncedUser.getName());

                            fingerprintInstruction.setText("✓ Login Successful!\nWelcome, " + syncedUser.getName());
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                            // Delay to show success message
                            new Handler().postDelayed(() -> {
                                dialog.dismiss();
                                saveLoginState(syncedUser.getEmployeeNumber());
                                showToast("Welcome, " + syncedUser.getName() + "!");
                                navigateToMainMenu();
                            }, 1000);

                        } else {
                            // User not found in either database
                            Log.w(TAG, "Fingerprint identified but user not found for scanner ID: " + scannedScannerID);
                            fingerprintInstruction.setText("❌ User not found\nPlease contact administrator");
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            showToast("Fingerprint not registered");
                        }
                    });

                } else {
                    // Identification failed
                    Log.e(TAG, "Fingerprint identification failed: " + ret);

                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);

                        // Cancel scanner operation before marking as not scanning
                        if (isScanning && sdk != null) {
                            try {
                                sdk.UF_Cancel(false);
                                Log.d(TAG, "Scanner cancelled after failed identification");
                            } catch (Exception e) {
                                Log.e(TAG, "Error cancelling scanner", e);
                            }
                        }
                        isScanning = false;

                        fingerprintInstruction.setText("❌ Fingerprint not recognized\nPlease try again");
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        showToast("Fingerprint not recognized");
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during fingerprint login", e);
                e.printStackTrace();

                mainHandler.post(() -> {
                    fingerprintProgress.setVisibility(View.GONE);

                    // Cancel scanner operation before marking as not scanning
                    if (isScanning && sdk != null) {
                        try {
                            sdk.UF_Cancel(false);
                            Log.d(TAG, "Scanner cancelled after exception");
                        } catch (Exception ex) {
                            Log.e(TAG, "Error cancelling scanner", ex);
                        }
                    }
                    isScanning = false;

                    fingerprintInstruction.setText("Error: " + e.getMessage());
                    fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    showToast("Error during fingerprint scan");
                });
            }
        });

        dialog.show();
    }


    private void saveLoginState(String staffId) {
        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean(IS_LOGGED_IN, true);
        editor.putString(LOGGED_IN_USER, staffId);
        editor.apply();
    }

    private boolean isUserLoggedIn() {
        return loginPrefs.getBoolean(IS_LOGGED_IN, false);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        staffIdEditText.setEnabled(!show);
        passwordEditText.setEnabled(!show);
    }

    private void navigateToMainMenu() {
        Intent intent = new Intent(LoginActivity.this, MainMenuActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up resources
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        if (sdk != null && isScanning) {
            try {
                sdk.UF_Cancel(false);
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling scan", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh language selection
        if (languageSpinner != null) {
            String currentLanguage = languagePrefs.getString(SELECTED_LANGUAGE, "en");
            int currentPosition = getLanguagePosition(currentLanguage);
            languageSpinner.setSelection(currentPosition);
        }

        // SIMPLIFIED: Port stays open (following PMOTamsNormal pattern)
        // Just reconnect to clear any cached state
        if (!isFirstResume && sdk != null) {
            Log.d(TAG, "onResume: Reconnecting to scanner...");
            new Thread(() -> {
                try {
                    sdk.UF_Reconnect(); // Clear cached module information
                    Thread.sleep(200); // Small delay for stability
                    Log.d(TAG, "✅ Scanner reconnected in onResume");
                } catch (Exception e) {
                    Log.e(TAG, "Error reconnecting in onResume", e);
                }
            }).start();
        } else if (isFirstResume) {
            Log.d(TAG, "onResume: First resume - scanner already initialized");
            isFirstResume = false;
        }
    }

    /**
     * Hide system navigation bar for kiosk mode
     * User can still swipe up to show it, but it will hide again when window loses focus
     */
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    /**
     * Disable back button for kiosk mode
     * Prevents users from exiting login screen accidentally
     */
    @Override
    public void onBackPressed() {
        // Do nothing - disable back button in kiosk mode
        // Users must use fingerprint authentication or admin PIN to proceed
        Log.d(TAG, "Back button pressed - disabled in kiosk mode");
    }
}