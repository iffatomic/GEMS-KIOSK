/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.supremainc.sfm_sdk_android.data.model.response.ControllerProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.api.ControllerProfileApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.ManualOverrideCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.service.ManualOverrideService;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;

import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manual Override Activity - Original Flow (Key Switch Based)
 *
 * Step 1: Select Vault
 * Step 2: Confirmation
 * Step 3: Monitor 3 Key Switches (via SignalR real-time events)
 * Step 4: Show Result (Success/Failure)
 *
 * NO FINGERPRINT AUTHENTICATION - Keys must be physically turned
 */
public class ManualOverrideActivity extends AppCompatActivity {

    private static final String TAG = "ManualOverrideActivity";

    // Toolbar
    private ImageButton backButton;

    // Step indicator
    private TextView stepNumber;
    private TextView stepDescription;

    // Step containers
    private LinearLayout step1Container;
    private LinearLayout step2Container;
    private LinearLayout step3Container;
    private LinearLayout step4Container;
    private LinearLayout step5Container;

    // Step 1: Vault Type Selection
    private Button btnMainVault;
    private Button btnDayVault;
    private LinearLayout vaultButtonContainer;

    // Step 2: Entry Point Selection
    private TextView tvSelectedVault;
    private Button btnMainGrill;
    private Button btnCompartment1;
    private Button btnCompartment2;
    private Button btnCompartment3;
    private Button btnBackToStep1;

    // Step 3: Time Frame Selection (NEW for GEMS Original)
    private TextView tvTimeFrameVaultDisplay;
    private TextView tvStartTime;
    private Button btnSelectEndTime;
    private TextView tvSelectedEndTime;
    private Button btnBackToStep2;
    private Button btnProceedToVerification;

    // Step 4: Key Switch Monitoring (NEW)
    private TextView tvKeySwitchVaultName;
    private TextView tvKeySwitchInstructions;
    private LinearLayout keySwitch1Container, keySwitch2Container, keySwitch3Container;
    private TextView tvKeySwitch1Status, tvKeySwitch2Status, tvKeySwitch3Status;
    private ImageView ivKeySwitch1Icon, ivKeySwitch2Icon, ivKeySwitch3Icon;
    private ProgressBar keySwitchProgress;
    private Button btnCancelKeySwitch;
    private TextView tvKeySwitchWaitingMessage;

    // Step 5: Result
    private ImageView ivResultIcon;
    private TextView tvResultTitle;
    private TextView tvResultDescription;
    private TextView tvOverrideDetails;
    private Button btnDone;

    // State tracking
    private int currentStep = 1;
    private ControllerProfileResponse selectedProfile = null;  // The selected vault/door profile from API

    // Time frame tracking (NEW for GEMS Original)
    private long selectedEndTimeMillis = 0;  // Selected end time in milliseconds
    private static final long MAX_OVERRIDE_DURATION_MS = 7L * 24 * 60 * 60 * 1000;  // 7 days in milliseconds

    // Key switch state tracking (NEW)
    private boolean keySwitch1On = false;
    private boolean keySwitch2On = false;
    private boolean keySwitch3On = false;
    private int selectedDoorId = -1;  // Track which door we're monitoring

    // Database and SDK
    private DatabaseHelper dbHelper;
    private SFM_SDK_ANDROID sdk;
    private volatile boolean isDestroyed = false;  // Thread-safe flag for lifecycle management

    // Background task management
    private ExecutorService executor;
    private Handler mainHandler;

    // API services
    private ManualOverrideService manualOverrideService;
    private ControllerProfileApiClient controllerProfileApiClient;

    // SignalR service for real-time key switch events (NEW)
    private SignalRService signalRService;
    private boolean signalRConnected = false;

