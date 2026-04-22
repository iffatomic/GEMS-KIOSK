/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import com.supremainc.sfm_sdk_android.data.model.response.VaultInfo;
import com.supremainc.sfm_sdk_android.network.api.ControllerProfileApiClient;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
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
    private FingerprintDownloadApiClient fingerprintDownloadApiClient;

    // Cache of controller profiles for this vault
    private List<ControllerProfileResponse> vaultProfiles = new ArrayList<>();

    // Cache of active override profiles from API (variableName -> ManualOverrideProfileResponse)
    // Key is controllerVariableName (e.g., "MAIN.SOFT_LOCK_A") for stable matching even when door names change
    private Map<String, ManualOverrideProfileResponse> activeOverrides = new HashMap<>();

    // SignalR service for real-time key switch monitoring
    private SignalRService signalRService;
    private boolean signalRConnected = false;

    // Key switch state tracking (doorId -> KeySwitchState)
    private Map<Integer, KeySwitchState> keySwitchStates = new HashMap<>();

    /**
     * Helper class to track key switch states for a door
     */
    private static class KeySwitchState {
        boolean switch1On = false;
        boolean switch2On = false;
        boolean switch3On = false;

        boolean allOn() {
            return switch1On && switch2On && switch3On;
        }

        /**
         * Get colored indicator for key switches
         * Green circles for ON, red circles for OFF
         */
        CharSequence getIndicator() {
            SpannableStringBuilder builder = new SpannableStringBuilder();

            // Add "Key Status" label
            String label = "Key Status: ";
            SpannableString labelSpan = new SpannableString(label);
            labelSpan.setSpan(new ForegroundColorSpan(Color.parseColor("#9E9E9E")),
                         0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(labelSpan);

            // Switch 1
            String circle1 = "● ";
            SpannableString span1 = new SpannableString(circle1);
            span1.setSpan(new ForegroundColorSpan(switch1On ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")),
                         0, circle1.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(span1);

            // Switch 2
            String circle2 = "● ";
            SpannableString span2 = new SpannableString(circle2);
            span2.setSpan(new ForegroundColorSpan(switch2On ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")),
                         0, circle2.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(span2);

            // Switch 3
            String circle3 = "●";
            SpannableString span3 = new SpannableString(circle3);
            span3.setSpan(new ForegroundColorSpan(switch3On ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")),
                         0, circle3.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(span3);

            return builder;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault_status);

        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        controllerProfileApiClient = new ControllerProfileApiClient(this);
        manualOverrideService = new ManualOverrideService(this);
        fingerprintDownloadApiClient = new FingerprintDownloadApiClient(this);

        initializeSDK();
        initializeSignalR();
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

    /**
     * Initialize SignalR connection for real-time key switch monitoring
     */
    private void initializeSignalR() {
        Log.d(TAG, "Initializing SignalR for key switch monitoring...");

        signalRService = new SignalRService(this);
        signalRService.initialize(new SignalRService.SignalREventListener() {
            @Override
            public void onKeySwitchChanged(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto event) {
                handleKeySwitchEvent(event);
            }

            @Override
            public void onKeySwitchAggregate(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto event) {
                handleKeySwitchAggregateEvent(event);
            }

            @Override
            public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {
                // Not used in this activity
            }

            @Override
            public void onDoorStateChanged(SignalRService.DoorStateData event) {
                // Not used in this activity
            }

            @Override
            public void onVaultIncident(SignalRService.VaultIncidentData incident) {
                // Not used in this activity
            }

            @Override
            public void onVaultIncidentBroadcast(com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto incident) {
                // Not used in this activity
            }

            @Override
            public void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {
                // When cutoff is reached, reload the vault status to reflect deactivated overrides
                mainHandler.post(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        loadDoorsFromApi();
                    }
                });
            }

            @Override
            public void onFingerprintEnrollmentCompleted(com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto event) {
                // Not used in this activity
            }

            @Override
            public void onKioskPatchReady() {
                // Handled by KioskUpdateService
            }

            @Override
            public void onConnectionClosed() {
                Log.w(TAG, "SignalR connection closed");
                signalRConnected = false;
            }
        });

        // Start connection
        signalRService.start(new SignalRService.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "✓ SignalR connected for key switch monitoring");
                signalRConnected = true;
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "✗ SignalR connection failed: " + errorMessage);
                signalRConnected = false;
            }
        });
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
        toolbarTitle.setText("Vault Status");
        tvVaultTitle.setText("Vault Status");
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

                // Load all profiles — they are grouped by vaultCategory in createDoorCards()
                vaultProfiles.clear();
                vaultProfiles.addAll(profiles);

                Log.d(TAG, "Loaded " + vaultProfiles.size() + " total controller profiles");

                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;

                    if (vaultProfiles.isEmpty()) {
                        progressLoadingDoors.setVisibility(View.GONE);
                        Toast.makeText(MainVaultStatusActivity.this,
                                "No vault doors found",
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
                // Match by variableName if available, otherwise match by vault/door name to controller profiles
                for (ManualOverrideProfileListItem profile : profiles) {
                    // Include Pending (0) and Active (1) profiles
                    // Exclude Deactivated (2) and Expired (3)
                    String status = profile.getStatus();
                    boolean isValidProfile = "0".equals(status) || "Pending".equalsIgnoreCase(status) ||
                                             "1".equals(status) || "Active".equalsIgnoreCase(status);

                    if (isValidProfile) {
                        String variableName = profile.getVariableName();
                        String vaultName = profile.getVaultName();

                        // If API returns variableName, use it directly
                        if (variableName != null && !variableName.isEmpty()) {
                            Log.d(TAG, "API provided variableName: " + variableName + " for vaultName: " + vaultName);
                        } else {
                            // API didn't provide variableName - match by door/vault name to controller profiles
                            // This handles names like "G-009" that don't follow "Vault Door A" pattern
                            variableName = matchVaultNameToControllerVariable(vaultName);
                            Log.d(TAG, "API missing variableName, matched by name: " + variableName + " from vaultName: " + vaultName);
                        }

                        // Only include profiles with identifiable variableName
                        if (variableName != null && !variableName.isEmpty()) {
                            // Convert ListItem to full ProfileResponse for compatibility
                            ManualOverrideProfileResponse overrideResponse = new ManualOverrideProfileResponse();
                            overrideResponse.setProfileId(profile.getId());
                            overrideResponse.setVariableName(variableName);
                            overrideResponse.setDoorName(vaultName);  // For display purposes
                            overrideResponse.setCustodian1(profile.getCustodian1());
                            overrideResponse.setCustodian2(profile.getCustodian2());
                            overrideResponse.setCustodian3(profile.getCustodian3());
                            overrideResponse.setStatus(status);
                            overrideResponse.setActivatedAt(profile.getActivatedAt());

                            // Use variableName as key for stable matching (e.g., "MAIN.SOFT_LOCK_A")
                            activeOverrides.put(variableName, overrideResponse);
                            Log.d(TAG, "Added valid override: " + variableName + " → " + vaultName + " (Profile ID: " + profile.getId() + ", Status: " + status + ")");
                        } else {
                            Log.w(TAG, "Could not match override profile to any controller - vaultName: " + vaultName);
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
     * Create dynamic door cards grouped by vault category,
     * each group wrapped in a collapsible section header.
     */
    private void createDoorCards() {
        doorCardsContainer.removeAllViews();
        progressLoadingDoors.setVisibility(View.GONE);

        Log.d(TAG, "Creating door cards for " + vaultProfiles.size() + " profiles");

        // Group profiles by vaultCategory, preserving insertion order
        java.util.LinkedHashMap<String, List<ControllerProfileResponse>> grouped = new java.util.LinkedHashMap<>();
        for (ControllerProfileResponse profile : vaultProfiles) {
            String category = "Other";
            VaultInfo info = profile.getVaultInfo();
            if (info != null && info.getVaultCategory() != null && !info.getVaultCategory().isEmpty()) {
                category = info.getVaultCategory();
            }
            if (!grouped.containsKey(category)) {
                grouped.put(category, new ArrayList<>());
            }
            grouped.get(category).add(profile);
        }

        int overrideCount = 0;
        for (Map.Entry<String, List<ControllerProfileResponse>> entry : grouped.entrySet()) {
            overrideCount += createCategorySection(entry.getKey(), entry.getValue());
        }

        final int finalOverrideCount = overrideCount;
        tvOverrideCount.setText(finalOverrideCount + " active override" + (finalOverrideCount != 1 ? "s" : ""));
    }

    /**
     * Build a collapsible section for a vault category (e.g. "Main Vault", "Day Vault").
     * The section header is tappable — it collapses/expands the door cards below.
     *
     * @return count of active overrides within this section
     */
    private int createCategorySection(String categoryName, List<ControllerProfileResponse> profiles) {
        float dp = getResources().getDisplayMetrics().density;

        // ── Section header ──────────────────────────────────────────────
        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        header.setBackgroundResource(R.drawable.container_background);
        header.setClickable(true);
        header.setFocusable(true);

        android.widget.LinearLayout.LayoutParams headerParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = (int)(8 * dp);
        header.setLayoutParams(headerParams);

        // Category name
        TextView titleView = new TextView(this);
        android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(titleParams);
        titleView.setText(categoryName);
        titleView.setTextSize(16);
        titleView.setTextColor(getResources().getColor(R.color.textPrimary));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        // Chevron indicator
        TextView chevron = new TextView(this);
        chevron.setText("▼");
        chevron.setTextSize(14);
        chevron.setTextColor(getResources().getColor(R.color.textPrimary));

        header.addView(titleView);
        header.addView(chevron);

        // ── Collapsible content ──────────────────────────────────────────
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setVisibility(View.VISIBLE);  // expanded by default

        android.widget.LinearLayout.LayoutParams contentParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        contentParams.bottomMargin = (int)(16 * dp);
        content.setLayoutParams(contentParams);

        // Populate door cards into content
        int overrideCount = 0;
        for (ControllerProfileResponse profile : profiles) {
            MaterialCardView card = createDoorCard(profile);
            content.addView(card);
            if (activeOverrides.containsKey(profile.getControllerVariableName())) {
                overrideCount++;
            }
        }

        // Toggle collapse / expand on header tap
        header.setOnClickListener(v -> {
            boolean expanded = content.getVisibility() == View.VISIBLE;
            content.setVisibility(expanded ? View.GONE : View.VISIBLE);
            chevron.setText(expanded ? "▶" : "▼");
        });

        doorCardsContainer.addView(header);
        doorCardsContainer.addView(content);

        return overrideCount;
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

        // Check if this door has an active override (match by controllerVariableName for stable matching)
        ManualOverrideProfileResponse override = activeOverrides.get(profile.getControllerVariableName());
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

            // Debug logging to check status value
            Log.d(TAG, "╔════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ Override Status Check for: " + displayName);
            Log.d(TAG, "║ Profile ID: " + override.getProfileId());
            Log.d(TAG, "║ Status: '" + status + "'");
            Log.d(TAG, "║ Variable: " + override.getVariableName());
            Log.d(TAG, "╚════════════════════════════════════════════════════════════");

            // Check if status is pending
            // Status can be: "Pending", "Active", "Completed", "Deactivated", "Expired"
            // Also check for numeric values: "0" = Pending, "1" = Active
            boolean isPending = status == null ||
                                status.isEmpty() ||
                                "0".equals(status) ||
                                "Pending".equalsIgnoreCase(status);

            boolean isActive = "1".equals(status) || "Active".equalsIgnoreCase(status);

            // Extract door ID from variable name
            int doorId = extractDoorIdFromVariableName(override.getVariableName());

            if (isPending) {
                // Get key switch states for this door
                CharSequence keySwitchIndicator = getKeySwitchIndicator(doorId);
                SpannableStringBuilder statusText = new SpannableStringBuilder();
                statusText.append("OVERRIDE PENDING\n");
                statusText.append(keySwitchIndicator);
                statusText.append("\nWaiting for key switches...");
                doorStatus.setText(statusText);
                doorStatus.setTextColor(getResources().getColor(R.color.holo_orange_dark));
                card.setStrokeColor(getResources().getColor(R.color.holo_orange_dark));
                card.setStrokeWidth((int) (4 * getResources().getDisplayMetrics().density));
                Log.d(TAG, "→ Displaying as PENDING");
            } else if (isActive) {
                // Get key switch states for this door (all should be ON if active)
                CharSequence keySwitchIndicator = getKeySwitchIndicator(doorId);
                SpannableStringBuilder statusText = new SpannableStringBuilder();
                statusText.append("OVERRIDE ACTIVE\n");
                statusText.append(keySwitchIndicator);
                doorStatus.setText(statusText);
                doorStatus.setTextColor(getResources().getColor(R.color.holo_green_dark));
                card.setStrokeColor(getResources().getColor(R.color.holo_green_dark));
                card.setStrokeWidth((int) (4 * getResources().getDisplayMetrics().density));
                Log.d(TAG, "→ Displaying as ACTIVE");
            } else {
                // Other statuses (Completed, Deactivated, Expired)
                CharSequence keySwitchIndicator = getKeySwitchIndicator(doorId);
                SpannableStringBuilder statusText = new SpannableStringBuilder();
                statusText.append("OVERRIDE " + status.toUpperCase() + "\n");
                statusText.append(keySwitchIndicator);
                doorStatus.setText(statusText);
                doorStatus.setTextColor(getResources().getColor(R.color.textSecondary));
                card.setStrokeColor(getResources().getColor(R.color.textSecondary));
                card.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
                Log.d(TAG, "→ Displaying as " + status.toUpperCase());
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
            // Override is active - show confirmation dialog first
            showDeactivateConfirmationDialog(override, displayName);
        } else {
            // No override - just show info
            Toast.makeText(this, displayName + " is locked (no override active)", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before fingerprint verification
     */
    private void showDeactivateConfirmationDialog(ManualOverrideProfileResponse override, String displayName) {
        String message = "Do you want to deactivate the override for this door?\n\n" +
                        "Door: " + displayName + "\n" +
                        "Status: " + override.getStatus() + "\n" +
                        "You will need to verify your fingerprint to proceed.";

        new AlertDialog.Builder(this)
            .setTitle("Deactivate Override")
            .setMessage(message)
            .setPositiveButton("Deactivate", (dialog, which) -> {
                // User confirmed - now show fingerprint verification
                showCustodianVerificationDialog(override, displayName);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // =================== DEACTIVATION FLOW ===================

    private void showDeactivationConfirmDialog(ManualOverrideProfileResponse override, String displayName) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate Override")
                .setMessage("Are you sure you want to deactivate the override for:\n\n" +
                        displayName + "\n\n" +
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

                    // Update VaultOverrideManager to remove the override
                    String vaultName = override.getDoorName();
                    if (vaultName != null && !vaultName.isEmpty()) {
                        VaultOverrideManager manager = VaultOverrideManager.getInstance(MainVaultStatusActivity.this);
                        manager.removeOverride(vaultName);
                        Log.d(TAG, "  → VaultOverrideManager updated: removed override for " + vaultName);
                    }

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

    private void showCustodianVerificationDialog(ManualOverrideProfileResponse override, String displayName) {
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
            dialogTitle.setText("Deactivate Override - Fingerprint Verification");
        }
        fingerprintInstruction.setText("Scan fingerprint to deactivate override:\n\n" +
                "Profile ID: " + override.getProfileId() + "\n" +
                "Door: " + displayName + "\n\n" +
                "Note: Custodian role is not authorized to deactivate");

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
            performCustodianVerification(override, displayName, dialog, fingerprintProgress, fingerprintInstruction);
        });

        dialog.show();
    }

    private void performCustodianVerification(ManualOverrideProfileResponse override, String displayName,
                                               AlertDialog dialog, ProgressBar progressBar, TextView instructionText) {
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

                    // Get the actual role string for synced users
                    String userRole = null;
                    if (scannedScannerID >= 10000) {
                        DatabaseHelper.SyncedFingerprint syncedUser = dbHelper.getUserByScannerId(scannedScannerID);
                        if (syncedUser != null) {
                            userRole = syncedUser.getRole();
                        }
                    }

                    final String finalUserName = userName;
                    final String finalUserStaffId = userStaffId;
                    final boolean finalIsAdmin = isAdmin;
                    final boolean finalIsCustodian = isCustodian;
                    final String finalUserRole = userRole;

                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;

                        if (finalUserName != null) {
                            // isAllowedOverride AUTHORIZATION CHECK
                            Log.d(TAG, "╔═══════════════════════════════════════════");
                            Log.d(TAG, "║ DEACTIVATION AUTHORIZATION CHECK");
                            Log.d(TAG, "╠═══════════════════════════════════════════");
                            Log.d(TAG, "║ Employee: " + finalUserName);
                            Log.d(TAG, "║ Staff ID: " + finalUserStaffId);
                            Log.d(TAG, "║ Role: " + (finalUserRole != null ? "'" + finalUserRole + "'" : "N/A (local)"));
                            Log.d(TAG, "║ Checking isAllowedOverride via live API...");

                            if (finalUserStaffId == null) {
                                Log.w(TAG, "║ Authorization: ✗ DENIED (cannot determine staff ID)");
                                Log.d(TAG, "╚═══════════════════════════════════════════");
                                instructionText.setText("❌ Authorization Failed\n\nCould not verify employee identity.");
                                instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                return;
                            }

                            instructionText.setText("Fingerprint matched. Checking permissions...");
                            instructionText.setTextColor(getResources().getColor(android.R.color.white));

                            fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
                                new ApiCallback<com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse>() {
                                    @Override
                                    public void onSuccess(com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse response) {
                                        com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData freshEmployee = null;
                                        if (response.getEmployees() != null) {
                                            for (com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData e : response.getEmployees()) {
                                                if (finalUserStaffId.equalsIgnoreCase(e.getStaffID())) {
                                                    freshEmployee = e;
                                                    break;
                                                }
                                            }
                                        }

                                        if (freshEmployee == null) {
                                            Log.w(TAG, "Employee not found in fresh API response: " + finalUserStaffId);
                                            runOnUiThread(() -> {
                                                instructionText.setText("❌ Employee not found in server data.\nPlease contact administrator.");
                                                instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                            });
                                            return;
                                        }

                                        final boolean liveAllowed = freshEmployee.isAllowedOverride();
                                        Log.d(TAG, "Live override check for " + finalUserStaffId + ": isAllowedOverride=" + liveAllowed);
                                        dbHelper.updateIsAllowedOverrideByEmployeeNumber(finalUserStaffId, liveAllowed);

                                        runOnUiThread(() -> proceedWithDeactivationIfAllowed(
                                            liveAllowed, finalUserName, finalUserStaffId, finalUserRole, finalIsAdmin,
                                            displayName, override, dialog, instructionText, "live API"));
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Log.w(TAG, "Live override check failed, falling back to cached DB value: " + error);
                                        // Cached fallback: read isAllowedOverride from local DB
                                        boolean cachedAllowed = false;
                                        if (scannedScannerID >= 10000) {
                                            DatabaseHelper.SyncedFingerprint sf = dbHelper.getUserByScannerId(scannedScannerID);
                                            if (sf != null) cachedAllowed = sf.isAllowedOverride();
                                        }
                                        final boolean allowed = cachedAllowed;
                                        runOnUiThread(() -> proceedWithDeactivationIfAllowed(
                                            allowed, finalUserName, finalUserStaffId, finalUserRole, finalIsAdmin,
                                            displayName, override, dialog, instructionText, "cached DB"));
                                    }
                                }
                            );
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

    /**
     * Called after isAllowedOverride check (live or cached). Shows a confirmation dialog
     * and proceeds with deactivation if the employee is authorised.
     */
    private void proceedWithDeactivationIfAllowed(
            boolean allowed,
            String userName,
            String userStaffId,
            String userRole,
            boolean isAdmin,
            String displayName,
            ManualOverrideProfileResponse override,
            android.app.Dialog fingerprintDialog,
            TextView instructionText,
            String checkSource) {

        if (!allowed) {
            Log.w(TAG, "║ Authorization: ✗ DENIED (isAllowedOverride=false, source=" + checkSource + ")");
            Log.d(TAG, "╚═══════════════════════════════════════════");
            instructionText.setText("❌ Override Not Permitted\n\nYour account is not authorised to deactivate manual vault override.");
            instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            Toast.makeText(this, "Access Denied: You are not authorised to deactivate override", Toast.LENGTH_LONG).show();
            return;
        }

        Log.i(TAG, "║ Authorization: ✓ GRANTED (isAllowedOverride=true, source=" + checkSource + ")");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        fingerprintDialog.dismiss();

        String roleDisplay = userRole != null ? userRole : (isAdmin ? "Admin" : "User");
        String confirmMessage = "Verified employee:\n\n" +
                                "Name: " + userName + "\n" +
                                "Staff ID: " + userStaffId + "\n" +
                                "Role: " + roleDisplay + "\n\n" +
                                "Door: " + displayName + "\n\n" +
                                "Proceed with deactivation?";

        new AlertDialog.Builder(this)
            .setTitle("Confirm Deactivation")
            .setMessage(confirmMessage)
            .setPositiveButton("Deactivate", (confirmDialog, which) -> {
                String profileId = override.getProfileId();
                Log.d(TAG, "╔═══════════════════════════════════════════");
                Log.d(TAG, "║ USER CONFIRMED - PROCEEDING WITH DEACTIVATION");
                Log.d(TAG, "╠═══════════════════════════════════════════");
                Log.d(TAG, "║ Profile ID: " + profileId);
                Log.d(TAG, "║ Door: " + displayName);
                Log.d(TAG, "║ Deactivated by: " + userName + " (" + userStaffId + ")");
                if (userRole != null) {
                    Log.d(TAG, "║ Role: " + userRole);
                } else {
                    Log.d(TAG, "║ User type: " + (isAdmin ? "Admin" : "User"));
                }

                if (profileId != null && !profileId.isEmpty()) {
                    Log.d(TAG, "║ Calling PAC API...");
                    Log.d(TAG, "╚═══════════════════════════════════════════");

                    android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                    progressDialog.setMessage("Deactivating override...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    manualOverrideService.deactivateProfile(profileId, new ManualOverrideCallback() {
                        @Override
                        public void onProfileCreated(ManualOverrideProfileResponse response) {
                            Log.w(TAG, "onProfileCreated called during deactivation - unexpected");
                        }

                        @Override
                        public void onProfileDeactivated(ManualOverrideProfileResponse response) {
                            Log.i(TAG, "╔═══════════════════════════════════════════");
                            Log.i(TAG, "║ ✓ PAC API DEACTIVATION SUCCESS");
                            Log.i(TAG, "╠═══════════════════════════════════════════");
                            Log.i(TAG, "║ Profile ID: " + response.getProfileId());
                            Log.i(TAG, "║ Status: " + response.getStatus());
                            Log.i(TAG, "║ Deactivated by: " + userName + " (" + userStaffId + ")");
                            if (userRole != null) Log.i(TAG, "║ Role: " + userRole);
                            Log.i(TAG, "╚═══════════════════════════════════════════");

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                String rd = userRole != null ? " (" + userRole + ")" : (isAdmin ? " (Admin)" : " (User)");
                                Toast.makeText(MainVaultStatusActivity.this,
                                    "✓ Override deactivated successfully by:\n" + userName + rd,
                                    Toast.LENGTH_LONG).show();
                                loadDoorsFromApi();
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

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(MainVaultStatusActivity.this,
                                    "Failed to deactivate: " + error,
                                    Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                } else {
                    Log.e(TAG, "║ ERROR: No profile ID found - cannot deactivate!");
                    Log.d(TAG, "╚═══════════════════════════════════════════");
                    Toast.makeText(this, "Cannot deactivate: No profile ID", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", (confirmDialog, which) ->
                Log.d(TAG, "User cancelled deactivation after fingerprint verification"))
            .show();
    }

    /**
     * Match vault name to controller variable by searching through loaded controller profiles
     * This handles various naming formats like "G-009", "Vault Door A", etc.
     * Matches against vaultName (doorName) and vaultInfo nested fields
     */
    private String matchVaultNameToControllerVariable(String vaultName) {
        if (vaultName == null || vaultName.isEmpty()) {
            return null;
        }

        // Search through loaded controller profiles for this vault
        for (ControllerProfileResponse profile : vaultProfiles) {
            // Try matching against top-level vaultName (which can be deserialized from doorName)
            if (profile.getVaultName() != null && vaultName.equalsIgnoreCase(profile.getVaultName())) {
                Log.d(TAG, "Matched '" + vaultName + "' to profile vaultName '" + profile.getVaultName() + "' → " + profile.getControllerVariableName());
                return profile.getControllerVariableName();
            }

            // Try matching against top-level vaultCode
            if (profile.getVaultCode() != null && vaultName.equalsIgnoreCase(profile.getVaultCode())) {
                Log.d(TAG, "Matched '" + vaultName + "' to profile vaultCode '" + profile.getVaultCode() + "' → " + profile.getControllerVariableName());
                return profile.getControllerVariableName();
            }

            // Try matching against vaultInfo nested fields if available
            VaultInfo vaultInfo = profile.getVaultInfo();
            if (vaultInfo != null) {
                // Match against vaultInfo.vaultName
                if (vaultInfo.getVaultName() != null && vaultName.equalsIgnoreCase(vaultInfo.getVaultName())) {
                    Log.d(TAG, "Matched '" + vaultName + "' to vaultInfo.vaultName '" + vaultInfo.getVaultName() + "' → " + profile.getControllerVariableName());
                    return profile.getControllerVariableName();
                }

                // Match against vaultInfo.vaultCode (e.g., "G009" matches "G-009")
                if (vaultInfo.getVaultCode() != null) {
                    String normalizedVaultName = vaultName.replaceAll("[^A-Za-z0-9]", "");
                    String normalizedVaultCode = vaultInfo.getVaultCode().replaceAll("[^A-Za-z0-9]", "");
                    if (normalizedVaultName.equalsIgnoreCase(normalizedVaultCode)) {
                        Log.d(TAG, "Matched '" + vaultName + "' (normalized: '" + normalizedVaultName + "') to vaultInfo.vaultCode '" + vaultInfo.getVaultCode() + "' → " + profile.getControllerVariableName());
                        return profile.getControllerVariableName();
                    }
                }
            }
        }

        Log.w(TAG, "No controller profile found matching vault name: " + vaultName);
        return null;
    }

    /**
     * Derive controller variable name from vault name
     * Used when API doesn't return variableName field
     * Examples:
     *   "Vault Door A" → "MAIN.SOFT_LOCK_A"
     *   "Vault Door B" → "MAIN.SOFT_LOCK_B"
     * @deprecated Use matchVaultNameToControllerVariable() instead - more reliable
     */
    private String deriveVariableNameFromVaultName(String vaultName, String vaultType) {
        if (vaultName == null || vaultName.isEmpty()) {
            return null;
        }

        // Extract door letter from vault name
        // Expected formats: "Vault Door A", "Vault Door B", etc.
        String doorLetter = null;

        // Try to extract letter from end of string
        vaultName = vaultName.trim().toUpperCase();
        if (vaultName.matches(".*\\s[A-Z]$")) {
            // Ends with space + single letter
            doorLetter = vaultName.substring(vaultName.length() - 1);
        } else if (vaultName.matches(".*[A-Z]$")) {
            // Ends with single letter (no space)
            doorLetter = vaultName.substring(vaultName.length() - 1);
        }

        if (doorLetter != null) {
            // Construct variable name: VAULTTYPE.SOFT_LOCK_LETTER
            return vaultType + ".SOFT_LOCK_" + doorLetter;
        }

        Log.w(TAG, "Could not derive variable name from vault name: " + vaultName);
        return null;
    }

    /**
     * Extract door ID from variable name
     * Parses variable names like "MAIN.SOFT_LOCK_A" to get door ID (A=1, B=2, etc.)
     */
    private int extractDoorIdFromVariableName(String variableName) {
        if (variableName == null || variableName.isEmpty()) {
            return -1;
        }

        // Extract the door letter from the variable name
        // Expected format: "MAIN.SOFT_LOCK_X" where X is A-G
        if (variableName.contains("SOFT_LOCK_")) {
            String doorLetter = variableName.substring(variableName.lastIndexOf("_") + 1);

            // Map door letters to IDs
            switch (doorLetter) {
                case "A": return 1;
                case "B": return 2;
                case "C": return 3;
                case "D": return 4;
                case "E": return 5;
                case "F": return 6;
                case "G": return 7;
                default:
                    Log.w(TAG, "Unknown door letter: " + doorLetter);
                    return -1;
            }
        }

        Log.w(TAG, "Could not extract door ID from variable: " + variableName);
        return -1;
    }

    // =================== KEY SWITCH EVENT HANDLERS ===================

    /**
     * Handle individual key switch state change event
     */
    private void handleKeySwitchEvent(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto event) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ KEY SWITCH EVENT");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Door: " + event.getDoorName() + " (ID: " + event.getDoorId() + ")");
        Log.d(TAG, "║ Switch: " + event.getKeySwitchNumber() + " → " + (event.isOn() ? "ON" : "OFF"));
        Log.d(TAG, "║ Time: " + event.getTimestamp());
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        int doorId = event.getDoorId();

        // Initialize state for this door if not exists
        if (!keySwitchStates.containsKey(doorId)) {
            keySwitchStates.put(doorId, new KeySwitchState());
        }

        KeySwitchState state = keySwitchStates.get(doorId);

        // Update specific switch
        switch (event.getKeySwitchNumber()) {
            case 1:
                state.switch1On = event.isOn();
                break;
            case 2:
                state.switch2On = event.isOn();
                break;
            case 3:
                state.switch3On = event.isOn();
                break;
            default:
                Log.w(TAG, "Unknown key switch number: " + event.getKeySwitchNumber());
                return;
        }

        // If event includes complete state snapshot, update all switches
        if (event.getAllKeySwitches() != null) {
            com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchStatesDto allStates = event.getAllKeySwitches();
            state.switch1On = allStates.isKeySwitch1();
            state.switch2On = allStates.isKeySwitch2();
            state.switch3On = allStates.isKeySwitch3();
            Log.d(TAG, "Updated complete state from event: " + state.getIndicator());
        }

        Log.i(TAG, "Key switch state updated for door " + doorId + ": " + state.getIndicator());

        // Refresh door cards to show updated indicators
        mainHandler.post(() -> {
            if (!isDestroyed() && !isFinishing()) {
                refreshDoorCards();
            }
        });
    }

    /**
     * Handle aggregate key switch event (all on/off)
     */
    private void handleKeySwitchAggregateEvent(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto event) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ KEY SWITCH AGGREGATE EVENT");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Door: " + event.getDoorName() + " (ID: " + event.getDoorId() + ")");
        Log.d(TAG, "║ Event: " + event.getEventType());
        Log.d(TAG, "║ All Keys: " + (event.isAllKeysState() ? "ON" : "OFF"));
        Log.d(TAG, "║ Time: " + event.getTimestamp());
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        int doorId = event.getDoorId();

        // Initialize state for this door if not exists
        if (!keySwitchStates.containsKey(doorId)) {
            keySwitchStates.put(doorId, new KeySwitchState());
        }

        KeySwitchState state = keySwitchStates.get(doorId);

        // Update all switches based on event type
        if (event.isAllKeysOn()) {
            state.switch1On = true;
            state.switch2On = true;
            state.switch3On = true;
            Log.i(TAG, "✓ All key switches turned ON for door " + doorId);
        } else if (event.isAllKeysOff()) {
            state.switch1On = false;
            state.switch2On = false;
            state.switch3On = false;
            Log.i(TAG, "All key switches turned OFF for door " + doorId);
        }

        // Also update from complete state snapshot if available
        if (event.getAllKeySwitches() != null) {
            com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchStatesDto allStates = event.getAllKeySwitches();
            state.switch1On = allStates.isKeySwitch1();
            state.switch2On = allStates.isKeySwitch2();
            state.switch3On = allStates.isKeySwitch3();
            Log.d(TAG, "Updated complete state from aggregate event: " + state.getIndicator());
        }

        // Refresh door cards to show updated indicators
        // When all keys are ON, we should also reload the full data as the override status may change to Active
        mainHandler.post(() -> {
            if (!isDestroyed() && !isFinishing()) {
                if (event.isAllKeysOn()) {
                    // All keys on - override might have transitioned to Active, reload full data
                    loadDoorsFromApi();
                } else {
                    // Just refresh the visual indicators
                    refreshDoorCards();
                }
            }
        });
    }

    /**
     * Refresh door cards without reloading from API
     * Used to update key switch indicators in real-time
     */
    private void refreshDoorCards() {
        Log.d(TAG, "Refreshing door cards with updated key switch states");
        createDoorCards();
    }

    /**
     * Get key switch indicator for a specific door
     * Returns a colored visual representation of key switch states
     * Green circles (●) for ON, red circles (●) for OFF
     */
    private CharSequence getKeySwitchIndicator(int doorId) {
        // Get real-time state from SignalR events
        KeySwitchState state = keySwitchStates.get(doorId);

        if (state != null) {
            return state.getIndicator();
        } else {
            // No state available yet - show all red circles (OFF)
            SpannableStringBuilder builder = new SpannableStringBuilder();

            // Add "Key Status" label
            String label = "Key Status: ";
            SpannableString labelSpan = new SpannableString(label);
            labelSpan.setSpan(new ForegroundColorSpan(Color.parseColor("#9E9E9E")),
                        0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(labelSpan);

            String allOff = "● ● ●";
            SpannableString span = new SpannableString(allOff);
            span.setSpan(new ForegroundColorSpan(Color.parseColor("#F44336")),
                        0, allOff.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(span);
            return builder;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDoorsFromApi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop SignalR connection
        if (signalRService != null && signalRConnected) {
            Log.d(TAG, "Stopping SignalR connection...");
            signalRService.stop();
            signalRConnected = false;
        }

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
