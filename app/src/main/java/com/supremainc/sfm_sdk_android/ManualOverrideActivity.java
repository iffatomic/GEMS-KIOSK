/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.supremainc.sfm_sdk_android.data.model.response.ControllerProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideConfigurationResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.data.model.response.VaultInfo;
import com.supremainc.sfm_sdk_android.network.api.ControllerProfileApiClient;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
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
import java.util.LinkedHashSet;
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
    private LinearLayout step6Container;

    // Step 1: Employee ID Input
    private EditText etEmployeeId;
    private Button btnProceedToFingerprint;

    // Step 2: Fingerprint Authentication
    private TextView tvEmployeeIdDisplay;
    private ImageView ivFingerprintIcon;
    private TextView tvFingerprintStatus;
    private LinearLayout verifiedEmployeeContainer;
    private TextView tvVerifiedEmployeeName;
    private TextView tvVerifiedEmployeeId;
    private ProgressBar fingerprintProgress;
    private Button btnScanFingerprint;
    private Button btnProceedToVaultSelection;
    private Button btnBackToEmployeeId;

    // Step 3: Vault/Door Selection
    private Spinner spinnerVaultCategory;
    private Button btnMainVault;
    private Button btnDayVault;
    private LinearLayout vaultButtonContainer;

    // Step 4: Time Frame Selection
    private TextView tvTimeFrameVaultDisplay;
    private TextView tvStartTime;
    private Spinner spinnerOverrideDuration;
    private TextView tvEndTimeLabel;
    private Button btnSelectEndTime;
    private TextView tvSelectedEndTime;
    private Button btnBackToStep3;
    private Button btnProceedToVerification;

    // Step 5: Key Switch Monitoring
    private TextView tvKeySwitchVaultName;
    private TextView tvKeySwitchInstructions;
    private LinearLayout keySwitch1Container, keySwitch2Container, keySwitch3Container;
    private TextView tvKeySwitch1Status, tvKeySwitch2Status, tvKeySwitch3Status;
    private ImageView ivKeySwitch1Icon, ivKeySwitch2Icon, ivKeySwitch3Icon;
    private ProgressBar keySwitchProgress;
    private Button btnCancelKeySwitch;
    private TextView tvKeySwitchWaitingMessage;

    // Step 6: Result
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
    private int maxOverrideDurationDays = 7;  // Reflects selected configuration's max days
    private List<ManualOverrideConfigurationResponse> overrideConfigurations = new ArrayList<>();
    private ManualOverrideConfigurationResponse selectedConfiguration = null;

    // Key switch state tracking (NEW)
    private boolean keySwitch1On = false;
    private boolean keySwitch2On = false;
    private boolean keySwitch3On = false;
    private String selectedDoorName = null;  // Track which door we're monitoring (by door name)

    // Fingerprint authentication state
    private boolean isFingerprintVerified = false;
    private boolean isScanning = false;
    private String enteredEmployeeId = "";  // Store entered employee ID from Step 1
    private DatabaseHelper.User expectedUser = null;  // Expected user based on entered employee ID (from users table)
    private DatabaseHelper.SyncedFingerprint expectedSyncedUser = null;  // Expected user based on entered employee ID (from synced_fingerprints table)
    private DatabaseHelper.User verifiedUser = null;  // Stores verified employee from users table (after fingerprint match)
    private DatabaseHelper.SyncedFingerprint verifiedSyncedUser = null;  // Stores verified employee from synced_fingerprints table (after fingerprint match)

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
    private FingerprintDownloadApiClient fingerprintDownloadApiClient;

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
        fingerprintDownloadApiClient = new FingerprintDownloadApiClient(this);

        // Initialize SDK (reuse from MainMenuActivity - scanner already initialized)
        sdk = new SFM_SDK_ANDROID();

        // Initialize SignalR for key switch monitoring (NEW)
        initializeSignalR();

        initializeViews();
        setupListeners();
        loadVaultsFromApi();
        loadOverrideConfiguration();
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
            @Override public void onFingerprintEnrollmentCompleted(com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto event) {}
            @Override public void onKioskPatchReady() {
                // Handled by KioskUpdateService
            }
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
                    String userMessage = "⚠ Cannot connect to API\nReal-time monitoring unavailable";
                    Toast.makeText(ManualOverrideActivity.this,
                        userMessage,
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
        step6Container = findViewById(R.id.step6Container);

        // Step 1: Employee ID Input
        etEmployeeId = findViewById(R.id.etEmployeeId);
        btnProceedToFingerprint = findViewById(R.id.btnProceedToFingerprint);

        // Step 2: Fingerprint Authentication
        tvEmployeeIdDisplay = findViewById(R.id.tvEmployeeIdDisplay);
        ivFingerprintIcon = findViewById(R.id.ivFingerprintIcon);
        tvFingerprintStatus = findViewById(R.id.tvFingerprintStatus);
        verifiedEmployeeContainer = findViewById(R.id.verifiedEmployeeContainer);
        tvVerifiedEmployeeName = findViewById(R.id.tvVerifiedEmployeeName);
        tvVerifiedEmployeeId = findViewById(R.id.tvVerifiedEmployeeId);
        fingerprintProgress = findViewById(R.id.fingerprintProgress);
        btnScanFingerprint = findViewById(R.id.btnScanFingerprint);
        btnProceedToVaultSelection = findViewById(R.id.btnProceedToVaultSelection);
        btnBackToEmployeeId = findViewById(R.id.btnBackToEmployeeId);

        // Step 3: Vault/Door Selection
        spinnerVaultCategory = findViewById(R.id.spinnerVaultCategory);
        btnMainVault = findViewById(R.id.btnMainVault);
        btnDayVault = findViewById(R.id.btnDayVault);
        vaultButtonContainer = findViewById(R.id.vaultButtonContainer);

        // Step 4: Time Frame Selection
        tvTimeFrameVaultDisplay = findViewById(R.id.tvTimeFrameVaultDisplay);
        tvStartTime = findViewById(R.id.tvStartTime);
        spinnerOverrideDuration = findViewById(R.id.spinnerOverrideDuration);
        tvEndTimeLabel = findViewById(R.id.tvEndTimeLabel);
        btnSelectEndTime = findViewById(R.id.btnSelectEndTime);
        tvSelectedEndTime = findViewById(R.id.tvSelectedEndTime);
        btnBackToStep3 = findViewById(R.id.btnBackToStep3);
        btnProceedToVerification = findViewById(R.id.btnProceedToVerification);

        // Step 5: Key Switch Monitoring
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

        // Step 6: Result
        ivResultIcon = findViewById(R.id.ivResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultDescription = findViewById(R.id.tvResultDescription);
        tvOverrideDetails = findViewById(R.id.tvOverrideDetails);
        btnDone = findViewById(R.id.btnDone);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        // Step 1: Employee ID Input
        btnProceedToFingerprint.setOnClickListener(v -> {
            String employeeId = etEmployeeId.getText().toString().trim();
            if (employeeId.isEmpty()) {
                Toast.makeText(this, "Please enter your employee ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // Verify employee ID exists in database
            verifyEmployeeIdExists(employeeId);
        });

        // Step 2: Fingerprint Authentication
        btnScanFingerprint.setOnClickListener(v -> startFingerprintScan());
        btnProceedToVaultSelection.setOnClickListener(v -> {
            if (isFingerprintVerified) {
                checkOverridePermissionThenProceed();
            } else {
                Toast.makeText(this, "Please verify your fingerprint first", Toast.LENGTH_SHORT).show();
            }
        });
        btnBackToEmployeeId.setOnClickListener(v -> {
            // Reset all employee verification state when going back to Step 1
            resetEmployeeVerificationState();
            showStep(1);
        });

        // Step 3: Vault/Door Selection - buttons are created dynamically in populateVaultButtons()

        // Step 4: Time Frame Selection
        btnBackToStep3.setOnClickListener(v -> showStep(3));  // Go back to vault selection

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

        // Step 5: Key Switch Monitoring
        btnCancelKeySwitch.setOnClickListener(v -> finish());

        // Step 6: Done
        btnDone.setOnClickListener(v -> finish());
    }

    // =================== EMPLOYEE ID VERIFICATION ===================

    /**
     * Verify that the entered employee ID exists in the database
     * Searches both users table and synced_fingerprints table
     */
    private void verifyEmployeeIdExists(String employeeId) {
        Log.d(TAG, "Verifying employee ID: " + employeeId);

        // Search in users table first (by staffId)
        DatabaseHelper.User user = dbHelper.getUserByStaffId(employeeId);

        // If not found in users table, search in synced_fingerprints table (by employeeNumber)
        DatabaseHelper.SyncedFingerprint syncedUser = null;
        if (user == null) {
            syncedUser = dbHelper.getSyncedUserByEmployeeNumber(employeeId);
        }

        if (user != null) {
            // Employee ID found in users table
            enteredEmployeeId = employeeId;
            expectedUser = user;
            expectedSyncedUser = null;

            Log.i(TAG, "✓ Employee ID verified: " + user.getName() + " (Staff ID: " + user.getStaffId() + ")");
            Toast.makeText(this, "Employee ID verified: " + user.getName(), Toast.LENGTH_SHORT).show();

            showStep(2);  // Proceed to fingerprint authentication

        } else if (syncedUser != null) {
            // Employee ID found in synced_fingerprints table
            enteredEmployeeId = employeeId;
            expectedUser = null;
            expectedSyncedUser = syncedUser;

            Log.i(TAG, "✓ Employee ID verified: " + syncedUser.getName() + " (Employee Number: " + syncedUser.getEmployeeNumber() + ")");
            Toast.makeText(this, "Employee ID verified: " + syncedUser.getName(), Toast.LENGTH_SHORT).show();

            showStep(2);  // Proceed to fingerprint authentication

        } else {
            // Employee ID not found
            Log.w(TAG, "✗ Employee ID not found: " + employeeId);
            Toast.makeText(this, "Employee ID not found!\nPlease check your employee ID and try again.", Toast.LENGTH_LONG).show();
        }
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
                        setupCategorySpinner(profiles);
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
                        String userMessage = "⚠ Cannot connect to API\nPlease check network connection";
                        Toast.makeText(ManualOverrideActivity.this,
                            userMessage,
                            Toast.LENGTH_LONG).show();
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
     * Load manual override configuration list from server.
     * Stored in overrideConfigurations; the RadioGroup in Step 4 is populated when that step is shown.
     * Falls back gracefully if the API is unavailable (RadioGroup will be empty, default days used).
     */
    private void loadOverrideConfiguration() {
        manualOverrideService.getOverrideConfiguration(new ApiCallback<List<ManualOverrideConfigurationResponse>>() {
            @Override
            public void onSuccess(List<ManualOverrideConfigurationResponse> configs) {
                if (configs != null && !configs.isEmpty()) {
                    overrideConfigurations = configs;
                    Log.i(TAG, "Override config list loaded: " + configs.size() + " items");

                    // Pre-select the default entry so maxOverrideDurationDays is ready
                    for (ManualOverrideConfigurationResponse c : configs) {
                        if (c.isDefault()) {
                            selectedConfiguration = c;
                            maxOverrideDurationDays = c.getMaxOverrideDurationDays();
                            Log.i(TAG, "Default config: id=" + c.getId() + ", maxDays=" + maxOverrideDurationDays);
                            break;
                        }
                    }
                    // If no isDefault entry, fall back to first item
                    if (selectedConfiguration == null) {
                        selectedConfiguration = configs.get(0);
                        maxOverrideDurationDays = selectedConfiguration.getMaxOverrideDurationDays();
                        Log.w(TAG, "No default config found, using first item: maxDays=" + maxOverrideDurationDays);
                    }
                } else {
                    Log.w(TAG, "Override config list is empty, keeping defaults");
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Could not load override config list, using defaults: " + error);
            }
        });
    }

    /**
     * Populate the Spinner in Step 4 with all available override configurations.
     * Pre-selects the default entry (isDefault=true) or the previously chosen one.
     */
    private void populateConfigurationSelector() {
        List<String> labels = new ArrayList<>();
        int defaultPosition = 0;

        if (overrideConfigurations.isEmpty()) {
            labels.add("Default (" + maxOverrideDurationDays + " day(s))");
        } else {
            for (int i = 0; i < overrideConfigurations.size(); i++) {
                ManualOverrideConfigurationResponse config = overrideConfigurations.get(i);
                labels.add(config.getMaxOverrideDurationDays() + " day(s)" + (config.isDefault() ? "  (Default)" : ""));

                boolean isCurrent = (selectedConfiguration != null && config.getId() == selectedConfiguration.getId())
                        || (selectedConfiguration == null && config.isDefault());
                if (isCurrent) {
                    defaultPosition = i;
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOverrideDuration.setAdapter(adapter);
        spinnerOverrideDuration.setSelection(defaultPosition, false);

        spinnerOverrideDuration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (overrideConfigurations.isEmpty()) return;
                ManualOverrideConfigurationResponse config = overrideConfigurations.get(position);
                selectedConfiguration = config;
                maxOverrideDurationDays = config.getMaxOverrideDurationDays();
                tvEndTimeLabel.setText("End Time (Max " + maxOverrideDurationDays + " day(s)):");
                // Reset end time when duration changes
                selectedEndTimeMillis = 0;
                tvSelectedEndTime.setText("Not selected");
                tvSelectedEndTime.setTextColor(getResources().getColor(R.color.textSecondary));
                btnProceedToVerification.setEnabled(false);
                Log.d(TAG, "Override duration selected: " + maxOverrideDurationDays + " days");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Perform a live API check of isAllowedOverride before allowing the user to proceed
     * to vault selection. Falls back to the cached DB value if the API is unreachable.
     */
    private void checkOverridePermissionThenProceed() {
        String employeeNumber = (verifiedSyncedUser != null)
                ? verifiedSyncedUser.getEmployeeNumber()
                : (verifiedUser != null ? verifiedUser.getStaffId() : null);

        if (employeeNumber == null) {
            Toast.makeText(this, "Cannot verify permissions: employee not identified", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button and show loading while checking
        btnProceedToVaultSelection.setEnabled(false);
        btnProceedToVaultSelection.setText("Checking permissions...");

        fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
            new ApiCallback<com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse>() {
                @Override
                public void onSuccess(com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse response) {
                    // Find the employee in the fresh response
                    com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData freshEmployee = null;
                    if (response.getEmployees() != null) {
                        for (com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData e : response.getEmployees()) {
                            if (employeeNumber.equalsIgnoreCase(e.getStaffID())) {
                                freshEmployee = e;
                                break;
                            }
                        }
                    }

                    final boolean liveAllowed = (freshEmployee != null && freshEmployee.isAllowedOverride());

                    // Persist fresh value to DB and in-memory object
                    if (freshEmployee != null) {
                        dbHelper.updateIsAllowedOverrideByEmployeeNumber(employeeNumber, liveAllowed);
                        if (verifiedSyncedUser != null) verifiedSyncedUser.setAllowedOverride(liveAllowed);
                    }

                    runOnUiThread(() -> {
                        btnProceedToVaultSelection.setEnabled(true);
                        btnProceedToVaultSelection.setText("Proceed to Vault Selection");

                        if (liveAllowed) {
                            Log.i(TAG, "Live check: override ALLOWED for " + employeeNumber);
                            showStep(3);
                        } else {
                            Log.w(TAG, "Live check: override DENIED for " + employeeNumber);
                            tvFingerprintStatus.setText("❌ Override Not Permitted\n\nYour account is not authorised to perform manual vault override.");
                            tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(ManualOverrideActivity.this,
                                "Access Denied: You are not authorised to perform manual override",
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    // API unreachable — fall back to cached DB value (already updated at fingerprint step if API was reachable then)
                    Log.w(TAG, "Live override check failed, falling back to cached value: " + error);
                    runOnUiThread(() -> {
                        btnProceedToVaultSelection.setEnabled(true);
                        btnProceedToVaultSelection.setText("Proceed to Vault Selection");

                        boolean cachedAllowed = (verifiedSyncedUser != null && verifiedSyncedUser.isAllowedOverride());
                        if (cachedAllowed) {
                            Log.i(TAG, "Cached check: override ALLOWED for " + employeeNumber);
                            showStep(3);
                        } else {
                            Log.w(TAG, "Cached check: override DENIED for " + employeeNumber);
                            tvFingerprintStatus.setText("❌ Override Not Permitted\n\nYour account is not authorised to perform manual vault override.");
                            tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(ManualOverrideActivity.this,
                                "Access Denied: You are not authorised to perform manual override",
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        );
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
     * Set up the vault category Spinner based on categories found in the loaded profiles.
     * When a category is selected, the vault buttons below are filtered accordingly.
     * Falls back gracefully if profiles are empty or have no category data.
     */
    private void setupCategorySpinner(List<ControllerProfileResponse> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            spinnerVaultCategory.setVisibility(View.GONE);
            showFallbackVaultButtons();
            return;
        }

        // Collect distinct categories from vaultInfo, preserving insertion order
        LinkedHashSet<String> categoriesSet = new LinkedHashSet<>();
        for (ControllerProfileResponse profile : profiles) {
            VaultInfo info = profile.getVaultInfo();
            if (info != null && info.getVaultCategory() != null && !info.getVaultCategory().isEmpty()) {
                categoriesSet.add(info.getVaultCategory());
            }
        }

        if (categoriesSet.isEmpty()) {
            // No category data from API — show all vaults directly without the spinner
            spinnerVaultCategory.setVisibility(View.GONE);
            populateVaultButtons(profiles);
            return;
        }

        spinnerVaultCategory.setVisibility(View.VISIBLE);

        List<String> categories = new ArrayList<>();
        categories.add("-- Select Vault Category --");
        categories.addAll(categoriesSet);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVaultCategory.setAdapter(adapter);

        spinnerVaultCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // Prompt row — clear vault buttons until a real category is chosen
                    vaultButtonContainer.removeAllViews();
                    return;
                }

                String selectedCategory = categories.get(position);
                Log.d(TAG, "Vault category selected: " + selectedCategory);

                List<ControllerProfileResponse> filtered = new ArrayList<>();
                for (ControllerProfileResponse profile : profiles) {
                    VaultInfo info = profile.getVaultInfo();
                    if (info != null && selectedCategory.equals(info.getVaultCategory())) {
                        filtered.add(profile);
                    }
                }

                Log.d(TAG, "Filtered vaults for '" + selectedCategory + "': " + filtered.size());
                populateVaultButtons(filtered);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                vaultButtonContainer.removeAllViews();
            }
        });

        // Clear buttons until the user picks a category
        vaultButtonContainer.removeAllViews();
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

        // Set click listener - proceed to time frame selection (step 4)
        button.setOnClickListener(v -> {
            // Check if this door already has an active override
            if (isDoorAlreadyActivated(profile)) {
                VaultOverrideManager.VaultOverride existing =
                        VaultOverrideManager.getInstance(this).getVaultOverride(
                                profile.getVaultName() != null && !profile.getVaultName().isEmpty()
                                        ? profile.getVaultName()
                                        : profile.getControllerVariableName());
                String endMsg = "Please deactivate it first.";
                if (existing != null && existing.endTimeMillis > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                    endMsg = "Active until: " + sdf.format(new java.util.Date(existing.endTimeMillis))
                            + "\nPlease deactivate it first.";
                }
                Toast.makeText(this, displayName + " is already overridden.\n" + endMsg,
                        Toast.LENGTH_LONG).show();
                return;
            }

            selectedProfile = profile;
            Log.d(TAG, "Selected door: " + displayName + " (Variable: " + profile.getControllerVariableName() + ")");

            // Proceed to time frame selection (step 4)
            showStep(4);
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
        if (profile == null) {
            return false;
        }

        // Use vaultName to check against VaultOverrideManager
        String vaultName = profile.getVaultName() != null && !profile.getVaultName().isEmpty()
                ? profile.getVaultName()
                : profile.getControllerVariableName();

        VaultOverrideManager manager = VaultOverrideManager.getInstance(this);
        return manager.isVaultOverridden(vaultName);
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
        step6Container.setVisibility(View.GONE);

        // All steps are sequential: 1, 2, 3, 4, 5, 6
        stepNumber.setText(String.valueOf(step));

        switch (step) {
            case 1:
                stepDescription.setText("Enter Employee ID");
                step1Container.setVisibility(View.VISIBLE);
                break;

            case 2:
                stepDescription.setText("Authenticate");
                step2Container.setVisibility(View.VISIBLE);

                // Display entered employee ID
                tvEmployeeIdDisplay.setText("Employee ID: " + enteredEmployeeId);

                // Reset fingerprint state when showing this step
                resetFingerprintState();
                break;

            case 3:
                stepDescription.setText("Select Vault");
                step3Container.setVisibility(View.VISIBLE);
                break;

            case 4:
                stepDescription.setText("Set Time Frame");
                step4Container.setVisibility(View.VISIBLE);

                // Display selected vault
                tvTimeFrameVaultDisplay.setText(getDoorDisplayName());

                // Show current time as start time
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                tvStartTime.setText(sdf.format(new java.util.Date()));

                // Re-fetch configuration from server so any server-side updates are
                // applied without needing to return to the main menu.
                manualOverrideService.getOverrideConfiguration(new ApiCallback<List<ManualOverrideConfigurationResponse>>() {
                    @Override
                    public void onSuccess(List<ManualOverrideConfigurationResponse> configs) {
                        if (configs != null && !configs.isEmpty()) {
                            overrideConfigurations = configs;
                            // Re-apply default selection
                            selectedConfiguration = null;
                            for (ManualOverrideConfigurationResponse c : configs) {
                                if (c.isDefault()) {
                                    selectedConfiguration = c;
                                    maxOverrideDurationDays = c.getMaxOverrideDurationDays();
                                    break;
                                }
                            }
                            if (selectedConfiguration == null) {
                                selectedConfiguration = configs.get(0);
                                maxOverrideDurationDays = selectedConfiguration.getMaxOverrideDurationDays();
                            }
                            Log.d(TAG, "Step 4: refreshed config, maxDays=" + maxOverrideDurationDays);
                        }
                        runOnUiThread(() -> {
                            populateConfigurationSelector();
                            tvEndTimeLabel.setText("End Time (Max " + maxOverrideDurationDays + " day(s)):");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Step 4: failed to refresh config, using cached values: " + error);
                        runOnUiThread(() -> {
                            populateConfigurationSelector();
                            tvEndTimeLabel.setText("End Time (Max " + maxOverrideDurationDays + " day(s)):");
                        });
                    }
                });

                // Reset end time selection
                selectedEndTimeMillis = 0;
                tvSelectedEndTime.setText("Not selected");
                tvSelectedEndTime.setTextColor(getResources().getColor(R.color.textSecondary));
                btnProceedToVerification.setEnabled(false);
                break;

            case 5:
                stepDescription.setText("Turn Key Switches");
                step5Container.setVisibility(View.VISIBLE);

                // Display vault name
                tvKeySwitchVaultName.setText(getDoorDisplayName());
                tvKeySwitchVaultName.setTextSize(28);
                tvKeySwitchVaultName.setTextColor(getResources().getColor(android.R.color.white));

                // Set instructions
                tvKeySwitchInstructions.setText("Please turn all 3 key switches to activate manual override");

                // Initialize key switch UI
                resetKeySwitchUI();

                // Store door name for event filtering
                selectedDoorName = selectedProfile.getVaultName();
                Log.d(TAG, "Monitoring key switches for Door: " + selectedDoorName);

                // Show waiting message
                keySwitchProgress.setVisibility(View.VISIBLE);
                tvKeySwitchWaitingMessage.setVisibility(View.VISIBLE);
                tvKeySwitchWaitingMessage.setText("Waiting for key switches to be turned...");
                break;

            case 6:
                stepDescription.setText("Complete");
                step6Container.setVisibility(View.VISIBLE);
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
        ivKeySwitch1Icon.setAlpha(1.0f);
        ivKeySwitch1Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
        keySwitch1Container.setBackgroundColor(0x40FF0000); // Red tint

        // Key Switch 2
        tvKeySwitch2Status.setText("OFF - Waiting...");
        tvKeySwitch2Status.setTextColor(getResources().getColor(R.color.textSecondary));
        ivKeySwitch2Icon.setAlpha(1.0f);
        ivKeySwitch2Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
        keySwitch2Container.setBackgroundColor(0x40FF0000); // Red tint

        // Key Switch 3
        tvKeySwitch3Status.setText("OFF - Waiting...");
        tvKeySwitch3Status.setTextColor(getResources().getColor(R.color.textSecondary));
        ivKeySwitch3Icon.setAlpha(1.0f);
        ivKeySwitch3Icon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
        keySwitch3Container.setBackgroundColor(0x40FF0000); // Red tint
    }

    /**
     * Handle individual key switch event from SignalR
     */
    private void handleKeySwitchEvent(com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto event) {
        // Filter by door name - only process events for the selected vault
        if (selectedDoorName == null || !selectedDoorName.equals(event.getDoorName())) {
            Log.d(TAG, "Ignoring key switch event for different door: " + event.getDoorName());
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
        // Filter by door name
        if (selectedDoorName == null || !selectedDoorName.equals(event.getDoorName())) {
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
            icon.setAlpha(1.0f);
            icon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
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

        // Use controller variable name directly (e.g., "MAIN.SOFT_LOCK_A")
        String variableName = selectedProfile.getControllerVariableName();
        Log.d(TAG, "║ Controller Variable: " + variableName);

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
                variableName,                   // Controller variable name (e.g., "MAIN.SOFT_LOCK_A")
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
                case "H": return 8;
                case "I": return 9;
                case "J": return 10;
                case "K": return 11;
                case "L": return 12;
                default:
                    Log.w(TAG, "Unknown door letter: " + doorLetter);
                    return -1;
            }
        }

        Log.w(TAG, "Could not extract door ID from variable: " + variableName);
        return -1;
    }

    private void showSuccessResult() {
        showStep(6);

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
        showStep(6);

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
        if (currentStep == 2) {
            // From fingerprint, go back to employee ID (step 1)
            resetEmployeeVerificationState();  // Reset all verification state
            showStep(1);
        } else if (currentStep == 3) {
            // From vault selection, go back to fingerprint (step 2)
            resetFingerprintState();  // Reset only fingerprint state
            showStep(2);
        } else if (currentStep == 4) {
            // From time frame, go back to vault selection (step 3)
            showStep(3);
        } else if (currentStep == 5) {
            // From key switch monitoring, go back to time frame (step 4)
            showStep(4);
        } else if (currentStep == 6) {
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
        if (selectedConfiguration == null && overrideConfigurations.isEmpty()) {
            // No configuration loaded yet — still usable with default maxOverrideDurationDays
            Log.w(TAG, "No override configuration loaded, using default maxDays=" + maxOverrideDurationDays);
        } else if (selectedConfiguration == null) {
            Toast.makeText(this, "Please select an override duration first", Toast.LENGTH_SHORT).show();
            return;
        }

        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        final long maxDurationMs = (long) maxOverrideDurationDays * 24 * 60 * 60 * 1000;

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

                                // Validate: must not exceed server-configured max duration
                                if (durationMillis > maxDurationMs) {
                                    Toast.makeText(this, "Maximum override duration is " + maxOverrideDurationDays + " day(s)", Toast.LENGTH_SHORT).show();
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

        // Set maximum date based on server-configured max duration
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis() + maxDurationMs);

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

        // Use controller variable name directly
        String variableName = selectedProfile.getControllerVariableName();
        String vaultName = getDoorDisplayName();
        long startTimeMillis = System.currentTimeMillis();

        // Get verified employee information
        String employeeName = "Unknown";
        String staffId = "Unknown";

        if (verifiedUser != null) {
            employeeName = verifiedUser.getName();
            staffId = verifiedUser.getStaffId();
        } else if (verifiedSyncedUser != null) {
            employeeName = verifiedSyncedUser.getName();
            staffId = verifiedSyncedUser.getEmployeeNumber();
        }

        Log.d(TAG, "Creating override with authenticated employee: " + employeeName + " (" + staffId + ")");
        Log.d(TAG, "NOTE: Using 'Key Switch Authorized' for custodian fields (required for key switch monitoring)");

        // Use "Key Switch Authorized" as placeholder for custodian fields
        // This is required for the backend to properly detect key switch monitoring
        // The employee authentication is recorded in the "requestedBy" field
        manualOverrideService.createOverrideProfile(
                variableName,             // Controller variable name (e.g., "MAIN.SOFT_LOCK_A")
                vaultName,
                "Key Switch Authorized",  // custodian1 - placeholder for key switch
                "Key Switch Authorized",  // custodian2 - placeholder for key switch
                "Key Switch Authorized",  // custodian3 - placeholder for key switch
                "",                       // department parameter
                startTimeMillis,
                selectedEndTimeMillis,
                "Mobile Kiosk - " + employeeName + " (" + staffId + ")",  // Authenticated employee recorded here
                new ManualOverrideCallback() {
                    @Override
                    public void onProfileCreated(com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse profile) {
                        runOnUiThread(() -> {
                            Log.i(TAG, "✓ Override profile created successfully: " + profile.getProfileId());
                            Log.i(TAG, "  → Profile ID: " + profile.getProfileId());
                            Log.i(TAG, "  → Status: " + profile.getStatus());
                            Log.i(TAG,"  → Door: " + getDoorDisplayName());

                            // Re-enable button
                            btnProceedToVerification.setEnabled(true);
                            btnProceedToVerification.setText("Proceed to Verification");

                            // Show success dialog with navigation options
                            showOverrideSuccessDialog(profile);
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

    // =================== FINGERPRINT AUTHENTICATION (NEW) ===================

    /**
     * Start fingerprint scanning for employee authentication
     */
    private void startFingerprintScan() {
        if (sdk == null) {
            tvFingerprintStatus.setText("Scanner not initialized. Please restart the app.");
            tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            return;
        }

        if (isScanning) {
            Toast.makeText(this, "Scan already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset UI
        tvFingerprintStatus.setText("Place your finger on the scanner...");
        tvFingerprintStatus.setTextColor(getResources().getColor(R.color.textPrimary));
        fingerprintProgress.setVisibility(View.VISIBLE);
        btnScanFingerprint.setEnabled(false);
        verifiedEmployeeContainer.setVisibility(View.GONE);

        isScanning = true;

        executor.execute(() -> {
            try {
                // Cancel any previous operations
                sdk.UF_Cancel(false);
                Thread.sleep(100);

                // Reconnect to scanner
                sdk.UF_Reconnect();
                Thread.sleep(200);

                mainHandler.post(() -> tvFingerprintStatus.setText("Scanning fingerprint..."));

                // Scan and identify
                int[] userID = new int[1];
                byte[] subID = new byte[1];
                byte[] templateData = new byte[3840];
                int[] templateSize = new int[1];
                int[] imageQuality = new int[1];

                Log.d(TAG, "Starting fingerprint identification...");
                UF_RET_CODE ret = sdk.UF_ScanTemplate(templateData, templateSize, imageQuality);

                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    ret = sdk.UF_IdentifyTemplate(384, templateData, userID, subID);
                    Log.d(TAG, "Identification result: " + ret + ", User ID: " + userID[0]);
                }

                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    int scannedScannerID = userID[0];
                    Log.d(TAG, "Fingerprint identified - Scanner ID: " + scannedScannerID);

                    // Look up user in database
                    DatabaseHelper.User user = dbHelper.getUserByScannerUserId(scannedScannerID);
                    DatabaseHelper.SyncedFingerprint syncedUser = null;

                    if (user == null) {
                        Log.d(TAG, "Not found in users table, checking synced_fingerprints...");
                        syncedUser = dbHelper.getUserByScannerId(scannedScannerID);
                    }

                    final DatabaseHelper.User finalUser = user;
                    final DatabaseHelper.SyncedFingerprint finalSyncedUser = syncedUser;

                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        btnScanFingerprint.setEnabled(true);

                        // Cancel scanner operation
                        try {
                            sdk.UF_Cancel(false);
                        } catch (Exception e) {
                            Log.e(TAG, "Error cancelling scanner", e);
                        }
                        isScanning = false;

                        if (finalUser != null || finalSyncedUser != null) {
                            // Fingerprint found - now verify it matches the entered employee ID
                            String scannedEmployeeName = finalUser != null ? finalUser.getName() : finalSyncedUser.getName();
                            String scannedStaffId = finalUser != null ? finalUser.getStaffId() : finalSyncedUser.getEmployeeNumber();

                            Log.d(TAG, "Scanned fingerprint belongs to: " + scannedEmployeeName + " (" + scannedStaffId + ")");
                            Log.d(TAG, "Expected employee ID: " + enteredEmployeeId);

                            // Verify the scanned fingerprint matches the entered employee ID
                            boolean isMatch = false;
                            if (finalUser != null && expectedUser != null) {
                                // Both from users table - compare staff IDs
                                isMatch = finalUser.getStaffId().equalsIgnoreCase(expectedUser.getStaffId());
                            } else if (finalSyncedUser != null && expectedSyncedUser != null) {
                                // Both from synced_fingerprints table - compare employee numbers
                                isMatch = finalSyncedUser.getEmployeeNumber().equalsIgnoreCase(expectedSyncedUser.getEmployeeNumber());
                            } else if (finalUser != null && expectedSyncedUser != null) {
                                // User from users table, expected from synced - compare IDs
                                isMatch = finalUser.getStaffId().equalsIgnoreCase(expectedSyncedUser.getEmployeeNumber());
                            } else if (finalSyncedUser != null && expectedUser != null) {
                                // User from synced, expected from users - compare IDs
                                isMatch = finalSyncedUser.getEmployeeNumber().equalsIgnoreCase(expectedUser.getStaffId());
                            }

                            if (isMatch) {
                                // SUCCESS: Fingerprint matches the entered employee ID
                                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                                Log.d(TAG, "║ FINGERPRINT AUTHENTICATION SUCCESS");
                                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                                Log.d(TAG, "║ Employee: " + scannedEmployeeName);
                                Log.d(TAG, "║ Staff ID: " + scannedStaffId);

                                // STEP 1: Check role authorization (reject Custodian role)
                                String userRole = null;
                                String userSource = null;

                                if (finalSyncedUser != null) {
                                    userRole = finalSyncedUser.getRole();
                                    userSource = "synced_fingerprints table (from API)";
                                    Log.d(TAG, "║ User Source: " + userSource);
                                    Log.d(TAG, "║ Role: " + (userRole != null ? "'" + userRole + "'" : "NULL"));
                                    Log.d(TAG, "║ Employee Number: " + finalSyncedUser.getEmployeeNumber());
                                    Log.d(TAG, "║ Username: " + finalSyncedUser.getUsername());
                                } else if (finalUser != null) {
                                    userSource = "users table (local enrollment)";
                                    Log.d(TAG, "║ User Source: " + userSource);
                                    Log.d(TAG, "║ Role: N/A (local users don't have role field)");
                                    Log.d(TAG, "║ Staff ID: " + finalUser.getStaffId());
                                    Log.d(TAG, "║ Department: " + finalUser.getDepartment());
                                }
                                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                                // STEP 2: Live API check for isAllowedOverride so stale DB values don't block
                                // users whose permission was recently updated on the server.
                                tvFingerprintStatus.setText("Fingerprint matched. Checking permissions...");
                                tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.white));

                                String employeeNumberForCheck = (finalSyncedUser != null)
                                        ? finalSyncedUser.getEmployeeNumber()
                                        : (finalUser != null ? finalUser.getStaffId() : null);

                                final String finalUserRole = userRole;

                                if (employeeNumberForCheck == null) {
                                    Log.w(TAG, "✗ AUTHORIZATION FAILED: cannot determine employee number");
                                    tvFingerprintStatus.setText("❌ Override Not Permitted\n\nCould not verify employee identity.");
                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                    return;
                                }

                                // Use the same all-employees endpoint that reset-scanner uses — confirmed working.
                                // The single-employee endpoint (/employee-fingerprints/{id}) may not exist on this backend.
                                fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
                                    new com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback<com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse>() {
                                        @Override
                                        public void onSuccess(com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse response) {
                                            // Find this employee in the fresh response
                                            com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData freshEmployee = null;
                                            if (response.getEmployees() != null) {
                                                for (com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse.EmployeeFingerprintData e : response.getEmployees()) {
                                                    if (employeeNumberForCheck.equalsIgnoreCase(e.getStaffID())) {
                                                        freshEmployee = e;
                                                        break;
                                                    }
                                                }
                                            }

                                            if (freshEmployee == null) {
                                                Log.w(TAG, "Employee not found in fresh API response: " + employeeNumberForCheck);
                                                runOnUiThread(() -> {
                                                    tvFingerprintStatus.setText("❌ Employee not found in server data.\nPlease contact administrator.");
                                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                                });
                                                return;
                                            }

                                            final boolean liveAllowed = freshEmployee.isAllowedOverride();
                                            Log.d(TAG, "Live override check for " + employeeNumberForCheck + ": isAllowedOverride=" + liveAllowed);

                                            // Persist the fresh value to DB so subsequent checks are also correct
                                            dbHelper.updateIsAllowedOverrideByEmployeeNumber(employeeNumberForCheck, liveAllowed);

                                            // Update the in-memory object so the Proceed button fallback is also fresh
                                            if (finalSyncedUser != null) finalSyncedUser.setAllowedOverride(liveAllowed);

                                            runOnUiThread(() -> {
                                                if (liveAllowed) {
                                                    isFingerprintVerified = true;
                                                    verifiedUser = finalUser;
                                                    verifiedSyncedUser = finalSyncedUser;

                                                    Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                                                    Log.i(TAG, "║ AUTHORIZATION CHECK PASSED (live)");
                                                    Log.i(TAG, "╠════════════════════════════════════════════════════════════");
                                                    Log.i(TAG, "║ Role: '" + finalUserRole + "'");
                                                    Log.i(TAG, "║ Authorization: ✓ GRANTED");
                                                    Log.i(TAG, "╚════════════════════════════════════════════════════════════");

                                                    tvFingerprintStatus.setText("✓ Fingerprint Verified!");
                                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                                                    tvVerifiedEmployeeName.setText(scannedEmployeeName);
                                                    tvVerifiedEmployeeId.setText("Staff ID: " + scannedStaffId);
                                                    verifiedEmployeeContainer.setVisibility(View.VISIBLE);

                                                    btnProceedToVaultSelection.setEnabled(true);
                                                    btnProceedToVaultSelection.setBackgroundTintList(getResources().getColorStateList(R.color.colorAccent));

                                                    Toast.makeText(ManualOverrideActivity.this, "✓ Authenticated as " + scannedEmployeeName, Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Log.w(TAG, "✗ AUTHORIZATION FAILED (live): isAllowedOverride=false for " + scannedEmployeeName);
                                                    tvFingerprintStatus.setText("❌ Override Not Permitted\n\nYour account is not authorised to perform manual vault override.");
                                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                                    Toast.makeText(ManualOverrideActivity.this,
                                                        "Access Denied: You are not authorised to perform manual override",
                                                        Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        }

                                        @Override
                                        public void onError(String error) {
                                            // API unreachable — fall back to cached DB value
                                            Log.w(TAG, "Live override check failed, falling back to cached DB value: " + error);
                                            runOnUiThread(() -> {
                                                boolean cachedAllowed = (finalSyncedUser != null && finalSyncedUser.isAllowedOverride());
                                                if (cachedAllowed) {
                                                    isFingerprintVerified = true;
                                                    verifiedUser = finalUser;
                                                    verifiedSyncedUser = finalSyncedUser;

                                                    Log.i(TAG, "║ AUTHORIZATION CHECK PASSED (cached fallback)");
                                                    tvFingerprintStatus.setText("✓ Fingerprint Verified!");
                                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                                                    tvVerifiedEmployeeName.setText(scannedEmployeeName);
                                                    tvVerifiedEmployeeId.setText("Staff ID: " + scannedStaffId);
                                                    verifiedEmployeeContainer.setVisibility(View.VISIBLE);

                                                    btnProceedToVaultSelection.setEnabled(true);
                                                    btnProceedToVaultSelection.setBackgroundTintList(getResources().getColorStateList(R.color.colorAccent));

                                                    Toast.makeText(ManualOverrideActivity.this, "✓ Authenticated as " + scannedEmployeeName, Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Log.w(TAG, "✗ AUTHORIZATION FAILED (cached fallback): isAllowedOverride=false for " + scannedEmployeeName);
                                                    tvFingerprintStatus.setText("❌ Override Not Permitted\n\nYour account is not authorised to perform manual vault override.");
                                                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                                    Toast.makeText(ManualOverrideActivity.this,
                                                        "Access Denied: You are not authorised to perform manual override",
                                                        Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        }
                                    }
                                );

                            } else {
                                // MISMATCH: Fingerprint does NOT match the entered employee ID
                                Log.w(TAG, "✗ Fingerprint mismatch! Scanned: " + scannedStaffId + ", Expected: " + enteredEmployeeId);

                                tvFingerprintStatus.setText("❌ Fingerprint Mismatch!\nThis fingerprint belongs to:\n" + scannedEmployeeName + "\n\nExpected employee ID: " + enteredEmployeeId);
                                tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                Toast.makeText(ManualOverrideActivity.this, "Fingerprint does not match employee ID " + enteredEmployeeId, Toast.LENGTH_LONG).show();
                            }

                        } else {
                            // Not found in database at all
                            tvFingerprintStatus.setText("❌ Fingerprint not recognized\nPlease try again");
                            tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(ManualOverrideActivity.this, "Fingerprint not registered", Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    // Identification failed
                    Log.e(TAG, "Fingerprint identification failed: " + ret);

                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        btnScanFingerprint.setEnabled(true);

                        try {
                            sdk.UF_Cancel(false);
                        } catch (Exception e) {
                            Log.e(TAG, "Error cancelling scanner", e);
                        }
                        isScanning = false;

                        tvFingerprintStatus.setText("❌ Fingerprint not recognized\nPlease try again");
                        tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        Toast.makeText(ManualOverrideActivity.this, "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during fingerprint scan", e);

                mainHandler.post(() -> {
                    fingerprintProgress.setVisibility(View.GONE);
                    btnScanFingerprint.setEnabled(true);

                    try {
                        sdk.UF_Cancel(false);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error cancelling scanner", ex);
                    }
                    isScanning = false;

                    tvFingerprintStatus.setText("Error: " + e.getMessage());
                    tvFingerprintStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    Toast.makeText(ManualOverrideActivity.this, "Error during scan", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Reset fingerprint authentication state
     */
    private void resetFingerprintState() {
        isFingerprintVerified = false;
        verifiedUser = null;
        verifiedSyncedUser = null;
        // Don't reset expectedUser/expectedSyncedUser here - we need them for validation

        tvFingerprintStatus.setText("Tap button below to scan fingerprint");
        tvFingerprintStatus.setTextColor(getResources().getColor(R.color.textPrimary));
        verifiedEmployeeContainer.setVisibility(View.GONE);
        fingerprintProgress.setVisibility(View.GONE);
        btnScanFingerprint.setEnabled(true);
        btnProceedToVaultSelection.setEnabled(false);
        btnProceedToVaultSelection.setBackgroundTintList(getResources().getColorStateList(R.color.buttonBackground));
    }

    /**
     * Reset all employee verification state (when going back to Step 1)
     */
    private void resetEmployeeVerificationState() {
        enteredEmployeeId = "";
        expectedUser = null;
        expectedSyncedUser = null;
        verifiedUser = null;
        verifiedSyncedUser = null;
        isFingerprintVerified = false;
    }

    // =================== SUCCESS DIALOG ===================

    /**
     * Show success dialog after profile created with navigation options
     */
    private void showOverrideSuccessDialog(com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse profile) {
        String message = "Override profile activated successfully!\n\n" +
                        "Door: " + getDoorDisplayName() + "\n" +
                        "Profile ID: " + profile.getProfileId() + "\n\n" +
                        "Go to the vault and turn all 3 key switches to open the door.\n\n" +
                        "You can monitor the key switch status in real-time from the Vault Status page.";

        new AlertDialog.Builder(this)
            .setTitle("✓ Override Activated")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Vault Status", (dialog, which) -> {
                Log.d(TAG, "User chose to go to Vault Status page");

                // Navigate to MainVaultStatusActivity
                Intent intent = new Intent(ManualOverrideActivity.this, MainVaultStatusActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Main Menu", (dialog, which) -> {
                Log.d(TAG, "User chose to go to Main Menu");

                // Navigate to MainMenuActivity
                Intent intent = new Intent(ManualOverrideActivity.this, MainMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            })
            .show();
    }

    // =================== LIFECYCLE ===================

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

        // Cancel any ongoing fingerprint scan
        if (sdk != null && isScanning) {
            try {
                sdk.UF_Cancel(false);
                Log.d(TAG, "Cancelled ongoing fingerprint scan");
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling fingerprint scan", e);
            }
        }

        // Close database
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