    // Cache of controller profiles (vaults and entry points) from API
    private List<ControllerProfileResponse> controllerProfiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_override_new);

        // Initialize
        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        manualOverrideService = new ManualOverrideService(this);
        controllerProfileApiClient = new ControllerProfileApiClient(this);

        // Initialize SignalR for key switch monitoring (NEW)
        initializeSignalR();

        initializeViews();
        setupListeners();
        loadVaultsFromApi();
        showStep(1);
    }

    /**
     * Initialize SignalR connection for real-time key switch monitoring
     */
    private void initializeSignalR() {
        Log.d(TAG, "Initializing SignalR for key switch events...");

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

            // Unused callbacks - no-op
            @Override public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {}
            @Override public void onDoorStateChanged(SignalRService.DoorStateData event) {}
            @Override public void onVaultIncident(SignalRService.VaultIncidentData incident) {}
            @Override public void onVaultIncidentBroadcast(com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto incident) {}
            @Override public void onInterimCutoffReached(com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto event) {}
            @Override public void onConnectionClosed() {
                runOnUiThread(() -> {
                    signalRConnected = false;
                    Toast.makeText(ManualOverrideActivity.this,
                        "SignalR disconnected. Key switches may not update.",
                        Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Connect to SignalR
        signalRService.start(new SignalRService.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "✓ SignalR connected - ready to monitor key switches");
                signalRConnected = true;
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "✗ SignalR connection failed: " + errorMessage);
                runOnUiThread(() -> {
                    Toast.makeText(ManualOverrideActivity.this,
                        "Failed to connect to event system: " + errorMessage,
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void initializeViews() {
        // Toolbar
        backButton = findViewById(R.id.back_button);

        // Step indicator
        stepNumber = findViewById(R.id.stepNumber);
        stepDescription = findViewById(R.id.stepDescription);

        // Step containers
        step1Container = findViewById(R.id.step1Container);
        step2Container = findViewById(R.id.step2Container);
        step3Container = findViewById(R.id.step3Container);
        step4Container = findViewById(R.id.step4Container);
        step5Container = findViewById(R.id.step5Container);

        // Step 1
        btnMainVault = findViewById(R.id.btnMainVault);
        btnDayVault = findViewById(R.id.btnDayVault);
        vaultButtonContainer = findViewById(R.id.vaultButtonContainer);

        // Step 2
        tvSelectedVault = findViewById(R.id.tvSelectedVault);
        btnMainGrill = findViewById(R.id.btnMainGrill);
        btnCompartment1 = findViewById(R.id.btnCompartment1);
        btnCompartment2 = findViewById(R.id.btnCompartment2);
        btnCompartment3 = findViewById(R.id.btnCompartment3);
        btnBackToStep1 = findViewById(R.id.btnBackToStep1);

        // Step 3: Time Frame Selection (NEW for GEMS Original)
        tvTimeFrameVaultDisplay = findViewById(R.id.tvTimeFrameVaultDisplay);
        tvStartTime = findViewById(R.id.tvStartTime);
        btnSelectEndTime = findViewById(R.id.btnSelectEndTime);
        tvSelectedEndTime = findViewById(R.id.tvSelectedEndTime);
        btnBackToStep2 = findViewById(R.id.btnBackToStep2);
        btnProceedToVerification = findViewById(R.id.btnProceedToVerification);

        // Step 4: Key Switch Monitoring (NEW)
        tvKeySwitchVaultName = findViewById(R.id.tvKeySwitchVaultName);
        tvKeySwitchInstructions = findViewById(R.id.tvKeySwitchInstructions);
        keySwitch1Container = findViewById(R.id.keySwitch1Container);
        keySwitch2Container = findViewById(R.id.keySwitch2Container);
        keySwitch3Container = findViewById(R.id.keySwitch3Container);
        tvKeySwitch1Status = findViewById(R.id.tvKeySwitch1Status);
        tvKeySwitch2Status = findViewById(R.id.tvKeySwitch2Status);
        tvKeySwitch3Status = findViewById(R.id.tvKeySwitch3Status);
        ivKeySwitch1Icon = findViewById(R.id.ivKeySwitch1Icon);
        ivKeySwitch2Icon = findViewById(R.id.ivKeySwitch2Icon);
        ivKeySwitch3Icon = findViewById(R.id.ivKeySwitch3Icon);
        keySwitchProgress = findViewById(R.id.keySwitchProgress);
        btnCancelKeySwitch = findViewById(R.id.btnCancelKeySwitch);
        tvKeySwitchWaitingMessage = findViewById(R.id.tvKeySwitchWaitingMessage);

        // Step 5: Result
        ivResultIcon = findViewById(R.id.ivResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultDescription = findViewById(R.id.tvResultDescription);
        tvOverrideDetails = findViewById(R.id.tvOverrideDetails);
        btnDone = findViewById(R.id.btnDone);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        // Step 1: Vault/Door Selection - buttons are created dynamically in populateVaultButtons()

        // Step 2: Entry Point Selection - REMOVED (no longer needed)
        // Each vault/door selection from API already represents a specific door

        // Step 3: Time Frame Selection
        btnBackToStep2.setOnClickListener(v -> showStep(1));  // Go back to step 1 (vault selection)

        btnSelectEndTime.setOnClickListener(v -> showDateTimePicker());

        btnProceedToVerification.setOnClickListener(v -> {
            // Validate time frame selected
            if (selectedEndTimeMillis == 0) {
                Toast.makeText(this, "Please select end time", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create override profile FIRST (with time frame)
            createOverrideProfileWithTimeFrame();
        });

        // Step 4: Key Switch Monitoring
        btnCancelKeySwitch.setOnClickListener(v -> finish());

        // Step 5: Done
        btnDone.setOnClickListener(v -> finish());
    }

    // =================== VAULT LOADING FROM API ===================

    /**
     * Load vaults and entry points from ControllerProfile API
     */
    private void loadVaultsFromApi() {
        Log.d(TAG, "Loading vaults and entry points from ControllerProfile API...");

        controllerProfileApiClient.getAllControllerProfiles(new ApiCallback<List<ControllerProfileResponse>>() {
            @Override
            public void onSuccess(List<ControllerProfileResponse> profiles) {
                Log.i(TAG, "Successfully loaded " + profiles.size() + " controller profiles");
                controllerProfiles = profiles;

                // Populate vault buttons on main thread
                mainHandler.post(() -> {
                    if (isDestroyed || isFinishing()) {
                        Log.d(TAG, "Activity destroyed, skipping UI update");
                        return;
                    }
                    try {
                        populateVaultButtons(profiles);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating UI", e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading controller profiles: " + error);
                mainHandler.post(() -> {
                    if (isDestroyed || isFinishing()) {
                        Log.d(TAG, "Activity destroyed, skipping UI update");
                        return;
                    }
                    try {
                        Toast.makeText(ManualOverrideActivity.this,
                            "Failed to load vaults: " + error,
                            Toast.LENGTH_SHORT).show();
                        // Fall back to showing hardcoded buttons if API fails
                        showFallbackVaultButtons();
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating UI", e);
                    }
                });
            }
        });
    }

    /**
     * Dynamically create vault/door buttons from API data
     * Each controller profile represents a specific door
     */
    private void populateVaultButtons(List<ControllerProfileResponse> profiles) {
        // Clear existing dynamic buttons
        vaultButtonContainer.removeAllViews();

        Log.d(TAG, "Found " + profiles.size() + " controller profiles (doors)");

        // Create a button for each controller profile (door)
        for (ControllerProfileResponse profile : profiles) {
            Button doorButton = createDoorButton(profile);
            vaultButtonContainer.addView(doorButton);
        }

        // If no profiles found, show fallback
        if (profiles.isEmpty()) {
            Log.w(TAG, "No controller profiles found in API response, showing fallback buttons");
            showFallbackVaultButtons();
        }
    }

    /**
     * Create a door button with proper styling
     * Each button represents a specific door (ControllerProfile)
     */
    private Button createDoorButton(ControllerProfileResponse profile) {
        Button button = new Button(this);

        // Set layout parameters
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int) (56 * getResources().getDisplayMetrics().density) // 56dp height
        );
        params.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density)); // 16dp bottom margin
        button.setLayoutParams(params);

        // Use vaultName as display name (e.g., "Vault Door A")
        final String displayName = profile.getVaultName() != null && !profile.getVaultName().isEmpty()
                ? profile.getVaultName()
                : profile.getControllerVariableName();

        // Set button properties
        button.setText(displayName);
        button.setTextSize(16);
        button.setTextColor(getResources().getColor(R.color.buttonText));
        button.setBackgroundTintList(getResources().getColorStateList(R.color.buttonBackground));
        button.setAllCaps(false);

        // Set click listener - go directly to confirmation (step 3)
        button.setOnClickListener(v -> {
            // Check if this door already has an active override
            if (isDoorAlreadyActivated(profile)) {
                Toast.makeText(this, displayName + " is already activated!\nPlease deactivate it first before creating a new override.", Toast.LENGTH_LONG).show();
                return;
            }

            selectedProfile = profile;
            Log.d(TAG, "Selected door: " + displayName + " (Variable: " + profile.getControllerVariableName() + ")");

            // Skip directly to confirmation (step 3)
            showStep(3);
        });

        return button;
    }

    /**
     * Show hardcoded vault buttons as fallback
     */
    private void showFallbackVaultButtons() {
        vaultButtonContainer.removeAllViews();
        btnMainVault.setVisibility(View.VISIBLE);
        btnDayVault.setVisibility(View.VISIBLE);
    }

    /**
     * Check if a door already has an active override
     */
    private boolean isDoorAlreadyActivated(ControllerProfileResponse profile) {
        if (profile == null || profile.getControllerVariableName() == null) {
            return false;
        }

        // You can check against local database or API if needed
        // For now, we'll return false to allow activation
        // TODO: Implement check against active overrides
        return false;
    }

    /**
     * Get display name for the selected door
     */
    private String getDoorDisplayName() {
        if (selectedProfile == null) {
            return "Unknown Door";
        }

        // Return just the vault name (e.g., "Vault Door A")
        return selectedProfile.getVaultName() != null && !selectedProfile.getVaultName().isEmpty()
                ? selectedProfile.getVaultName()
                : selectedProfile.getControllerVariableName();
    }

    // =================== STEP NAVIGATION ===================

    private void showStep(int step) {
        currentStep = step;

        step1Container.setVisibility(View.GONE);
        step2Container.setVisibility(View.GONE);
        step3Container.setVisibility(View.GONE);
        step4Container.setVisibility(View.GONE);
        step5Container.setVisibility(View.GONE);

        // Map internal step numbers to display step numbers (skip step 2)
        // Internal: 1, 3, 4, 5 → Display: 1, 2, 3, 4
        int displayStepNumber;
        switch (step) {
            case 1: displayStepNumber = 1; break;
            case 3: displayStepNumber = 2; break;
            case 4: displayStepNumber = 3; break;
            case 5: displayStepNumber = 4; break;
            default: displayStepNumber = step; break;
        }
        stepNumber.setText(String.valueOf(displayStepNumber));

        switch (step) {
            case 1:
                stepDescription.setText("Select Vault");
                step1Container.setVisibility(View.VISIBLE);
                break;

            case 2:
                // Step 2 removed - skip to step 3 directly
                showStep(3);
                break;

            case 3:
                stepDescription.setText("Set Time Frame");
                step3Container.setVisibility(View.VISIBLE);

                // Display selected vault
                tvTimeFrameVaultDisplay.setText(getDoorDisplayName());

                // Show current time as start time
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                tvStartTime.setText(sdf.format(new java.util.Date()));

                // Reset end time selection
                selectedEndTimeMillis = 0;
                tvSelectedEndTime.setText("Not selected");
                tvSelectedEndTime.setTextColor(getResources().getColor(R.color.textSecondary));
                btnProceedToVerification.setEnabled(false);
                break;

            case 4:
                stepDescription.setText("Turn Key Switches");
                step4Container.setVisibility(View.VISIBLE);

                // Display vault name
                tvKeySwitchVaultName.setText(getDoorDisplayName());
                tvKeySwitchVaultName.setTextSize(28);
                tvKeySwitchVaultName.setTextColor(getResources().getColor(android.R.color.white));

                // Set instructions
                tvKeySwitchInstructions.setText("Please turn all 3 key switches to activate manual override");

                // Initialize key switch UI
                resetKeySwitchUI();

                // Extract door ID for event filtering
                selectedDoorId = extractDoorIdFromProfile(selectedProfile);
                Log.d(TAG, "Monitoring key switches for Door ID: " + selectedDoorId);

                // Show waiting message
                keySwitchProgress.setVisibility(View.VISIBLE);
                tvKeySwitchWaitingMessage.setVisibility(View.VISIBLE);
                tvKeySwitchWaitingMessage.setText("Waiting for key switches to be turned...");
                break;

            case 5:
                stepDescription.setText("Complete");
                step5Container.setVisibility(View.VISIBLE);
                break;
        }
    }

    // =================== KEY SWITCH MONITORING (SignalR Events) ===================

    /**
     * Reset key switch state
     */
    private void resetKeySwitchState() {
        keySwitch1On = false;
        keySwitch2On = false;
        keySwitch3On = false;
        Log.d(TAG, "Key switch state reset");
    }

    /**
     * Reset key switch UI indicators
     */
    private void resetKeySwitchUI() {
        // Key Switch 1
        tvKeySwitch1Status.setText("OFF - Waiting...");
        tvKeySwitch1Status.setTextColor(getResources().getColor(R.color.textSecondary));
        ivKeySwitch1Icon.setAlpha(0.3f);
        ivKeySwitch1Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
        keySwitch1Container.setBackgroundColor(0x40FF0000); // Red tint

        // Key Switch 2
        tvKeySwitch2Status.setText("OFF - Waiting...");
        tvKeySwitch2Status.setTextColor(getResources().getColor(R.color.textSecondary));
        ivKeySwitch2Icon.setAlpha(0.3f);
        ivKeySwitch2Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
        keySwitch2Container.setBackgroundColor(0x40FF0000); // Red tint

        // Key Switch 3
        tvKeySwitch3Status.setText("OFF - Waiting...");
        tvKeySwitch3Status.setTextColor(getResources().getColor(R.color.textSecondary));
        ivKeySwitch3Icon.setAlpha(0.3f);
        ivKeySwitch3Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
        keySwitch3Container.setBackgroundColor(0x40FF0000); // Red tint
    }

    /**
     * Handle individual key switch event from SignalR
     */
    private void handleKeySwitchEvent(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto event) {
        // Filter by door ID - only process events for the selected vault
        if (event.getDoorId() != selectedDoorId) {
            Log.d(TAG, "Ignoring key switch event for different door: " + event.getDoorId());
            return;
        }

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ KEY SWITCH EVENT");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Door: " + event.getDoorName() + " (ID: " + event.getDoorId() + ")");
        Log.d(TAG, "║ Switch: " + event.getKeySwitchNumber());
        Log.d(TAG, "║ State: " + (event.isOn() ? "ON" : "OFF"));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        runOnUiThread(() -> {
            // Update state based on switch number
            switch (event.getKeySwitchNumber()) {
                case 1:
                    keySwitch1On = event.isOn();
                    updateKeySwitchUI(1, event.isOn());
                    break;
                case 2:
                    keySwitch2On = event.isOn();
                    updateKeySwitchUI(2, event.isOn());
                    break;
                case 3:
                    keySwitch3On = event.isOn();
                    updateKeySwitchUI(3, event.isOn());
                    break;
            }

            // Check if all switches are ON
            checkIfAllKeySwitchesOn();
        });
    }

    /**
     * Handle aggregate key switch event (all ON or all OFF)
     */
    private void handleKeySwitchAggregateEvent(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto event) {
        // Filter by door ID
        if (event.getDoorId() != selectedDoorId) {
            return;
        }

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ KEY SWITCH AGGREGATE EVENT");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Door: " + event.getDoorName());
        Log.d(TAG, "║ Event: " + event.getEventType());
        Log.d(TAG, "║ All Keys State: " + (event.isAllKeysState() ? "ON" : "OFF"));
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        runOnUiThread(() -> {
            if (event.isAllKeysOn()) {
                // All switches turned ON
                keySwitch1On = true;
                keySwitch2On = true;
                keySwitch3On = true;
                updateKeySwitchUI(1, true);
                updateKeySwitchUI(2, true);
                updateKeySwitchUI(3, true);
                checkIfAllKeySwitchesOn();
            } else if (event.isAllKeysOff()) {
                // All switches turned OFF
                keySwitch1On = false;
                keySwitch2On = false;
                keySwitch3On = false;
                updateKeySwitchUI(1, false);
                updateKeySwitchUI(2, false);
                updateKeySwitchUI(3, false);
            }
        });
    }

    /**
     * Update UI for a specific key switch
     */
    private void updateKeySwitchUI(int switchNumber, boolean isOn) {
        TextView statusText;
        ImageView icon;
        LinearLayout container;

        switch (switchNumber) {
            case 1:
                statusText = tvKeySwitch1Status;
                icon = ivKeySwitch1Icon;
                container = keySwitch1Container;
                break;
            case 2:
                statusText = tvKeySwitch2Status;
                icon = ivKeySwitch2Icon;
                container = keySwitch2Container;
                break;
            case 3:
                statusText = tvKeySwitch3Status;
                icon = ivKeySwitch3Icon;
                container = keySwitch3Container;
                break;
            default:
                return;
        }

        if (isOn) {
            // Switch is ON - green
            statusText.setText("ON ✓");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            icon.setAlpha(1.0f);
            icon.setColorFilter(getResources().getColor(android.R.color.holo_green_light));
            container.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark) & 0x40FFFFFF);
            Log.i(TAG, "Key Switch " + switchNumber + " turned ON");
        } else {
            // Switch is OFF - red
            statusText.setText("OFF - Waiting...");
            statusText.setTextColor(getResources().getColor(R.color.textSecondary));
            icon.setAlpha(0.3f);
            icon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
            container.setBackgroundColor(0x40FF0000);
            Log.i(TAG, "Key Switch " + switchNumber + " turned OFF");
        }
    }

    /**
     * Check if all 3 key switches are ON
     * Backend automatically activates the pending profile when keys are turned on
     */
    private void checkIfAllKeySwitchesOn() {
        if (keySwitch1On && keySwitch2On && keySwitch3On) {
            Log.i(TAG, "✓✓✓ ALL KEY SWITCHES ON - Profile automatically activated by backend");

            // Hide waiting message
            tvKeySwitchWaitingMessage.setVisibility(View.GONE);
            keySwitchProgress.setVisibility(View.VISIBLE);

            Toast.makeText(this, "All key switches ON! Override activated!", Toast.LENGTH_LONG).show();

            // Short delay before showing success
            mainHandler.postDelayed(() -> {
                showSuccessResult();
            }, 1500);
        }
    }

    // =================== ACTIVATE OVERRIDE ===================

    private void activateOverride() {
        Log.d(TAG, "Activating override: " + getDoorDisplayName());

        // Show progress
        keySwitchProgress.setVisibility(View.VISIBLE);
        btnCancelKeySwitch.setEnabled(false);
        tvKeySwitchWaitingMessage.setText("Sending request to PAC API...");

        // API-FIRST APPROACH: Send to PAC_API first, show result based on API response
        sendOverrideToPacApi();
    }

    /**
     * Send override profile to PAC_API server - API-FIRST APPROACH
     * Success/failure is determined by API response only
     * Local database is updated AFTER successful API response for caching only
     */
    private void sendOverrideToPacApi() {
        if (selectedProfile == null) {
            Log.e(TAG, "ERROR: No profile selected");
            runOnUiThread(() -> {
                keySwitchProgress.setVisibility(View.GONE);
                showFailureResult("No door selected");
            });
            return;
        }

        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ API-FIRST OVERRIDE ACTIVATION");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Door: " + getDoorDisplayName());
        Log.d(TAG, "║ Controller Variable: " + selectedProfile.getControllerVariableName());

        // Extract door ID from controller variable name (e.g., "MAIN.SOFT_LOCK_A" -> extract door letter)
        int doorId = extractDoorIdFromProfile(selectedProfile);
        if (doorId == -1) {
            Log.e(TAG, "║ ERROR: Could not determine door ID from profile");
            Log.d(TAG, "╚═══════════════════════════════════════════");
            runOnUiThread(() -> {
                keySwitchProgress.setVisibility(View.GONE);
                showFailureResult("Invalid door configuration");
            });
            return;
        }

        Log.d(TAG, "║ Door ID: " + doorId);

        ManualOverrideService service = new ManualOverrideService(this);

        // Get vault name for display
        String vaultName = selectedProfile.getVaultName();

        // Set start time to now (end time will be null - indefinite until API's 8pm cutoff)
        long startTime = System.currentTimeMillis();

        Log.d(TAG, "║ Vault Name: " + vaultName);
        Log.d(TAG, "║ Activation Method: Key Switches (Physical)");
        Log.d(TAG, "║ No custodian authentication required");
        Log.d(TAG, "║ Creating indefinite override (no end time - API handles 8pm cutoff)");
        Log.d(TAG, "║ Sending to PAC API...");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // Use placeholder custodian names since key switches provide physical authorization
        String custodianPlaceholder = "Key Switch Authorized";

        service.createIndefiniteOverrideProfile(
                doorId,
                vaultName,                      // vaultName
                custodianPlaceholder,           // custodian1 name (placeholder)
                custodianPlaceholder,           // custodian2 name (placeholder)
                custodianPlaceholder,           // custodian3 name (placeholder)
                startTime,                      // startTimeMillis
                "Mobile Kiosk - Key Switches",  // requestedBy
                new ManualOverrideCallback() {
                    @Override
                    public void onProfileCreated(ManualOverrideProfileResponse response) {
                        runOnUiThread(() -> {
                            Log.i(TAG, "╔═══════════════════════════════════════════");
                            Log.i(TAG, "║ ✓ PAC API SUCCESS");
                            Log.i(TAG, "╠═══════════════════════════════════════════");
                            Log.i(TAG, "║ Profile ID: " + response.getProfileId());
                            Log.i(TAG, "║ Variable: " + response.getVariableName());
                            Log.i(TAG, "║ Status: " + response.getStatus());
                            Log.i(TAG, "╚═══════════════════════════════════════════");

                            keySwitchProgress.setVisibility(View.GONE);

                            // OPTIONAL: Save to local database for caching/logging only
                            // This does NOT affect the success/failure shown to user
                            saveToLocalDatabaseForCaching(response.getProfileId());

                            // Show success based on API response
                            showSuccessResult();
                        });
                    }

                    @Override
                    public void onProfileDeactivated(ManualOverrideProfileResponse response) {
                        // Not used during activation
                    }

                    @Override
                    public void onProfileError(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "╔═══════════════════════════════════════════");
                            Log.e(TAG, "║ ✗ PAC API FAILURE");
                            Log.e(TAG, "╠═══════════════════════════════════════════");
                            Log.e(TAG, "║ Error: " + error);
                            Log.e(TAG, "╚═══════════════════════════════════════════");

                            keySwitchProgress.setVisibility(View.GONE);

                            // Show failure based on API response
                            showFailureResult("PAC API Error: " + error);
                        });
                    }
                }
        );
    }

    /**
     * OPTIONAL: Save override to local database for caching/logging purposes only
     * This is called AFTER successful API response
     * This does NOT affect success/failure shown to user
     */
    private void saveToLocalDatabaseForCaching(String profileId) {
        try {
            if (selectedProfile == null) {
                Log.w(TAG, "Cannot save to database: no profile selected");
                return;
            }

            // Use the controller variable name as a unique identifier
            String vaultType = selectedProfile.getVaultName();
            String entryPoint = selectedProfile.getControllerVariableName();

            // Use placeholders since key switches don't have custodian IDs
            long overrideId = dbHelper.activateVaultOverride(
                    vaultType,
                    entryPoint,
                    "KEY_SWITCH_1",
                    "KEY_SWITCH_2",
                    "KEY_SWITCH_3"
            );

            if (overrideId != -1 && profileId != null) {
                dbHelper.linkOverrideToProfile((int)overrideId, profileId);
                Log.d(TAG, "Local database updated for caching (Override ID: " + overrideId + ", Profile ID: " + profileId + ")");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save to local database (non-critical): " + e.getMessage());
            // Don't show error to user - this is just for caching
        }
    }

    /**
     * Update status text in Step 4
     */
    private void updateStatusTextInStep4(String message) {
        // You can add a status TextView in step4Container if needed
        Log.d(TAG, "Status: " + message);
    }

    /**
     * Extract door ID from controller profile
     * Maps controller variable name to door ID (1-7)
     * Example: "MAIN.SOFT_LOCK_A" -> 1 (Door A)
     */
    private int extractDoorIdFromProfile(ControllerProfileResponse profile) {
        if (profile == null || profile.getControllerVariableName() == null) {
            return -1;
        }

        String variableName = profile.getControllerVariableName();
        Log.d(TAG, "Extracting door ID from variable: " + variableName);

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

    private void showSuccessResult() {
        showStep(5);

        ivResultIcon.setImageResource(R.drawable.ic_check_circle);
        ivResultIcon.setColorFilter(getResources().getColor(R.color.holo_green_dark));
        tvResultTitle.setText("Override Activated!");
        tvResultTitle.setTextColor(getResources().getColor(R.color.holo_green_dark));
        tvResultDescription.setText(getDoorDisplayName() + "\nis now overridden");

        // Show key switch activation details
        tvOverrideDetails.setText("Activated via:\n✓ Key Switch 1\n✓ Key Switch 2\n✓ Key Switch 3");
        tvOverrideDetails.setVisibility(View.VISIBLE);
    }

    private void showFailureResult(String errorMessage) {
        showStep(5);

        ivResultIcon.setImageResource(android.R.drawable.ic_delete);
        ivResultIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
        tvResultTitle.setText("Override Failed!");
        tvResultTitle.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        tvResultDescription.setText(errorMessage);
        tvOverrideDetails.setVisibility(View.GONE);
    }

    // =================== HELPER METHODS ===================

    @Override
    public void onBackPressed() {
        if (currentStep == 3) {
            // From confirmation, go back to vault selection (step 1)
            showStep(1);
        } else if (currentStep == 4) {
            // From verification, go back to confirmation (step 3)
            showStep(3);
        } else if (currentStep == 5) {
            // From result, close activity
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    // =================== TIME FRAME SELECTION (GEMS Original) ===================

    /**
     * Show date and time picker dialog for end time selection
     */
    private void showDateTimePicker() {
        final java.util.Calendar calendar = java.util.Calendar.getInstance();

        // Show date picker first
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Date selected, now show time picker
                    android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                // Both date and time selected
                                java.util.Calendar selectedCal = java.util.Calendar.getInstance();
                                selectedCal.set(year, month, dayOfMonth, hourOfDay, minute, 0);

                                long selectedTimeMillis = selectedCal.getTimeInMillis();
                                long currentTimeMillis = System.currentTimeMillis();
                                long durationMillis = selectedTimeMillis - currentTimeMillis;

                                // Validate: must be in future
                                if (selectedTimeMillis <= currentTimeMillis) {
                                    Toast.makeText(this, "End time must be in the future", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Validate: max 7 days
                                if (durationMillis > MAX_OVERRIDE_DURATION_MS) {
                                    Toast.makeText(this, "Maximum override duration is 7 days", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Valid time selected
                                selectedEndTimeMillis = selectedTimeMillis;

                                // Display selected time
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                                tvSelectedEndTime.setText(sdf.format(new java.util.Date(selectedEndTimeMillis)));
                                tvSelectedEndTime.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                                // Enable proceed button
                                btnProceedToVerification.setEnabled(true);

                                Log.d(TAG, "End time selected: " + sdf.format(new java.util.Date(selectedEndTimeMillis)));
                            },
                            calendar.get(java.util.Calendar.HOUR_OF_DAY),
                            calendar.get(java.util.Calendar.MINUTE),
                            false
                    );
                    timePickerDialog.show();
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
        );

        // Set minimum date to today
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());

        // Set maximum date to 7 days from now
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis() + MAX_OVERRIDE_DURATION_MS);

        datePickerDialog.show();
    }

    /**
     * Create override profile with time frame, then proceed to key switch monitoring
     */
    private void createOverrideProfileWithTimeFrame() {
        if (selectedProfile == null) {
            Toast.makeText(this, "No vault selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedEndTimeMillis == 0) {
            Toast.makeText(this, "Please select end time", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Creating override profile with time frame...");

        // Show progress
        btnProceedToVerification.setEnabled(false);
        btnProceedToVerification.setText("Creating...");

        // Extract door ID
        int doorId = extractDoorIdFromProfile(selectedProfile);
        String vaultName = getDoorDisplayName();
        long startTimeMillis = System.currentTimeMillis();

        // Create profile with time frame
        manualOverrideService.createOverrideProfile(
                doorId,
                vaultName,
                "Key Switch Authorized",  // custodian1 (placeholder)
                "Key Switch Authorized",  // name/custodian2 (placeholder)
                "Key Switch Authorized",  // employee/custodian3 (placeholder)
                "",  // department
                startTimeMillis,
                selectedEndTimeMillis,
                "Mobile Kiosk - Key Switches",
                new ManualOverrideCallback() {
                    @Override
                    public void onProfileCreated(com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse profile) {
                        runOnUiThread(() -> {
                            Log.i(TAG, "✓ Override profile created successfully: " + profile.getProfileId());
                            Toast.makeText(ManualOverrideActivity.this,
                                "Profile created! Waiting for key switches...",
                                Toast.LENGTH_SHORT).show();

                            // Profile created → Now show key switch monitoring
                            resetKeySwitchState();
                            showStep(4);
                        });
                    }

                    @Override
                    public void onProfileError(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "✗ Failed to create profile: " + error);
                            Toast.makeText(ManualOverrideActivity.this,
                                "Failed to create profile: " + error,
                                Toast.LENGTH_LONG).show();

                            // Re-enable button
                            btnProceedToVerification.setEnabled(true);
                            btnProceedToVerification.setText("Create Override");
                        });
                    }

                    // Unused callbacks
                    @Override public void onProfileDeactivated(com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse profile) {}
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Signal all background threads to stop
        isDestroyed = true;

        // Remove all pending handler callbacks to prevent UI updates after destroy
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        // Disconnect SignalR
        if (signalRService != null && signalRConnected) {
            try {
                signalRService.stop();
                Log.d(TAG, "SignalR connection closed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing SignalR connection", e);
            }
        }

        // Properly shutdown executor and wait for running tasks
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();  // Interrupt running tasks
            try {
                // Wait max 2 seconds for tasks to finish
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for executor shutdown", e);
                Thread.currentThread().interrupt();
            }
        }

        // Close database
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
