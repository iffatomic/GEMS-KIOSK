/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk_android.data.model.response.ControllerProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileListItem;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;
import com.supremainc.sfm_sdk_android.network.api.ControllerProfileApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.ManualOverrideCallback;
import com.supremainc.sfm_sdk_android.service.ManualOverrideService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Vault Status Activity
 * Shows the status of all entry points (Main Grill, Compartments 1-3)
 * Allows deactivation of active overrides with custodian fingerprint verification
 */
public class MainVaultStatusActivity extends AppCompatActivity {

    private static final String TAG = "MainVaultStatusActivity";
    protected static final String VAULT_TYPE = "MAIN";  // Override in DayVaultStatusActivity

    // Toolbar
    private ImageButton backButton;
    private TextView toolbarTitle;
    private Button btnRefresh;

    // Info Card
    private TextView tvVaultTitle;
    private TextView tvOverrideCount;

    // Dynamic Door Cards Container
    private android.widget.LinearLayout doorCardsContainer;
    private ProgressBar progressLoadingDoors;

    // Database and SDK
    private DatabaseHelper dbHelper;
    private SFM_SDK_ANDROID sdk;
    private boolean isScanning = false;

    // Background task management
    private ExecutorService executor;
    private Handler mainHandler;

    // API services
    private ControllerProfileApiClient controllerProfileApiClient;
    private ManualOverrideService manualOverrideService;

    // Cache of controller profiles for this vault
    private List<ControllerProfileResponse> vaultProfiles = new ArrayList<>();

