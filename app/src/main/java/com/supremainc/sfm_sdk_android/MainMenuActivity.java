/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk_android.data.model.request.ValidateFingerprintRequest;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;

import android.util.Base64;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainMenuActivity extends AppCompatActivity {

    private static final String TAG = "MainMenuActivity";

    // UI Components
    private Button btnFingerprintEnrollment, btnDeleteUser, btnActivateVault, btnSystemSettings,
            btnLogOut, btnInfo, btnManualOverride, btnMainVaultStatus, btnDayVaultStatus, btnActivityLog;
    private Spinner languageSpinner;
    private TextView overrideCountBadge;
    private LinearLayout noOverridesMessage;
    private LinearLayout overrideCardsContainer;
    private android.widget.ImageView imgLogo;

    private Button btnViewMoreOverrides;

    // Double-tap detection for logo
    private long lastLogoTapTime = 0;
    private static final long DOUBLE_TAP_DELAY = 500; // 500ms between taps

    // Language preferences
    private SharedPreferences languagePrefs;
    private SharedPreferences loginPrefs;
    private static final String LANGUAGE_PREF = "language_pref";
    private static final String LOGIN_PREF = "login_pref";
    private static final String SELECTED_LANGUAGE = "selected_language";
    private static final String IS_LOGGED_IN = "is_logged_in";
    private static final String LOGGED_IN_USER = "logged_in_user";

    // Language options - English and Malay only
    private final String[] languageCodes = {"en", "ms"};
    private final String[] languageNames = {"English", "Bahasa Malaysia"};

    // Vault Override Manager
    private VaultOverrideManager vaultManager;

    // Staff Enrollment Service for API verification
    private StaffEnrollmentService staffEnrollmentService;

    // Handler for updating countdown timers
    private Handler updateHandler = new Handler();
    private Runnable updateRunnable;

    // Fingerprint SDK and verification
    private SFM_SDK_ANDROID sdk;
    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isScanning = false;
    private DatabaseHelper dbHelper;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load saved language preference before setting content view
        loadLanguagePreference();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Initialize preferences
        languagePrefs = getSharedPreferences(LANGUAGE_PREF, MODE_PRIVATE);
        loginPrefs = getSharedPreferences(LOGIN_PREF, MODE_PRIVATE);

        // Initialize vault manager
        vaultManager = VaultOverrideManager.getInstance(this);

        // Initialize staff enrollment service for API verification
        staffEnrollmentService = new StaffEnrollmentService(this);

        // Initialize database helper and SDK
        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeSDK();

        // Login check removed - no login required, admin enrollment is the authentication

        // Initialize UI components
        initializeViews();

        // Setup language spinner
        setupLanguageSpinner();

        // Setup button click listeners
        setupClickListeners();

        // Setup dashboard
        refreshOverrideDashboard();

        // Start auto-refresh timer
        startDashboardAutoRefresh();
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
                sdk.UF_CloseCommPort();
                Thread.sleep(500);
            } catch (Exception e) {
                Log.d(TAG, "No existing connection to close");
            }

            // Open fresh connection
            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);


            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Thread.sleep(300);
                sdk.UF_Reconnect();
                Log.d(TAG, "Scanner ready");
            } else {
                Log.e(TAG, "Failed to connect scanner: " + ret);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
        }
    }

    private void initializeViews() {
        btnFingerprintEnrollment = findViewById(R.id.btn_fingerprint_enrollment);
        btnDeleteUser = findViewById(R.id.btn_delete_user);
        btnActivateVault = findViewById(R.id.btn_activate_vault);
        btnSystemSettings = findViewById(R.id.btn_system_settings);
        btnLogOut = findViewById(R.id.btn_log_out);
        btnInfo = findViewById(R.id.btn_info);
        btnManualOverride = findViewById(R.id.btn_manual_override);
        btnMainVaultStatus = findViewById(R.id.btn_main_vault_status);
        btnDayVaultStatus = findViewById(R.id.DayVaultStatus);
        btnActivityLog = findViewById(R.id.btn_activity_log);
        languageSpinner = findViewById(R.id.language_spinner);
        imgLogo = findViewById(R.id.imgLogo);

        // Dashboard components
        overrideCountBadge = findViewById(R.id.overrideCountBadge);
        noOverridesMessage = findViewById(R.id.noOverridesMessage);
        overrideCardsContainer = findViewById(R.id.overrideCardsContainer);
        btnViewMoreOverrides = findViewById(R.id.btnViewMoreOverrides);
    }

    private void setupLanguageSpinner() {
        if (languageSpinner == null) {
            return;
        }

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

        String currentLanguage = languagePrefs.getString(SELECTED_LANGUAGE, "en");
        int currentPosition = getLanguagePosition(currentLanguage);
        languageSpinner.setSelection(currentPosition);

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
            }
        });
    }

    private int getLanguagePosition(String languageCode) {
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(languageCode)) {
                return i;
            }
        }
        return 0;
    }

    private void changeLanguage(String languageCode) {
        SharedPreferences.Editor editor = languagePrefs.edit();
        editor.putString(SELECTED_LANGUAGE, languageCode);
        editor.apply();

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.locale = locale;
        resources.updateConfiguration(config, dm);

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
        btnFingerprintEnrollment.setOnClickListener(v -> {
            // Show admin verification dialog before allowing access to enrollment
            showAdminVerificationDialog();
        });

        btnDeleteUser.setOnClickListener(v -> {
            // Navigate to Delete User Activity (admin verification happens inside the activity)
            Intent intent = new Intent(MainMenuActivity.this, DeleteUserActivity.class);
            startActivity(intent);
        });

        btnInfo.setOnClickListener(v -> {
            try {
                ProfileBottomSheet profileBottomSheet = new ProfileBottomSheet();
                profileBottomSheet.show(getSupportFragmentManager(), "ProfileBottomSheet");
            } catch (Exception e) {
                showToast("Profile - Error loading");
                Log.e("MainMenuActivity", "Error showing profile", e);
            }
        });
        btnManualOverride.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, ManualOverrideActivity.class);
            startActivity(intent);
        });
        btnMainVaultStatus.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, MainVaultStatusActivity.class);
            startActivity(intent);
        });
        btnDayVaultStatus.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, DayVaultStatusActivity.class);
            startActivity(intent);
        });

        btnActivityLog.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, ActivityLogActivity.class);
            startActivity(intent);
        });

        btnSystemSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(MainMenuActivity.this, SystemSettingsActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                showToast(getString(R.string.system_settings) + " - Coming Soon");
            }
        });

        btnActivateVault.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, ActivateVaultActivity.class);
            startActivity(intent);
        });

        btnViewMoreOverrides.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, OverrideListActivity.class);
            startActivity(intent);
        });

        btnLogOut.setOnClickListener(v -> {
            showLogoutConfirmation();
        });

        // Double-tap logo to open System Settings (hidden admin feature)
        if (imgLogo != null) {
            imgLogo.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();
                long timeDiff = currentTime - lastLogoTapTime;

                if (timeDiff < DOUBLE_TAP_DELAY && timeDiff > 0) {
                    // Double tap detected!
                    Log.d(TAG, "Logo double-tapped - opening System Settings");
                    Toast.makeText(this, "Opening System Settings...", Toast.LENGTH_SHORT).show();

                    try {
                        Intent intent = new Intent(MainMenuActivity.this, SystemSettingsActivity.class);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening System Settings", e);
                        showToast("Error opening System Settings");
                    }

                    // Reset tap time to prevent triple-tap
                    lastLogoTapTime = 0;
                } else {
                    // First tap or too long between taps
                    lastLogoTapTime = currentTime;
                }
            });
        }
    }

    /**
     * Refresh the Manual Override Dashboard
     */
    private void refreshOverrideDashboard() {
        List<VaultOverrideManager.VaultOverride> activeOverrides = vaultManager.getActiveOverrides();

        // Update count badge
        int count = activeOverrides.size();
        overrideCountBadge.setText(String.valueOf(count));

        if (count == 0) {
            // Show "all secure" message
            noOverridesMessage.setVisibility(View.VISIBLE);
            overrideCardsContainer.setVisibility(View.GONE);
            btnViewMoreOverrides.setVisibility(View.GONE);  // NEW: Hide button
        } else {
            // Hide "all secure" message and show cards
            noOverridesMessage.setVisibility(View.GONE);
            overrideCardsContainer.setVisibility(View.VISIBLE);

            // Clear existing cards
            overrideCardsContainer.removeAllViews();

            // Show only first 3 overrides on dashboard
            int displayCount = Math.min(count, 3);
            for (int i = 0; i < displayCount; i++) {
                addOverrideCard(activeOverrides.get(i));
            }

            // NEW: Show "View More" button if more than 3 overrides
            if (count >= 1) {
                btnViewMoreOverrides.setVisibility(View.VISIBLE);
                btnViewMoreOverrides.setText("View All ");
            } else {
                btnViewMoreOverrides.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Add a vault override card to the dashboard
     */
    private void addOverrideCard(VaultOverrideManager.VaultOverride override) {
        // Create card view
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        cardView.setLayoutParams(cardParams);
        cardView.setCardBackgroundColor(Color.parseColor("#40FF0000")); // Transparent red
        cardView.setCardElevation(4);
        cardView.setRadius(8);
        cardView.setContentPadding(16, 16, 16, 16);

        // Create content layout
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Header row: Vault name and status
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        // Vault name
        TextView vaultNameText = new TextView(this);
        vaultNameText.setText("🔓 " + override.vaultName);
        vaultNameText.setTextSize(18);
        vaultNameText.setTextColor(getResources().getColor(R.color.textPrimary));
        vaultNameText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        vaultNameText.setLayoutParams(nameParams);
        headerRow.addView(vaultNameText);

        // Status badge
        TextView statusBadge = new TextView(this);
        statusBadge.setText("OVERRIDE ACTIVE");
        statusBadge.setTextSize(12);
        statusBadge.setTextColor(Color.WHITE);
        statusBadge.setBackgroundColor(Color.parseColor("#FF0000"));
        statusBadge.setPadding(12, 6, 12, 6);
        statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(statusBadge);

        contentLayout.addView(headerRow);

        // Supervisor info
        TextView supervisorText = new TextView(this);
        supervisorText.setText("Supervisor: " + override.supervisorId);
        supervisorText.setTextSize(14);
        supervisorText.setTextColor(getResources().getColor(R.color.textSecondary));
        LinearLayout.LayoutParams supervisorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        supervisorParams.setMargins(0, 8, 0, 8);
        supervisorText.setLayoutParams(supervisorParams);
        contentLayout.addView(supervisorText);

        // Time remaining with countdown
        TextView timeRemainingText = new TextView(this);
        timeRemainingText.setText("Time Remaining: " + override.getRemainingTimeFormatted());
        timeRemainingText.setTextSize(16);
        timeRemainingText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
        timeRemainingText.setTypeface(null, android.graphics.Typeface.BOLD);
        timeRemainingText.setTag(override); // Store override reference for updates
        contentLayout.addView(timeRemainingText);

        // Deactivate button
        Button deactivateButton = new Button(this);
        deactivateButton.setText("Deactivate Override");
        deactivateButton.setTextColor(Color.WHITE);
        deactivateButton.setBackgroundColor(Color.parseColor("#FF0000"));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 16, 0, 0);
        deactivateButton.setLayoutParams(buttonParams);
        deactivateButton.setOnClickListener(v -> {
            showDeactivateConfirmation(override.vaultName);
        });
        contentLayout.addView(deactivateButton);

        cardView.addView(contentLayout);
        overrideCardsContainer.addView(cardView);
    }

    /**
     * Show confirmation dialog for deactivating override
     */
    private void showDeactivateConfirmation(String vaultName) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate Override")
                .setMessage("Are you sure you want to deactivate the manual override for " + vaultName + "?")
                .setPositiveButton("Yes, Deactivate", (dialog, which) -> {
                    vaultManager.removeOverride(vaultName);
                    refreshOverrideDashboard();
                    Toast.makeText(this, vaultName + " override deactivated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Start auto-refresh for dashboard (updates countdown timers every second)
     */
    private void startDashboardAutoRefresh() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Update countdown timers for all cards
                updateCountdownTimers();

                // Schedule next update in 1 second
                updateHandler.postDelayed(this, 1000);
            }
        };
        updateHandler.post(updateRunnable);
    }

    /**
     * Update countdown timers for all override cards
     */
    private void updateCountdownTimers() {
        for (int i = 0; i < overrideCardsContainer.getChildCount(); i++) {
            View cardView = overrideCardsContainer.getChildAt(i);
            if (cardView instanceof CardView) {
                LinearLayout contentLayout = (LinearLayout) ((CardView) cardView).getChildAt(0);

                // Find the time remaining TextView (it has the override as tag)
                for (int j = 0; j < contentLayout.getChildCount(); j++) {
                    View child = contentLayout.getChildAt(j);
                    if (child instanceof TextView && child.getTag() instanceof VaultOverrideManager.VaultOverride) {
                        TextView timeText = (TextView) child;
                        VaultOverrideManager.VaultOverride override =
                                (VaultOverrideManager.VaultOverride) child.getTag();

                        if (override.isExpired()) {
                            // Override has expired, refresh dashboard
                            refreshOverrideDashboard();
                            return;
                        } else {
                            // Update countdown
                            timeText.setText("Time Remaining: " + override.getRemainingTimeFormatted());
                        }
                    }
                }
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

        // Refresh dashboard when returning to this activity
        refreshOverrideDashboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop updates when activity is not visible
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private boolean isUserLoggedIn() {
        return loginPrefs.getBoolean(IS_LOGGED_IN, false);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(MainMenuActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_confirmation_title))
                .setMessage(getString(R.string.logout_confirmation_message))
                .setPositiveButton(getString(R.string.logout), (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private void performLogout() {
        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean(IS_LOGGED_IN, false);
        editor.putString(LOGGED_IN_USER, "");
        editor.apply();

        showToast(getString(R.string.logout_successful));
        navigateToLogin();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // =================== ADMIN VERIFICATION FOR ENROLLMENT ===================

    /**
     * Show admin fingerprint verification dialog before allowing access to enrollment
     */
    private void showAdminVerificationDialog() {
        Log.d(TAG, "=== Admin verification initiated ===");

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
        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        android.widget.ImageView fingerprintIcon = dialogView.findViewById(R.id.fingerprintIcon);

        // Update dialog title and instruction
        if (dialogTitle != null) {
            dialogTitle.setText("Admin Verification");
        }
        fingerprintInstruction.setText("Admin fingerprint required to access enrollment.\n\nPlace your finger on the scanner...");

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
            dialog.show();
            return;
        }

        // Start fingerprint scanning
        isScanning = true;
        fingerprintProgress.setVisibility(View.VISIBLE);

        Log.d(TAG, "Starting admin fingerprint verification...");

        // Run fingerprint identification in background
        executor.execute(() -> {
            try {
                // Reconnect to scanner
                sdk.UF_Reconnect();
                Log.d(TAG, "Scanner reconnected");

                // Cancel any previous operations to ensure scanner is ready
                sdk.UF_Cancel(false);
                Log.d(TAG, "UF_Cancel called to clear previous operations");

                mainHandler.post(() -> {
                    fingerprintInstruction.setText("Scanning fingerprint...");
                    fingerprintIcon.setImageResource(R.drawable.fingerprint_scanning);
                });

                // QUALITY CHECK: Scan template first to get quality score
                byte[] templateData = new byte[384];
                int[] templateSize = new int[1];
                int[] imageQuality = new int[1];

                Log.d(TAG, "Scanning fingerprint template to check quality...");
                UF_RET_CODE scanRet = sdk.UF_ScanTemplate(templateData, templateSize, imageQuality);

                Log.d(TAG, "╔════════════════════════════════════════");
                Log.d(TAG, "║ FINGERPRINT QUALITY CHECK");
                Log.d(TAG, "╠════════════════════════════════════════");
                Log.d(TAG, "║ Scan Result: " + scanRet);
                Log.d(TAG, "║ Template Size: " + templateSize[0]);
                Log.d(TAG, "║ Image Quality: " + imageQuality[0]);
                Log.d(TAG, "╚════════════════════════════════════════");

                if (scanRet != UF_RET_CODE.UF_RET_SUCCESS) {
                    Log.e(TAG, "Failed to scan fingerprint template: " + scanRet);
                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        isScanning = false;
                        fingerprintInstruction.setText("❌ Fingerprint scan failed\n\nPlease try again.");
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
                        showToast("Fingerprint scan failed");
                    });
                    return;
                }

                // Check quality threshold
                if (imageQuality[0] < 40) {
                    Log.w(TAG, "⚠️ LOW QUALITY FINGERPRINT: " + imageQuality[0] + " (threshold: 40)");
                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        isScanning = false;
                        fingerprintInstruction.setText("❌ Poor fingerprint quality (" + imageQuality[0] + ")\n\n" +
                                "Please:\n• Clean your finger\n• Press firmly\n• Try again");
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                        fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
                        showToast("Poor quality - please clean finger and retry");
                    });
                    return;
                }

                // Quality is acceptable, now identify using the scanned template
                int[] userID = new int[1];
                byte[] subID = new byte[1];

                Log.d(TAG, "Quality acceptable (" + imageQuality[0] + "), identifying fingerprint...");
                UF_RET_CODE ret = sdk.UF_IdentifyTemplate(templateSize[0], templateData, userID, subID);

                Log.d(TAG, "UF_IdentifyTemplate returned: " + ret + ", Scanner ID: " + userID[0]);

                mainHandler.post(() -> {
                    fingerprintInstruction.setText("Verifying identity...");
                });

                // Check if identification succeeded
                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    int scannedScannerID = userID[0];
                    Log.d(TAG, "╔═══════════════════════════════════════════");
                    Log.d(TAG, "║ ADMIN VERIFICATION - DATABASE LOOKUP");
                    Log.d(TAG, "╠═══════════════════════════════════════════");
                    Log.d(TAG, "║ Scanner ID: " + scannedScannerID);
                    Log.d(TAG, "╚═══════════════════════════════════════════");

                    mainHandler.post(() -> {
                        fingerprintInstruction.setText("Looking up user...");
                    });

                    // Look up user directly using the scanner ID we already have
                    if (scannedScannerID >= 10000) {
                        // Synced fingerprint from API
                        DatabaseHelper.SyncedFingerprint syncedUser = dbHelper.getUserByScannerId(scannedScannerID);

                        if (syncedUser != null) {
                            // CRITICAL: Check if synced user is actually an admin
                            String syncedRole = syncedUser.getRole();
                            boolean isAdmin = "ADMIN".equals(syncedRole);

                            if (isAdmin) {
                                Log.i(TAG, "✓ Synced admin identified: " + syncedUser.getName() + " (Role: " + syncedRole + ")");
                                handleAdminVerified(syncedUser.getName(), dialog);
                            } else {
                                Log.w(TAG, "Non-admin attempted access: " + syncedUser.getName() + " (Role: " + syncedRole + ")");
                                handleNonAdminAccess(syncedUser.getName(), dialog);
                            }
                        } else {
                            Log.w(TAG, "Scanner ID found but not in database: " + scannedScannerID);
                            handleFingerprintNotFound(dialog);
                        }
                    } else {
                        // Local user - check in local database
                        DatabaseHelper.User localUser = dbHelper.getUserByScannerUserId(scannedScannerID);

                        if (localUser != null) {
                            if (localUser.isAdmin()) {
                                Log.i(TAG, "✓ Local admin identified: " + localUser.getName());
                                handleAdminVerified(localUser.getName(), dialog);
                            } else {
                                Log.w(TAG, "Non-admin attempted access: " + localUser.getName());
                                handleNonAdminAccess(localUser.getName(), dialog);
                            }
                        } else {
                            Log.w(TAG, "Scanner ID found but not in database: " + scannedScannerID);
                            handleFingerprintNotFound(dialog);
                        }
                    }

                } else {
                    // Identification failed
                    Log.e(TAG, "╔════════════════════════════════════════");
                    Log.e(TAG, "║ FINGERPRINT IDENTIFICATION FAILED");
                    Log.e(TAG, "╠════════════════════════════════════════");
                    Log.e(TAG, "║ Result: " + ret);
                    Log.e(TAG, "║ Scanner ID: " + userID[0] + " (0 = no match)");
                    Log.e(TAG, "║ Quality Score: " + imageQuality[0]);
                    Log.e(TAG, "╚════════════════════════════════════════");

                    final int finalQuality = imageQuality[0];
                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        isScanning = false;

                        String message = "❌ Fingerprint not recognized\n\n";
                        if (finalQuality < 60) {
                            message += "Quality: " + finalQuality + " (Low)\n" +
                                      "• Clean your finger\n• Press firmly\n• Try again";
                        } else {
                            message += "Quality: " + finalQuality + " (OK)\n" +
                                      "• Use enrolled finger\n• Try again";
                        }

                        fingerprintInstruction.setText(message);
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
                        showToast("Fingerprint not recognized (Quality: " + finalQuality + ")");
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during admin verification", e);
                e.printStackTrace();

                mainHandler.post(() -> {
                    fingerprintProgress.setVisibility(View.GONE);
                    isScanning = false;

                    fingerprintInstruction.setText("Error: " + e.getMessage());
                    fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
                    showToast("Error during fingerprint scan");
                });
            }
        });

        dialog.show();
    }

    /**
     * Verify fingerprint using SCANNER-BASED IDENTIFICATION
     * Uses UF_IdentifyTemplate to match against enrolled fingerprints in scanner memory
     */
    private void verifyFingerprintWithApi(byte[] fingerprintTemplate, AlertDialog dialog) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ SCANNER-BASED ADMIN VERIFICATION");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Identifying fingerprint using scanner...");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // Use scanner to identify fingerprint
        executor.execute(() -> {
            // Cancel any previous operations to ensure scanner is ready
            sdk.UF_Cancel(false);
            Log.d(TAG, "UF_Cancel called before UF_IdentifyTemplate");

            int[] userID = new int[1];
            byte[] subID = new byte[1];

            UF_RET_CODE ret = sdk.UF_IdentifyTemplate(fingerprintTemplate.length, fingerprintTemplate, userID, subID);

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                int scannerId = userID[0];
                Log.d(TAG, "✓ Scanner identified fingerprint, Scanner ID: " + scannerId);

                // Check if synced fingerprint (ID >= 10000) or local user
                if (scannerId >= 10000) {
                    // Synced fingerprint from API
                    DatabaseHelper.SyncedFingerprint syncedUser = dbHelper.getUserByScannerId(scannerId);

                    // DEBUG: Check database state
                    Log.d(TAG, "DEBUG - Database query result: " + (syncedUser == null ? "NULL" : syncedUser.toString()));
                    int totalSynced = dbHelper.getSyncedFingerprintCount();
                    Log.d(TAG, "DEBUG - Total synced fingerprints in DB: " + totalSynced);
                    int totalEnrolled = dbHelper.getEnrolledSyncedFingerprintCount();
                    Log.d(TAG, "DEBUG - Total enrolled synced fingerprints: " + totalEnrolled);

                    if (syncedUser != null) {
                        Log.i(TAG, "✓ Synced admin identified: " + syncedUser.getName());
                        handleAdminVerified(syncedUser.getName(), dialog);
                    } else {
                        Log.w(TAG, "Scanner ID found but not in database: " + scannerId);
                        handleFingerprintNotFound(dialog);
                    }
                } else {
                    // Local user - check in local database
                    DatabaseHelper.User localUser = dbHelper.getUserByScannerUserId(scannerId);

                    if (localUser != null) {
                        if (localUser.isAdmin()) {
                            Log.i(TAG, "✓ Local admin identified: " + localUser.getName());
                            handleAdminVerified(localUser.getName(), dialog);
                        } else {
                            Log.w(TAG, "Non-admin attempted access: " + localUser.getName());
                            handleNonAdminAccess(localUser.getName(), dialog);
                        }
                    } else {
                        Log.w(TAG, "Scanner ID found but not in database: " + scannerId);
                        handleFingerprintNotFound(dialog);
                    }
                }
            } else {
                Log.w(TAG, "Scanner could not identify fingerprint: " + ret);
                handleFingerprintNotFound(dialog);
            }
        });
    }

    private void handleAdminVerified(String name, AlertDialog dialog) {
        runOnUiThread(() -> {
            View dialogView = dialog.findViewById(android.R.id.content);
            ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
            TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

            fingerprintProgress.setVisibility(View.GONE);
            isScanning = false;

            fingerprintInstruction.setText("✓ Admin Verified!\nWelcome, " + name);
            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_green_light));

            // Update icon to approved
            android.widget.ImageView fingerprintIcon = dialogView.findViewById(R.id.fingerprintIcon);
            if (fingerprintIcon != null) {
                fingerprintIcon.setImageResource(R.drawable.fingerprint_approved);
            }

            // Delay to show success message (GEMS Original - no enrollment)
            new Handler().postDelayed(() -> {
                dialog.dismiss();
                showToast("Welcome, " + name);
                // Stay on MainMenu - no enrollment in GEMS Original
            }, 1000);
        });
    }

    private void handleNonAdminAccess(String name, AlertDialog dialog) {
        runOnUiThread(() -> {
            View dialogView = dialog.findViewById(android.R.id.content);
            ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
            TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

            fingerprintProgress.setVisibility(View.GONE);
            isScanning = false;

            fingerprintInstruction.setText("❌ Access Denied\n\nOnly admins can access enrollment.\nPlease ask an admin to verify.");
            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            showToast("Only admins can access fingerprint enrollment");

            android.widget.ImageView fingerprintIcon = dialogView.findViewById(R.id.fingerprintIcon);
            if (fingerprintIcon != null) {
                fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
            }
        });
    }

    private void handleFingerprintNotFound(AlertDialog dialog) {
        runOnUiThread(() -> {
            View dialogView = dialog.findViewById(android.R.id.content);
            ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
            TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

            fingerprintProgress.setVisibility(View.GONE);
            isScanning = false;

            fingerprintInstruction.setText("❌ Fingerprint not recognized\n\nPlease try again or contact administrator.");
            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            showToast("Fingerprint not registered");

            android.widget.ImageView fingerprintIcon = dialogView.findViewById(R.id.fingerprintIcon);
            if (fingerprintIcon != null) {
                fingerprintIcon.setImageResource(R.drawable.fingerprint_rejected);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        // Clean up executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        // Cancel any ongoing scan
        if (sdk != null && isScanning) {
            try {
                sdk.UF_Cancel(false);
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling scan", e);
            }
        }

        // Close the scanner port - MainMenuActivity manages the scanner lifecycle
        if (sdk != null) {
            try {
                sdk.UF_CloseCommPort();
                Log.d(TAG, "Scanner port closed in MainMenuActivity onDestroy");
            } catch (Exception e) {
                Log.e(TAG, "Error closing scanner port", e);
            }
        }

        // Close database
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}