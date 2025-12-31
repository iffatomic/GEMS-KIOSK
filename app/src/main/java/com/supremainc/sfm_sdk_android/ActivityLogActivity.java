/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.graphics.Color;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileListItem;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.data.model.response.VaultIncidentLog;
import com.supremainc.sfm_sdk_android.network.api.ManualOverrideApiClient;
import com.supremainc.sfm_sdk_android.network.api.VaultIncidentLogApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Activity to display system activity logs
 * Shows attendance records and vault override actions
 */
public class ActivityLogActivity extends AppCompatActivity {

    private static final String TAG = "ActivityLogActivity";

    // UI Elements
    private Button btnBack;
    private TextView logCount;
    private LinearLayout logListContainer;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private Spinner spinnerFilter;

    // Data
    private DatabaseHelper dbHelper;
    private StaffEnrollmentService staffEnrollmentService;
    private VaultIncidentLogApiClient vaultIncidentLogApiClient;
    private ManualOverrideApiClient manualOverrideApiClient;
    private SimpleDateFormat iso8601Formatter;  // For parsing ISO-8601 from API
    private SimpleDateFormat displayDateFormatter;  // For displaying to user

    // Filter state
    private String currentFilter = "ALL"; // ALL, ENROLLMENT, OVERRIDE, VAULT_INCIDENT

    // Cache for loaded data
    private List<ActivityLogItem> allLogs = new ArrayList<>();
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // Initialize services and formatters
        dbHelper = new DatabaseHelper(this);
        staffEnrollmentService = new StaffEnrollmentService(this);
        vaultIncidentLogApiClient = new VaultIncidentLogApiClient(this);
        manualOverrideApiClient = new ManualOverrideApiClient(this);

        // ISO-8601 formatter for parsing timestamps from API
        iso8601Formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