    // Cache of active override profiles from API (profileId -> ManualOverrideProfileResponse)
    private Map<String, ManualOverrideProfileResponse> activeOverrides = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault_status);

        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        controllerProfileApiClient = new ControllerProfileApiClient(this);
        manualOverrideService = new ManualOverrideService(this);

        initializeSDK();
        initializeViews();
        setupListeners();
        loadDoorsFromApi();
    }

    protected String getVaultType() {
        return VAULT_TYPE;
    }

    protected String getVaultDisplayName() {
        return "Main Vault";
    }

    private void initializeSDK() {
        try {
            sdk = new SFM_SDK_ANDROID();
            sdk.UF_InitSysParameter();

            try {
                sdk.UF_CloseCommPort();
                Thread.sleep(500);
            } catch (Exception e) {
                Log.d(TAG, "No existing connection to close");
            }

            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);
            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Thread.sleep(300);
                sdk.UF_Reconnect();
                Log.d(TAG, "Scanner ready");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
        }
    }

    private void initializeViews() {
        // Toolbar
        backButton = findViewById(R.id.back_button);
        toolbarTitle = findViewById(R.id.toolbar_title);
        btnRefresh = findViewById(R.id.btnRefresh);

        // Info Card
        tvVaultTitle = findViewById(R.id.tvVaultTitle);
        tvOverrideCount = findViewById(R.id.tvOverrideCount);

        // Dynamic door cards container
        doorCardsContainer = findViewById(R.id.doorCardsContainer);
        progressLoadingDoors = findViewById(R.id.progressLoadingDoors);

        // Set title
        toolbarTitle.setText(getVaultDisplayName() + " Status");
        tvVaultTitle.setText(getVaultDisplayName());
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> {
            loadDoorsFromApi();
        });
    }

    // =================== LOAD DOORS FROM API ===================

    /**
     * Load doors for this vault from ControllerProfile API
     */
    private void loadDoorsFromApi() {
        Log.d(TAG, "Loading doors for " + getVaultDisplayName() + " from API...");
        progressLoadingDoors.setVisibility(View.VISIBLE);
        doorCardsContainer.removeAllViews();

        controllerProfileApiClient.getAllControllerProfiles(new ApiCallback<List<ControllerProfileResponse>>() {
            @Override
            public void onSuccess(List<ControllerProfileResponse> profiles) {
                Log.i(TAG, "Successfully loaded " + profiles.size() + " controller profiles");

                // Filter profiles for this vault based on variable name prefix
                // Main Vault: MAIN.SOFT_LOCK_*
                // Day Vault: DAY.SOFT_LOCK_*
                vaultProfiles.clear();
                String variablePrefix = getVaultType() + ".SOFT_LOCK_";

                for (ControllerProfileResponse profile : profiles) {
                    String variableName = profile.getControllerVariableName();
                    if (variableName != null && variableName.startsWith(variablePrefix)) {
                        vaultProfiles.add(profile);
                    }
                }

                Log.d(TAG, "Found " + vaultProfiles.size() + " doors for " + getVaultDisplayName() + " (filter: " + variablePrefix + "*)");

                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;

                    if (vaultProfiles.isEmpty()) {
                        progressLoadingDoors.setVisibility(View.GONE);
                        Toast.makeText(MainVaultStatusActivity.this,
                                "No doors found for " + getVaultDisplayName(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Load active overrides from API, then create door cards
                        loadActiveOverridesFromApi();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading controller profiles: " + error);
                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;

                    progressLoadingDoors.setVisibility(View.GONE);
                    Toast.makeText(MainVaultStatusActivity.this,
                            "Failed to load doors: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Load valid manual override profiles from PAC API
     * This fetches Pending and Active profiles (excludes Deactivated and Expired)
     */
    private void loadActiveOverridesFromApi() {
        Log.d(TAG, "Loading valid override profiles from PAC API...");

        manualOverrideService.getAllProfilesList(new ApiCallback<List<ManualOverrideProfileListItem>>() {
            @Override
            public void onSuccess(List<ManualOverrideProfileListItem> profiles) {
                Log.i(TAG, "Successfully loaded " + profiles.size() + " manual override profiles from API");

                // Clear existing overrides
                activeOverrides.clear();

                // Process each profile and build the activeOverrides map
                // Match by vaultName instead of variableName (since backend doesn't send variableName)
                for (ManualOverrideProfileListItem profile : profiles) {
                    // Include Pending (0) and Active (1) profiles
                    // Exclude Deactivated (2) and Expired (3)
                    String status = profile.getStatus();
                    boolean isValidProfile = "0".equals(status) || "Pending".equalsIgnoreCase(status) ||
                                             "1".equals(status) || "Active".equalsIgnoreCase(status);

                    if (isValidProfile) {
                        String vaultName = profile.getVaultName();

                        // Only include profiles with vaultName
                        if (vaultName != null && !vaultName.isEmpty()) {
                            // Convert ListItem to full ProfileResponse for compatibility
                            ManualOverrideProfileResponse overrideResponse = new ManualOverrideProfileResponse();
                            overrideResponse.setProfileId(profile.getId());
                            overrideResponse.setDoorName(vaultName);
                            overrideResponse.setCustodian1(profile.getCustodian1());
                            overrideResponse.setCustodian2(profile.getCustodian2());
                            overrideResponse.setCustodian3(profile.getCustodian3());
                            overrideResponse.setStatus(status);
                            overrideResponse.setActivatedAt(profile.getActivatedAt());

                            // Use vaultName as key for matching
                            activeOverrides.put(vaultName, overrideResponse);
                            Log.d(TAG, "Added valid override: " + vaultName + " (Profile ID: " + profile.getId() + ", Status: " + status + ")");
                        }
                    }
                }

                Log.i(TAG, "Found " + activeOverrides.size() + " valid override(s) for " + getVaultDisplayName());

                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    createDoorCards();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading active overrides: " + error);
                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;

                    // Still create door cards even if override loading fails
                    // They'll just show as locked
                    Toast.makeText(MainVaultStatusActivity.this,
                            "Warning: Could not load active overrides",
                            Toast.LENGTH_SHORT).show();
                    createDoorCards();
                });
            }
        });
    }

    /**
     * Create dynamic door cards for each controller profile
     */
    private void createDoorCards() {
        doorCardsContainer.removeAllViews();
        progressLoadingDoors.setVisibility(View.GONE);

        Log.d(TAG, "Creating " + vaultProfiles.size() + " door cards");

        int overrideCount = 0;

        for (ControllerProfileResponse profile : vaultProfiles) {
            MaterialCardView doorCard = createDoorCard(profile);
            doorCardsContainer.addView(doorCard);

            // Count if this door has an active override (match by vaultName)
            if (activeOverrides.containsKey(profile.getVaultName())) {
                overrideCount++;
            }
        }

        // Update override count
        final int finalOverrideCount = overrideCount;
        tvOverrideCount.setText(finalOverrideCount + " active override" + (finalOverrideCount != 1 ? "s" : ""));
    }

    /**
     * Create a single door card
     */
    private MaterialCardView createDoorCard(ControllerProfileResponse profile) {
        MaterialCardView card = new MaterialCardView(this);

        // Set layout parameters
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        card.setLayoutParams(params);

        // Card styling
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(4 * getResources().getDisplayMetrics().density);
        card.setClickable(true);
        card.setFocusable(true);

        // Check if this door has an active override (match by vaultName)
        ManualOverrideProfileResponse override = activeOverrides.get(profile.getVaultName());
        boolean hasOverride = override != null;

        // Create card content
        android.widget.LinearLayout cardContent = new android.widget.LinearLayout(this);
        cardContent.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        cardContent.setGravity(android.view.Gravity.CENTER_VERTICAL);
        cardContent.setPadding(
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density)
        );

        // Left side - Text content
        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(this);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        textContainer.setLayoutParams(textParams);

        // Door name (use vaultName which contains "Vault Door A", "Vault Door B", etc.)
        TextView doorName = new TextView(this);
        String displayName = profile.getVaultName() != null && !profile.getVaultName().isEmpty()
                ? profile.getVaultName()
                : profile.getControllerVariableName();
        doorName.setText(displayName);
        doorName.setTextSize(18);
        doorName.setTextColor(getResources().getColor(R.color.text_primary));
        doorName.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(doorName);

        // Door status
        TextView doorStatus = new TextView(this);
        android.widget.LinearLayout.LayoutParams statusParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        doorStatus.setLayoutParams(statusParams);

        if (hasOverride) {
            String status = override.getStatus();
            boolean isPending = "0".equals(status) || "Pending".equalsIgnoreCase(status);

            if (isPending) {
                doorStatus.setText("OVERRIDE PENDING\nProfile: " + override.getProfileId() + "\nWaiting for key switches...");
                doorStatus.setTextColor(getResources().getColor(R.color.holo_orange_dark));
                card.setStrokeColor(getResources().getColor(R.color.holo_orange_dark));
                card.setStrokeWidth((int) (4 * getResources().getDisplayMetrics().density));
            } else {
                doorStatus.setText("OVERRIDE ACTIVE\nProfile: " + override.getProfileId());
                doorStatus.setTextColor(getResources().getColor(R.color.holo_green_dark));
                card.setStrokeColor(getResources().getColor(R.color.holo_green_dark));
                card.setStrokeWidth((int) (4 * getResources().getDisplayMetrics().density));
            }
        } else {
            doorStatus.setText("Locked");
            doorStatus.setTextColor(getResources().getColor(R.color.textSecondary));
            card.setStrokeColor(getResources().getColor(R.color.colorPrimary));
            card.setStrokeWidth(0);
        }

        doorStatus.setTextSize(14);
        textContainer.addView(doorStatus);

        // Right side - Lock icon
        ImageView lockIcon = new ImageView(this);
        lockIcon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                (int) (40 * getResources().getDisplayMetrics().density),
                (int) (40 * getResources().getDisplayMetrics().density)
        ));

        if (hasOverride) {
            String status = override.getStatus();
            boolean isPending = "0".equals(status) || "Pending".equalsIgnoreCase(status);

            if (isPending) {
                lockIcon.setImageResource(R.drawable.ic_lock);  // Locked icon for pending
                lockIcon.setColorFilter(getResources().getColor(R.color.holo_orange_dark));
            } else {
                lockIcon.setImageResource(R.drawable.ic_lock_open);  // Unlocked icon for active
                lockIcon.setColorFilter(getResources().getColor(R.color.holo_green_dark));
            }
        } else {
            lockIcon.setImageResource(R.drawable.ic_lock);
            lockIcon.setColorFilter(getResources().getColor(R.color.textSecondary));
        }

        // Add views to card
        cardContent.addView(textContainer);
        cardContent.addView(lockIcon);
        card.addView(cardContent);

        // Set click listener
        card.setOnClickListener(v -> handleDoorClick(profile, override, displayName));

        return card;
    }

    /**
     * Handle door card click
     */
    private void handleDoorClick(ControllerProfileResponse profile, ManualOverrideProfileResponse override, String displayName) {
        if (override != null) {
            // Override is active - show deactivation dialog
            showDeactivationConfirmDialog(override, displayName);
        } else {
            // No override - just show info
            Toast.makeText(this, displayName + " is locked (no override active)", Toast.LENGTH_SHORT).show();
        }
    }

    // =================== DEACTIVATION FLOW ===================

    private void showDeactivationConfirmDialog(ManualOverrideProfileResponse override, String displayName) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate Override")
                .setMessage("Are you sure you want to deactivate the override for:\n\n" +
                        getVaultDisplayName() + " - " + displayName + "\n\n" +
                        "Profile ID: " + override.getProfileId())
                .setPositiveButton("Deactivate", (dialog, which) -> {
                    // GEMS Original: No fingerprint verification - deactivate immediately
                    deactivateOverrideDirectly(override);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Deactivate override directly without fingerprint verification (GEMS Original)
     */
    private void deactivateOverrideDirectly(ManualOverrideProfileResponse override) {
        String profileId = override.getProfileId();

        if (profileId == null || profileId.isEmpty()) {
            Toast.makeText(this, "Cannot deactivate: No profile ID", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Deactivating override without fingerprint verification...");
        Log.d(TAG, "Profile ID: " + profileId);

        // Show progress dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Deactivating override...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        manualOverrideService.deactivateProfile(profileId, new ManualOverrideCallback() {
            @Override
            public void onProfileDeactivated(ManualOverrideProfileResponse response) {
                Log.i(TAG, "✓ Override deactivated successfully");
                Log.i(TAG, "Profile ID: " + response.getProfileId());
                Log.i(TAG, "Status: " + response.getStatus());

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainVaultStatusActivity.this,
                        "Override deactivated successfully!",
                        Toast.LENGTH_LONG).show();

                    // Reload data from API
                    loadDoorsFromApi();
                });
            }

            @Override
            public void onProfileError(String error) {
                Log.e(TAG, "✗ Failed to deactivate override: " + error);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainVaultStatusActivity.this,
                        "Failed to deactivate: " + error,
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProfileCreated(ManualOverrideProfileResponse response) {
                // Not used
            }
        });
    }

    private void showCustodianVerificationDialog(ManualOverrideProfileResponse override) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fingerprint_scanner, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
        TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);
        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);

        if (dialogTitle != null) {
            dialogTitle.setText("Custodian Verification");
        }
        fingerprintInstruction.setText("Scan a custodian's fingerprint to deactivate override:\n\n" +
                "Profile ID: " + override.getProfileId() + "\n" +
                "Door: " + override.getDoorName());

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

        dialog.setOnShowListener(dialogInterface -> {
            // Start scanning automatically when dialog shows
            performCustodianVerification(override, dialog, fingerprintProgress, fingerprintInstruction);
        });

        dialog.show();
    }

    private void performCustodianVerification(ManualOverrideProfileResponse override, AlertDialog dialog,
                                               ProgressBar progressBar, TextView instructionText) {
        if (isScanning) return;

        isScanning = true;
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                sdk.UF_Reconnect();

                mainHandler.post(() -> instructionText.setText("Scanning fingerprint..."));

                int[] userID = new int[1];
                byte[] subID = new byte[1];

                UF_RET_CODE ret = sdk.UF_Identify(userID, subID);

                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    int scannedScannerID = userID[0];
                    Log.d(TAG, "Fingerprint identified, Scanner ID: " + scannedScannerID);

                    // Check both synced fingerprints (ID >= 10000) and local users (ID < 10000)
                    // This matches the activation logic
                    boolean isCustodian = false;
                    boolean isAdmin = false;
                    String userName = null;
                    String userStaffId = null;

                    if (scannedScannerID >= 10000) {
                        // Synced fingerprint from API
                        Log.d(TAG, "Checking synced fingerprints table for scanner ID: " + scannedScannerID);
                        DatabaseHelper.SyncedFingerprint syncedUser = dbHelper.getUserByScannerId(scannedScannerID);

                        if (syncedUser != null) {
                            userName = syncedUser.getName();
                            userStaffId = syncedUser.getEmployeeNumber();
                            String syncedRole = syncedUser.getRole();
                            isCustodian = "CUSTODIAN".equals(syncedRole);
                            isAdmin = "ADMIN".equals(syncedRole);
                            Log.i(TAG, "Synced user identified: " + userName + ", role: " + syncedRole);
                        }
                    } else {
                        // Local user
                        Log.d(TAG, "Checking local users table for scanner ID: " + scannedScannerID);
                        DatabaseHelper.User localUser = dbHelper.getUserByScannerUserId(scannedScannerID);

                        if (localUser != null) {
                            userName = localUser.getName();
                            userStaffId = localUser.getStaffId();
                            isAdmin = localUser.isAdmin();
                            isCustodian = !localUser.isAdmin();
                            Log.i(TAG, "Local user identified: " + userName + ", isAdmin: " + isAdmin);
                        }
                    }

                    final String finalUserName = userName;
                    final String finalUserStaffId = userStaffId;
                    final boolean finalIsAdmin = isAdmin;
                    final boolean finalIsCustodian = isCustodian;

                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;

                        if (finalUserName != null && (finalIsAdmin || finalIsCustodian)) {
                                // API-FIRST APPROACH: Call PAC API
                                String profileId = override.getProfileId();
                                Log.d(TAG, "╔═══════════════════════════════════════════");
                                Log.d(TAG, "║ API-FIRST DEACTIVATION");
                                Log.d(TAG, "╠═══════════════════════════════════════════");
                                Log.d(TAG, "║ Profile ID: " + profileId);
                                Log.d(TAG, "║ Door: " + override.getDoorName());
                                Log.d(TAG, "║ Deactivated by: " + finalUserName + " (" + finalUserStaffId + ")");
                                Log.d(TAG, "║ User type: " + (finalIsAdmin ? "Admin" : "Custodian"));

                                // Must have a profile ID to deactivate via API
                                if (profileId != null && !profileId.isEmpty()) {
                                    Log.d(TAG, "║ Calling PAC API...");
                                    Log.d(TAG, "╚═══════════════════════════════════════════");

                                    instructionText.setText("Deactivating override via PAC API...");
                                    progressBar.setVisibility(View.VISIBLE);

                                    manualOverrideService.deactivateProfile(profileId, new ManualOverrideCallback() {
                                        @Override
                                        public void onProfileCreated(ManualOverrideProfileResponse response) {
                                            // Not used
                                            Log.w(TAG, "onProfileCreated called during deactivation - unexpected");
                                        }

                                        @Override
                                        public void onProfileDeactivated(ManualOverrideProfileResponse response) {
                                            Log.i(TAG, "╔═══════════════════════════════════════════");
                                            Log.i(TAG, "║ ✓ PAC API DEACTIVATION SUCCESS");
                                            Log.i(TAG, "╠═══════════════════════════════════════════");
                                            Log.i(TAG, "║ Profile ID: " + response.getProfileId());
                                            Log.i(TAG, "║ Status: " + response.getStatus());
                                            Log.i(TAG, "╚═══════════════════════════════════════════");

                                            runOnUiThread(() -> {
                                                progressBar.setVisibility(View.GONE);

                                                // Show success based on API response
                                                instructionText.setText("✓ Override deactivated successfully by:\n" + finalUserName +
                                                                      (finalIsAdmin ? " (Admin)" : " (Custodian)"));
                                                instructionText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                                                Toast.makeText(MainVaultStatusActivity.this, "Override deactivated successfully!", Toast.LENGTH_LONG).show();

                                                // Close dialog and refresh after delay
                                                new Handler().postDelayed(() -> {
                                                    dialog.dismiss();
                                                    loadDoorsFromApi(); // Reload from API
                                                }, 1500);
                                            });
                                        }

                                        @Override
                                        public void onProfileError(String error) {
                                            Log.e(TAG, "╔═══════════════════════════════════════════");
                                            Log.e(TAG, "║ ✗ PAC API DEACTIVATION FAILED");
                                            Log.e(TAG, "╠═══════════════════════════════════════════");
                                            Log.e(TAG, "║ Error: " + error);
                                            Log.e(TAG, "║ Profile ID attempted: " + profileId);
                                            Log.e(TAG, "╚═══════════════════════════════════════════");

                                            // Show failure based on API response
                                            runOnUiThread(() -> {
                                                progressBar.setVisibility(View.GONE);
                                                instructionText.setText("PAC API deactivation failed:\n" + error);
                                                instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                                Toast.makeText(MainVaultStatusActivity.this, "Failed to deactivate: " + error, Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    });
                                } else {
                                    Log.e(TAG, "║ ERROR: No profile ID found - cannot deactivate!");
                                    Log.d(TAG, "╚═══════════════════════════════════════════");
                                    instructionText.setText("Error: No profile ID.\nCannot deactivate.");
                                    instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                    Toast.makeText(MainVaultStatusActivity.this, "Cannot deactivate: No profile ID", Toast.LENGTH_LONG).show();
                                }
                        } else {
                            instructionText.setText("Fingerprint not recognized.\nPlease try again.");
                            instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;
                        instructionText.setText("Fingerprint not recognized.\nPlease try again.");
                        instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during verification", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isScanning = false;
                    instructionText.setText("Error: " + e.getMessage());
                    instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDoorsFromApi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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

        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