        // Display formatter for showing timestamps to user
        displayDateFormatter = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());

        initializeViews();
        setupListeners();
        loadActivityLogs();
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btn_back);
        logCount = findViewById(R.id.log_count);
        logListContainer = findViewById(R.id.log_list_container);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.progress_bar);
        spinnerFilter = findViewById(R.id.spinner_filter);

        // Setup spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.activity_log_filters, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Map spinner position to filter type
                switch (position) {
                    case 0: currentFilter = "ALL"; break;
                    case 1: currentFilter = "ENROLLMENT"; break;
                    case 2: currentFilter = "OVERRIDE"; break;
                    case 3: currentFilter = "VAULT_INCIDENT"; break;
                    default: currentFilter = "ALL";
                }
                loadActivityLogs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep current filter
            }
        });
    }

    /**
     * Load and display activity logs from API
     */
    private void loadActivityLogs() {
        if (isLoading) {
            Log.d(TAG, "Already loading, skipping duplicate request");
            return;
        }

        isLoading = true;
        showLoading();
        allLogs.clear();

        Log.d(TAG, "Loading activity logs from API (filter: " + currentFilter + ")");

        // Track how many API calls we're waiting for
        final int[] pendingCalls = {0};

        // Determine which calls to make based on filter
        boolean loadEnrollments = currentFilter.equals("ALL") || currentFilter.equals("ENROLLMENT");
        boolean loadOverrides = currentFilter.equals("ALL") || currentFilter.equals("OVERRIDE");
        boolean loadVaultIncidents = currentFilter.equals("ALL") || currentFilter.equals("VAULT_INCIDENT");

        if (loadEnrollments) {
            pendingCalls[0] += 2; // Admin + Custodian
        }
        if (loadOverrides) {
            pendingCalls[0] += 1; // Override profiles from profiles-list
        }
        if (loadVaultIncidents) {
            pendingCalls[0] += 1; // Vault incident logs
        }

        final int totalCalls = pendingCalls[0];

        // Runnable to check if all calls are complete
        Runnable checkComplete = () -> {
            pendingCalls[0]--;
            Log.d(TAG, "API call completed. Remaining: " + pendingCalls[0] + "/" + totalCalls);

            if (pendingCalls[0] <= 0) {
                isLoading = false;
                hideLoading();
                displayAllLogs();
            }
        };

        // Load enrollment logs
        if (loadEnrollments) {
            // Get enrolled admins
            staffEnrollmentService.getEnrolledAdminUsers(new StaffEnrollmentCallback() {
                @Override
                public void onPendingUsersRetrieved(List<UserListItem> users) {
                    Log.i(TAG, "Retrieved " + users.size() + " enrolled admin(s)");
                    for (UserListItem user : users) {
                        ActivityLogItem logItem = new ActivityLogItem();
                        logItem.type = "ENROLLMENT";
                        logItem.title = "Admin Enrolled";
                        logItem.description = user.getName() + " (" + user.getEmployeeNumber() + ")";
                        logItem.timestamp = parseIso8601Timestamp(user.getCreatedAt());
                        logItem.icon = "✓";
                        logItem.userItem = user;
                        allLogs.add(logItem);
                    }
                    checkComplete.run();
                }

                @Override
                public void onEnrollmentError(String error) {
                    Log.e(TAG, "Error loading admin enrollments: " + error);
                    runOnUiThread(() -> Toast.makeText(ActivityLogActivity.this,
                        "Error loading admin logs: " + error, Toast.LENGTH_SHORT).show());
                    checkComplete.run();
                }

                @Override public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
                @Override public void onUserValidated(UserListItem user) {}
                @Override public void onUserNotFound() {}
                @Override public void onUserDeleted() {}
                @Override public void onFingerprintValidated(UserListItem user) {}
                @Override public void onFingerprintNotFound() {}
                @Override public void onSyncStarted(int totalFingerprints) {}
                @Override public void onSyncProgress(int current, int total, String userName) {}
                @Override public void onSyncCompleted(int successCount, int failCount) {}
            });

            // Get enrolled custodians
            staffEnrollmentService.getEnrolledCustodianUsers(new StaffEnrollmentCallback() {
                @Override
                public void onPendingUsersRetrieved(List<UserListItem> users) {
                    Log.i(TAG, "Retrieved " + users.size() + " enrolled custodian(s)");
                    for (UserListItem user : users) {
                        ActivityLogItem logItem = new ActivityLogItem();
                        logItem.type = "ENROLLMENT";
                        logItem.title = "Custodian Enrolled";
                        logItem.description = user.getName() + " (" + user.getEmployeeNumber() + ")";
                        logItem.timestamp = parseIso8601Timestamp(user.getCreatedAt());
                        logItem.icon = "✓";
                        logItem.userItem = user;
                        allLogs.add(logItem);
                    }
                    checkComplete.run();
                }

                @Override
                public void onEnrollmentError(String error) {
                    Log.e(TAG, "Error loading custodian enrollments: " + error);
                    runOnUiThread(() -> Toast.makeText(ActivityLogActivity.this,
                        "Error loading custodian logs: " + error, Toast.LENGTH_SHORT).show());
                    checkComplete.run();
                }

                @Override public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
                @Override public void onUserValidated(UserListItem user) {}
                @Override public void onUserNotFound() {}
                @Override public void onUserDeleted() {}
                @Override public void onFingerprintValidated(UserListItem user) {}
                @Override public void onFingerprintNotFound() {}
                @Override public void onSyncStarted(int totalFingerprints) {}
                @Override public void onSyncProgress(int current, int total, String userName) {}
                @Override public void onSyncCompleted(int successCount, int failCount) {}
            });
        }

        // Load override profile history
        if (loadOverrides) {
            manualOverrideApiClient.getProfilesList(new ApiCallback<List<ManualOverrideProfileListItem>>() {
                @Override
                public void onSuccess(List<ManualOverrideProfileListItem> profiles) {
                    Log.i(TAG, "Retrieved " + profiles.size() + " override profile(s)");
                    for (ManualOverrideProfileListItem profile : profiles) {
                        ActivityLogItem logItem = new ActivityLogItem();
                        logItem.type = "OVERRIDE";
                        logItem.title = "Manual Override";

                        // Build description using vaultName (profiles-list includes this)
                        String description = profile.getVaultName() != null ? profile.getVaultName() : "Unknown Vault";

                        // Add custodian info
                        if (profile.getCustodian1() != null) {
                            description += " by " + profile.getCustodian1();
                        }

                        // Add status
                        if (profile.getStatus() != null) {
                            description += " (" + profile.getStatus() + ")";
                        }
                        logItem.description = description;

                        // Use createdAt timestamp
                        logItem.timestamp = parseIso8601Timestamp(profile.getCreatedAt());

                        // Set icon based on status
                        if (profile.getStatus() != null) {
                            switch (profile.getStatus()) {
                                case "Active": logItem.icon = "✓"; break;
                                case "Deactivated": logItem.icon = "⏸"; break;
                                case "Expired": logItem.icon = "⏹"; break;
                                case "Completed": logItem.icon = "✔"; break;
                                case "Pending": logItem.icon = "⏳"; break;
                                default: logItem.icon = "🔓"; break;
                            }
                        } else {
                            logItem.icon = "🔓";
                        }

                        logItem.overrideProfileListItem = profile;
                        allLogs.add(logItem);
                    }
                    checkComplete.run();
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading override profiles: " + error);
                    runOnUiThread(() -> Toast.makeText(ActivityLogActivity.this,
                        "Error loading override history: " + error, Toast.LENGTH_SHORT).show());
                    checkComplete.run();
                }
            });
        }

        // Load vault incident logs
        if (loadVaultIncidents) {
            vaultIncidentLogApiClient.getRecentLogs(50, new ApiCallback<ApiResponse<List<VaultIncidentLog>>>() {
                @Override
                public void onSuccess(ApiResponse<List<VaultIncidentLog>> response) {
                    if (response.isSuccess() && response.hasData()) {
                        List<VaultIncidentLog> logs = response.getData();
                        Log.i(TAG, "Retrieved " + logs.size() + " vault incident log(s)");
                        for (VaultIncidentLog log : logs) {
                            ActivityLogItem logItem = new ActivityLogItem();
                            logItem.type = "VAULT_INCIDENT";
                            logItem.title = log.getEventType() != null ? log.getEventType() : "Vault Incident";

                            // Build description
                            String description = log.getVaultName() != null ? log.getVaultName() : "Unknown Vault";
                            if (log.getDoorState() != null) {
                                description += " - " + log.getDoorState();
                            }
                            if (log.getSeverity() != null) {
                                description += " (" + log.getSeverity() + ")";
                            }
                            logItem.description = description;

                            // Use occurredAt if available, otherwise use createdAt
                            String timestamp = log.getOccurredAt() != null ?
                                              log.getOccurredAt() : log.getCreatedAt();
                            logItem.timestamp = parseIso8601Timestamp(timestamp);

                            // Set icon based on severity
                            if (log.getSeverity() != null) {
                                switch (log.getSeverity()) {
                                    case "Critical": logItem.icon = "🚨"; break;
                                    case "High": logItem.icon = "⚠️"; break;
                                    case "Medium": logItem.icon = "🔔"; break;
                                    case "Low": logItem.icon = "ℹ️"; break;
                                    default: logItem.icon = "🔓"; break;
                                }
                            } else {
                                logItem.icon = "🔓";
                            }

                            logItem.vaultIncidentLog = log;
                            allLogs.add(logItem);
                        }
                    } else {
                        Log.w(TAG, "No vault incident logs returned");
                    }
                    checkComplete.run();
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading vault incident logs: " + error);
                    runOnUiThread(() -> Toast.makeText(ActivityLogActivity.this,
                        "Error loading vault incident logs: " + error, Toast.LENGTH_SHORT).show());
                    checkComplete.run();
                }
            });
        }
    }


    /**
     * Display all loaded logs
     */
    private void displayAllLogs() {
        // Sort logs by timestamp (newest first)
        Collections.sort(allLogs, (a, b) -> Long.compare(b.timestamp, a.timestamp));

        // Update UI
        runOnUiThread(() -> {
            logCount.setText("Total Logs: " + allLogs.size());

            if (allLogs.isEmpty()) {
                showEmptyState();
            } else {
                hideEmptyState();
                displayLogs(allLogs);
            }
        });
    }

    /**
     * Get human-readable vault name from variable name
     */
    private String getVaultNameFromVariable(String variableName) {
        if (variableName == null) return "Unknown Vault";

        switch (variableName) {
            case "MAIN.SOFT_LOCK_A": return "Main Vault - Door A";
            case "MAIN.SOFT_LOCK_B": return "Main Vault - Door B";
            case "MAIN.SOFT_LOCK_C": return "Main Vault - Door C";
            case "MAIN.SOFT_LOCK_D": return "Day Vault - Door D";
            case "MAIN.SOFT_LOCK_E": return "Day Vault - Door E";
            case "MAIN.SOFT_LOCK_F": return "Day Vault - Door F";
            case "MAIN.SOFT_LOCK_G": return "Day Vault - Door G";
            default: return variableName;
        }
    }

    /**
     * Display log items in the list
     */
    private void displayLogs(List<ActivityLogItem> logs) {
        logListContainer.removeAllViews();

        for (ActivityLogItem log : logs) {
            addLogCard(log);
        }
    }

    /**
     * Add a log card to the list
     */
    private void addLogCard(ActivityLogItem log) {
        // Create card
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(4);
        cardView.setRadius(8);
        cardView.setCardBackgroundColor(getResources().getColor(R.color.colorSurface));
        cardView.setContentPadding(16, 16, 16, 16);

        // Content layout
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);

        // Icon
        TextView iconText = new TextView(this);
        iconText.setText(log.icon);
        iconText.setTextSize(24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconParams.setMargins(0, 0, 16, 0);
        iconText.setLayoutParams(iconParams);
        contentLayout.addView(iconText);

        // Text content
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        // Title
        TextView titleText = new TextView(this);
        titleText.setText(log.title);
        titleText.setTextSize(16);
        titleText.setTextColor(getResources().getColor(R.color.textPrimary));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        textLayout.addView(titleText);

        // Description
        TextView descText = new TextView(this);
        descText.setText(log.description);
        descText.setTextSize(14);
        descText.setTextColor(getResources().getColor(R.color.textSecondary));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, 4, 0, 4);
        descText.setLayoutParams(descParams);
        textLayout.addView(descText);

        // Timestamp
        TextView timeText = new TextView(this);
        timeText.setText(formatTimestamp(log.timestamp));
        timeText.setTextSize(12);
        timeText.setTextColor(getResources().getColor(R.color.textSecondary));
        textLayout.addView(timeText);

        contentLayout.addView(textLayout);

        // Type badge
        TextView typeBadge = new TextView(this);
        typeBadge.setText(log.type);
        typeBadge.setTextSize(10);
        typeBadge.setTextColor(Color.WHITE);

        // Set different colors for different log types
        int badgeColor;
        if (log.type.equals("ENROLLMENT")) {
            badgeColor = getResources().getColor(R.color.holo_green_dark);
        } else if (log.type.equals("OVERRIDE")) {
            badgeColor = getResources().getColor(R.color.colorAccent);
        } else if (log.type.equals("VAULT_INCIDENT")) {
            badgeColor = getResources().getColor(R.color.holo_orange_dark);
        } else {
            badgeColor = getResources().getColor(R.color.colorPrimary);
        }
        typeBadge.setBackgroundColor(badgeColor);
        typeBadge.setPadding(12, 6, 12, 6);
        typeBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        contentLayout.addView(typeBadge);

        cardView.addView(contentLayout);

        // Make override and vault incident items clickable to show details
        if (log.type.equals("OVERRIDE") && log.overrideProfileListItem != null) {
            cardView.setClickable(true);
            cardView.setFocusable(true);
            cardView.setForeground(getResources().getDrawable(android.R.drawable.list_selector_background));
            cardView.setOnClickListener(v -> showOverrideProfileListDetailsDialog(log.overrideProfileListItem));
        } else if (log.type.equals("VAULT_INCIDENT") && log.vaultIncidentLog != null) {
            cardView.setClickable(true);
            cardView.setFocusable(true);
            cardView.setForeground(getResources().getDrawable(android.R.drawable.list_selector_background));
            cardView.setOnClickListener(v -> showVaultIncidentDetailsDialog(log.vaultIncidentLog));
        }

        logListContainer.addView(cardView);
    }

    /**
     * Show override profile list details dialog
     */
    private void showOverrideProfileListDetailsDialog(ManualOverrideProfileListItem profile) {
        // Create dialog view
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(24, 24, 24, 24);

        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Override Details");
        titleText.setTextSize(20);
        titleText.setTextColor(getResources().getColor(R.color.textPrimary));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 16);
        titleText.setLayoutParams(titleParams);
        dialogView.addView(titleText);

        // Vault info
        addDetailRow(dialogView, "Vault/Door:", profile.getVaultName() != null ? profile.getVaultName() : "N/A");
        addDetailRow(dialogView, "Profile ID:", profile.getId());
        addDetailRow(dialogView, "Status:", profile.getStatus() != null ? profile.getStatus() : "N/A");

        // Custodian info
        addDetailRow(dialogView, "\nCustodians:", "");
        addDetailRow(dialogView, "Custodian 1:", profile.getCustodian1() != null ? profile.getCustodian1() : "N/A");
        addDetailRow(dialogView, "Custodian 2:", profile.getCustodian2() != null ? profile.getCustodian2() : "N/A");
        addDetailRow(dialogView, "Custodian 3:", profile.getCustodian3() != null ? profile.getCustodian3() : "N/A");

        // Timestamps
        addDetailRow(dialogView, "\nTimestamps:", "");
        addDetailRow(dialogView, "Created At:", formatIso8601Display(profile.getCreatedAt()));

        if (profile.getActivatedAt() != null && !profile.getActivatedAt().isEmpty()) {
            addDetailRow(dialogView, "Activated At:", formatIso8601Display(profile.getActivatedAt()));
        }

        if (profile.getDeactivatedAt() != null && !profile.getDeactivatedAt().isEmpty()) {
            addDetailRow(dialogView, "Deactivated At:", formatIso8601Display(profile.getDeactivatedAt()));
        }

        // Create and show dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Style the dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.container_background);
        }

        dialog.show();
    }

    /**
     * Show override details dialog (for full ManualOverrideProfileResponse - not currently used)
     */
    private void showOverrideDetailsDialog(ManualOverrideProfileResponse profile) {
        // Create dialog view
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(24, 24, 24, 24);

        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Override Details");
        titleText.setTextSize(20);
        titleText.setTextColor(getResources().getColor(R.color.textPrimary));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 16);
        titleText.setLayoutParams(titleParams);
        dialogView.addView(titleText);

        // Vault info
        String vaultName = getVaultNameFromVariable(profile.getVariableName());
        addDetailRow(dialogView, "Vault/Door:", vaultName);
        addDetailRow(dialogView, "Variable Name:", profile.getVariableName());
        addDetailRow(dialogView, "Status:", profile.getStatus());

        // Custodian info
        addDetailRow(dialogView, "\nCustodian:", "");
        addDetailRow(dialogView, "Custodian 1:", profile.getCustodian1() != null ? profile.getCustodian1() : "N/A");
        addDetailRow(dialogView, "Custodian 2:", profile.getCustodian2() != null ? profile.getCustodian2() : "N/A");
        addDetailRow(dialogView, "Custodian 3:", profile.getCustodian3() != null ? profile.getCustodian3() : "N/A");

        // Timestamps
        addDetailRow(dialogView, "\nTimestamps:", "");
        addDetailRow(dialogView, "Created At:", formatIso8601Display(profile.getCreatedAt()));

        if (profile.getActivatedAt() != null && !profile.getActivatedAt().isEmpty()) {
            addDetailRow(dialogView, "Activated At:", formatIso8601Display(profile.getActivatedAt()));
        }

        if (profile.getStartDateTime() != null && !profile.getStartDateTime().isEmpty()) {
            addDetailRow(dialogView, "Start Time:", formatIso8601Display(profile.getStartDateTime()));
        }

        if (profile.getEndDateTime() != null && !profile.getEndDateTime().isEmpty()) {
            addDetailRow(dialogView, "End Time:", formatIso8601Display(profile.getEndDateTime()));
        }

        if (profile.getCompletedAt() != null && !profile.getCompletedAt().isEmpty()) {
            addDetailRow(dialogView, "Completed At:", formatIso8601Display(profile.getCompletedAt()));
        }

        // Requested by
        if (profile.getRequestedBy() != null && !profile.getRequestedBy().isEmpty()) {
            addDetailRow(dialogView, "\nRequested By:", profile.getRequestedBy());
        }

        // Create and show dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Style the dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.container_background);
        }

        dialog.show();
    }

    /**
     * Show vault incident log details dialog
     */
    private void showVaultIncidentDetailsDialog(VaultIncidentLog log) {
        // Create dialog view
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(24, 24, 24, 24);

        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Vault Incident Details");
        titleText.setTextSize(20);
        titleText.setTextColor(getResources().getColor(R.color.textPrimary));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 16);
        titleText.setLayoutParams(titleParams);
        dialogView.addView(titleText);

        // Vault info
        addDetailRow(dialogView, "Vault Name:", log.getVaultName() != null ? log.getVaultName() : "N/A");
        addDetailRow(dialogView, "Location:", log.getLocation() != null ? log.getLocation() : "N/A");
        addDetailRow(dialogView, "Event Type:", log.getEventType() != null ? log.getEventType() : "N/A");
        addDetailRow(dialogView, "Door State:", log.getDoorState() != null ? log.getDoorState() : "N/A");
        addDetailRow(dialogView, "Severity:", log.getSeverity() != null ? log.getSeverity() : "N/A");

        // Timestamp
        if (log.getOccurredAt() != null && !log.getOccurredAt().isEmpty()) {
            addDetailRow(dialogView, "\nOccurred At:", formatIso8601Display(log.getOccurredAt()));
        }

        // Create and show dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Style the dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.container_background);
        }

        dialog.show();
    }

    /**
     * Add a detail row to the dialog
     */
    private void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 8);
        row.setLayoutParams(rowParams);

        // Label
        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextSize(14);
        labelText.setTextColor(getResources().getColor(R.color.textSecondary));
        labelText.setTypeface(null, android.graphics.Typeface.BOLD);
        labelText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.4f
        );
        labelText.setLayoutParams(labelParams);
        row.addView(labelText);

        // Value
        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(14);
        valueText.setTextColor(getResources().getColor(R.color.textPrimary));
        valueText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.6f
        );
        valueText.setLayoutParams(valueParams);
        row.addView(valueText);

        parent.addView(row);
    }

    /**
     * Show loading state
     */
    private void showLoading() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            logListContainer.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        });
    }

    /**
     * Hide loading state
     */
    private void hideLoading() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
        });
    }

    /**
     * Show empty state message
     */
    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        logListContainer.setVisibility(View.GONE);
    }

    /**
     * Hide empty state message
     */
    private void hideEmptyState() {
        emptyState.setVisibility(View.GONE);
        logListContainer.setVisibility(View.VISIBLE);
    }

    /**
     * Parse ISO-8601 timestamp string to milliseconds
     */
    private long parseIso8601Timestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0;
        }

        try {
            // Clean up timestamp - remove 'Z' or timezone offset if present
            String cleanTimestamp = timestamp.trim();

            // Remove 'Z' suffix if present
            if (cleanTimestamp.endsWith("Z")) {
                cleanTimestamp = cleanTimestamp.substring(0, cleanTimestamp.length() - 1);
            }

            // Remove timezone offset like "+08:00" or "-05:00" if present
            if (cleanTimestamp.matches(".*[+-]\\d{2}:\\d{2}$")) {
                cleanTimestamp = cleanTimestamp.substring(0, cleanTimestamp.length() - 6);
            }

            // Remove milliseconds if present
            if (cleanTimestamp.contains(".")) {
                cleanTimestamp = cleanTimestamp.substring(0, cleanTimestamp.indexOf('.'));
            }

            Date date = iso8601Formatter.parse(cleanTimestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
            return 0;
        }
    }

    /**
     * Format timestamp to readable string
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "N/A";
        }
        return displayDateFormatter.format(new Date(timestamp));
    }

    /**
     * Format ISO-8601 timestamp for display
     */
    private String formatIso8601Display(String iso8601Timestamp) {
        long timestamp = parseIso8601Timestamp(iso8601Timestamp);
        return formatTimestamp(timestamp);
    }

    /**
     * Activity log item data class
     */
    private static class ActivityLogItem {
        String type;        // ENROLLMENT, OVERRIDE, VAULT_INCIDENT
        String title;
        String description;
        long timestamp;
        String icon;
        UserListItem userItem; // For enrollment items
        ManualOverrideProfileResponse overrideProfile; // For full override profile items (not used currently)
        ManualOverrideProfileListItem overrideProfileListItem; // For override profile list items
        VaultIncidentLog vaultIncidentLog; // For vault incident log items
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActivityLogs();
    }
}
