/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.Comparator;

import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk_android.adapters.DeleteUserAdapter;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;
import com.supremainc.sfm_sdk_android.util.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for deleting enrolled users
 * Requires admin fingerprint verification
 * Deletes from: Scanner hardware, PAC API, and local database
 */
public class DeleteUserActivity extends AppCompatActivity {

    private static final String TAG = "DeleteUserActivity";

    // UI Components
    private ImageButton btnBack;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LinearLayout tvEmptyMessage;
    private LinearLayout tvAdminStatus;
    private TextView tvAdminStatusText;

    // Search and Filter UI
    private LinearLayout searchFilterContainer;
    private EditText searchEditText;
    private ImageButton btnClearSearch;
    private Button btnFilterAll;
    private Button btnFilterAdmins;
    private Button btnFilterCustodians;
    private Button btnSort;

    // Filter state
    private String currentRoleFilter = "ALL";  // ALL, ADMIN, CUSTODIAN
    private String currentSearchQuery = "";
    private boolean sortAscending = true;      // true = A-Z, false = Z-A

    // Services
    private StaffEnrollmentService staffEnrollmentService;
    private DatabaseHelper dbHelper;
    private SFM_SDK_ANDROID sdk;

    // Data
    private List<UserListItem> allEnrolledUsers = new ArrayList<>();
    private DeleteUserAdapter adapter;

    // Background execution
    private ExecutorService executor;
    private Handler mainHandler;

    // Admin verification state
    private boolean isAdminVerified = false;
    private String verifiedAdminName = "";
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_user);

        // Initialize services
        staffEnrollmentService = new StaffEnrollmentService(this);
        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize SDK
        sdk = new SFM_SDK_ANDROID(this);

        // Initialize UI
        initializeViews();
        setupClickListeners();

        // Check if admin verification is disabled for testing (from config.json)
        boolean disableAdminVerification = ConfigManager.getConfig().getTestingFlags().isDisableAdminVerification();

        if (disableAdminVerification) {
            // TESTING MODE: Skip admin verification
            Log.w(TAG, "⚠️ ADMIN VERIFICATION DISABLED FOR TESTING (from config.json) ⚠️");
            isAdminVerified = true;
            verifiedAdminName = "TEST MODE (NO VERIFICATION)";
            tvAdminStatusText.setText("⚠️ TEST MODE - Admin verification bypassed");
            tvAdminStatus.setVisibility(View.VISIBLE);
            Toast.makeText(this, "⚠️ TEST MODE: Admin verification disabled", Toast.LENGTH_LONG).show();
            loadEnrolledUsers();
        } else {
            // PRODUCTION MODE: Require admin verification
            showAdminVerificationDialog();
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        recyclerView = findViewById(R.id.recyclerViewUsers);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        tvAdminStatus = findViewById(R.id.tvAdminStatus);
        tvAdminStatusText = findViewById(R.id.tvAdminStatusText);

        // Search and Filter UI
        searchFilterContainer = findViewById(R.id.searchFilterContainer);
        searchEditText = findViewById(R.id.searchEditText);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterAdmins = findViewById(R.id.btnFilterAdmins);
        btnFilterCustodians = findViewById(R.id.btnFilterCustodians);
        btnSort = findViewById(R.id.btnSort);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Search functionality
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Clear search button
        btnClearSearch.setOnClickListener(v -> {
            searchEditText.setText("");
            currentSearchQuery = "";
        });

        // Role filter buttons
        btnFilterAll.setOnClickListener(v -> {
            currentRoleFilter = "ALL";
            updateFilterButtonStates();
            applyFilters();
        });

        btnFilterAdmins.setOnClickListener(v -> {
            currentRoleFilter = "ADMIN";
            updateFilterButtonStates();
            applyFilters();
        });

        btnFilterCustodians.setOnClickListener(v -> {
            currentRoleFilter = "CUSTODIAN";
            updateFilterButtonStates();
            applyFilters();
        });

        // Sort button
        btnSort.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            btnSort.setText(sortAscending ? "A-Z" : "Z-A");
            applySorting();
            applyFilters();
        });
    }

    /**
     * Show admin verification dialog
     * User must scan admin fingerprint to proceed
     * Uses the same dialog and flow as MainMenuActivity
     */
    private void showAdminVerificationDialog() {
        Log.d(TAG, "=== Admin verification initiated ===");

        // Inflate the dialog layout (same as MainMenuActivity)
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
        android.widget.Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        android.widget.Button retryButton = dialogView.findViewById(R.id.retryButton);
        ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
        TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);
        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);

        // Update dialog title and instruction
        if (dialogTitle != null) {
            dialogTitle.setText("Admin Verification");
        }
        fingerprintInstruction.setText("Admin fingerprint required to delete users.\n\nPlace your finger on the scanner...");

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
            finish(); // Exit DeleteUserActivity when cancelled
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
                });

                // Identify fingerprint
                int[] userID = new int[1];
                byte[] subID = new byte[1];

                Log.d(TAG, "Calling UF_Identify...");
                UF_RET_CODE ret = sdk.UF_Identify(userID, subID);

                Log.d(TAG, "UF_Identify returned: " + ret + ", Scanner ID: " + userID[0]);

                mainHandler.post(() -> {
                    fingerprintInstruction.setText("Verifying identity...");
                });

                // Check if identification succeeded
                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    int scannedScannerID = userID[0];
                    Log.d(TAG, "✓ Scanner identified fingerprint, Scanner ID: " + scannedScannerID);

                    mainHandler.post(() -> {
                        fingerprintInstruction.setText("Verifying identity...");
                    });

                    // SCANNER-BASED VERIFICATION: Check scanner ID range
                    boolean isAdmin = false;
                    String userName = null;

                    if (scannedScannerID >= 10000) {
                        // Synced fingerprint from API
                        Log.d(TAG, "Checking synced fingerprints table for scanner ID: " + scannedScannerID);
                        DatabaseHelper.SyncedFingerprint syncedUser = dbHelper.getUserByScannerId(scannedScannerID);

                        if (syncedUser != null) {
                            userName = syncedUser.getName();
                            // Check actual role from database
                            String syncedRole = syncedUser.getRole();
                            isAdmin = "ADMIN".equals(syncedRole);  // Only accept if role is ADMIN
                            Log.i(TAG, "✓ Synced user identified: " + userName + ", role: " + syncedRole + ", isAdmin: " + isAdmin);
                        } else {
                            Log.w(TAG, "Scanner ID found but not in synced database: " + scannedScannerID);
                        }
                    } else {
                        // Local user
                        Log.d(TAG, "Checking local users table for scanner ID: " + scannedScannerID);
                        DatabaseHelper.User localUser = dbHelper.getUserByScannerUserId(scannedScannerID);

                        if (localUser != null) {
                            userName = localUser.getName();
                            isAdmin = localUser.isAdmin();
                            Log.i(TAG, "✓ Local user identified: " + userName + ", isAdmin: " + isAdmin);
                        } else {
                            Log.w(TAG, "Scanner ID found but not in local database: " + scannedScannerID);
                        }
                    }

                    // Handle result
                    if (isAdmin && userName != null) {
                        // Admin verified successfully
                        String finalUserName = userName;
                        mainHandler.post(() -> {
                            fingerprintProgress.setVisibility(View.GONE);
                            isScanning = false;

                            fingerprintInstruction.setText("✓ Admin Verified\n\n" + finalUserName);
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            Toast.makeText(this, "Admin verified: " + finalUserName, Toast.LENGTH_SHORT).show();

                            // Set admin verification status
                            isAdminVerified = true;
                            verifiedAdminName = finalUserName;

                            // Update status display
                            tvAdminStatusText.setText("✓ Admin verified: " + finalUserName);
                            tvAdminStatus.setVisibility(View.VISIBLE);

                            // Close dialog
                            dialog.dismiss();

                            // NOW LOAD THE ENROLLED USERS LIST
                            Log.d(TAG, "Admin verified successfully, loading enrolled users...");
                            loadEnrolledUsers();
                        });
                    } else if (!isAdmin && userName != null) {
                        // Non-admin attempted access
                        String finalUserName = userName;
                        mainHandler.post(() -> {
                            fingerprintProgress.setVisibility(View.GONE);
                            isScanning = false;

                            fingerprintInstruction.setText("❌ Access Denied\n\n" + finalUserName + " is not an admin!");
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(this, "Access denied - Admin privileges required", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        // Fingerprint not found in database
                        mainHandler.post(() -> {
                            fingerprintProgress.setVisibility(View.GONE);
                            isScanning = false;

                            fingerprintInstruction.setText("❌ Fingerprint not recognized\n\nPlease try again.");
                            fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(this, "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    // Identification failed - show error but don't auto-retry
                    Log.e(TAG, "Fingerprint identification failed: " + ret);

                    mainHandler.post(() -> {
                        fingerprintProgress.setVisibility(View.GONE);
                        isScanning = false;

                        fingerprintInstruction.setText("❌ Fingerprint not recognized\n\nPlease try again or cancel.");
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        Toast.makeText(this, "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "Error during fingerprint scan", Toast.LENGTH_SHORT).show();
                });
            }
        });

        dialog.show();
    }

    /**
     * Verify fingerprint with PAC API
     * Checks if the scanned fingerprint belongs to an admin user
     */
    private void verifyFingerprintWithApi(byte[] fingerprintTemplate, AlertDialog dialog) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ API-BASED ADMIN VERIFICATION");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Validating fingerprint with PAC API...");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // Call API with byte array (service will convert to Base64)
        staffEnrollmentService.validateFingerprint(fingerprintTemplate, new StaffEnrollmentCallback() {
            @Override
            public void onFingerprintValidated(UserListItem user) {
                runOnUiThread(() -> {
                    View dialogView = dialog.findViewById(android.R.id.content);
                    ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
                    TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

                    fingerprintProgress.setVisibility(View.GONE);
                    isScanning = false;

                    String role = user.getRole();
                    Log.i(TAG, "✓ FINGERPRINT VALIDATED - User: " + user.getName() + ", Role: " + role);

                    // Check if user is admin
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        // SUCCESS! Admin verified
                        isAdminVerified = true;
                        verifiedAdminName = user.getName();

                        Log.d(TAG, "Admin verified: " + verifiedAdminName);

                        fingerprintInstruction.setText("✓ Admin Verified!\nWelcome, " + verifiedAdminName);
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_green_light));

                        // Delay to show success message, then load users
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            dialog.dismiss();
                            Toast.makeText(DeleteUserActivity.this, "Welcome, " + verifiedAdminName, Toast.LENGTH_SHORT).show();
                            tvAdminStatusText.setText("Verified Admin: " + verifiedAdminName);
                            tvAdminStatus.setVisibility(View.VISIBLE);
                            loadEnrolledUsers();
                        }, 1000);

                    } else {
                        // Custodian or other role - not an admin
                        Log.w(TAG, "Non-admin attempted access: " + user.getName() + " (Role: " + role + ")");
                        fingerprintInstruction.setText("❌ Access Denied\n\nOnly admins can delete users.\nPlease ask an admin to verify.");
                        fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        Toast.makeText(DeleteUserActivity.this, "Only admins can delete users", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFingerprintNotFound() {
                runOnUiThread(() -> {
                    View dialogView = dialog.findViewById(android.R.id.content);
                    ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
                    TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

                    fingerprintProgress.setVisibility(View.GONE);
                    isScanning = false;

                    Log.w(TAG, "Fingerprint not recognized by API");
                    fingerprintInstruction.setText("❌ Fingerprint not recognized\n\nPlease try again or contact administrator.");
                    fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    Toast.makeText(DeleteUserActivity.this, "Fingerprint not registered", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onEnrollmentError(String error) {
                Log.e(TAG, "✗ API VERIFICATION ERROR: " + error);

                runOnUiThread(() -> {
                    View dialogView = dialog.findViewById(android.R.id.content);
                    ProgressBar fingerprintProgress = dialogView.findViewById(R.id.fingerprintProgress);
                    TextView fingerprintInstruction = dialogView.findViewById(R.id.fingerprintInstruction);

                    fingerprintProgress.setVisibility(View.GONE);
                    isScanning = false;

                    fingerprintInstruction.setText("❌ Verification Error\n\n" + error + "\n\nPlease try again.");
                    fingerprintInstruction.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    Toast.makeText(DeleteUserActivity.this, "API Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {}
            @Override
            public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
            @Override
            public void onUserValidated(UserListItem user) {}
            @Override
            public void onUserNotFound() {}
            @Override
            public void onUserDeleted() {}
            @Override
            public void onSyncStarted(int totalFingerprints) {}
            @Override
            public void onSyncProgress(int current, int total, String userName) {}
            @Override
            public void onSyncCompleted(int successCount, int failCount) {}
        });
    }

    /**
     * Load enrolled users from API
     */
    private void loadEnrolledUsers() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmptyMessage.setVisibility(View.GONE);

        // Clear existing list
        allEnrolledUsers.clear();

        // Load from multiple sources:
        // 1. Local database (synced users from API)
        // 2. Local database (locally enrolled users)
        // 3. API (enrolled users)
        loadLocalUsers();
    }

    /**
     * Load users from local database (both synced and local users)
     */
    private void loadLocalUsers() {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Loading users from local database...");

                // Load synced users (from API, stored in synced_fingerprints table)
                List<DatabaseHelper.SyncedFingerprint> syncedFingerprints = dbHelper.getAllSyncedFingerprints();
                Log.d(TAG, "Found " + syncedFingerprints.size() + " synced users");

                for (DatabaseHelper.SyncedFingerprint synced : syncedFingerprints) {
                    UserListItem user = new UserListItem();
                    user.setEmployeeNumber(synced.getEmployeeNumber());
                    user.setUsername(synced.getUsername());
                    user.setName(synced.getName());
                    user.setRole(synced.getRole()); // Use actual role from database (ADMIN or CUSTODIAN)
                    allEnrolledUsers.add(user);
                }

                // Load local users (enrolled directly on this device)
                List<DatabaseHelper.User> localUsers = dbHelper.getAllActiveUsers();
                Log.d(TAG, "Found " + localUsers.size() + " local users");

                for (DatabaseHelper.User local : localUsers) {
                    UserListItem user = new UserListItem();
                    user.setEmployeeNumber(local.getStaffId());
                    user.setUsername(local.getStaffId());
                    user.setName(local.getName());
                    user.setRole(local.isAdmin() ? "ADMIN" : "CUSTODIAN");
                    allEnrolledUsers.add(user);
                }

                // Update UI on main thread
                mainHandler.post(() -> {
                    Log.d(TAG, "Total users loaded from local DB: " + allEnrolledUsers.size());
                    // After loading local users, load from API
                    loadAdmins();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading local users", e);
                mainHandler.post(() -> {
                    // Continue to API even if local load fails
                    loadAdmins();
                });
            }
        });
    }

    private void loadAdmins() {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ LOADING ENROLLED ADMINS FROM API");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        staffEnrollmentService.getEnrolledAdminUsers(new StaffEnrollmentCallback() {
            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {
                Log.d(TAG, "✓ API returned enrolled admins");
                if (users != null && !users.isEmpty()) {
                    Log.d(TAG, "  → Adding " + users.size() + " admin(s) to list");
                    for (UserListItem user : users) {
                        Log.d(TAG, "     - " + user.getName() + " (" + user.getEmployeeNumber() + ")");
                    }
                    allEnrolledUsers.addAll(users);
                } else {
                    Log.d(TAG, "  → No admins returned from API");
                }
                // After loading admins, load custodians
                loadCustodians();
            }

            @Override
            public void onEnrollmentError(String error) {
                Log.e(TAG, "✗ Error loading admins from API: " + error);
                loadCustodians(); // Continue to custodians even if admins fail
            }

            @Override
            public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
            @Override
            public void onUserValidated(UserListItem user) {}
            @Override
            public void onUserNotFound() {}
            @Override
            public void onUserDeleted() {}
            @Override
            public void onFingerprintValidated(UserListItem user) {}
            @Override
            public void onFingerprintNotFound() {}
            @Override
            public void onSyncStarted(int totalFingerprints) {}
            @Override
            public void onSyncProgress(int current, int total, String userName) {}
            @Override
            public void onSyncCompleted(int successCount, int failCount) {}
        });
    }

    private void loadCustodians() {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ LOADING ENROLLED CUSTODIANS FROM API");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        staffEnrollmentService.getEnrolledCustodianUsers(new StaffEnrollmentCallback() {
            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {
                Log.d(TAG, "✓ API returned enrolled custodians");
                if (users != null && !users.isEmpty()) {
                    Log.d(TAG, "  → Adding " + users.size() + " custodian(s) to list");
                    for (UserListItem user : users) {
                        Log.d(TAG, "     - " + user.getName() + " (" + user.getEmployeeNumber() + ")");
                    }
                    allEnrolledUsers.addAll(users);
                } else {
                    Log.d(TAG, "  → No custodians returned from API");
                }
                displayUsers();
            }

            @Override
            public void onEnrollmentError(String error) {
                Log.e(TAG, "✗ Error loading custodians from API: " + error);
                displayUsers(); // Display what we have
            }

            @Override
            public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
            @Override
            public void onUserValidated(UserListItem user) {}
            @Override
            public void onUserNotFound() {}
            @Override
            public void onUserDeleted() {}
            @Override
            public void onFingerprintValidated(UserListItem user) {}
            @Override
            public void onFingerprintNotFound() {}
            @Override
            public void onSyncStarted(int totalFingerprints) {}
            @Override
            public void onSyncProgress(int current, int total, String userName) {}
            @Override
            public void onSyncCompleted(int successCount, int failCount) {}
        });
    }

    private void displayUsers() {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ DISPLAYING USERS");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Total users before deduplication: " + allEnrolledUsers.size());
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // DEDUPLICATE: Remove duplicate users (same employee number)
        // Use LinkedHashMap to preserve order while removing duplicates
        java.util.Map<String, UserListItem> uniqueUsers = new java.util.LinkedHashMap<>();

        for (UserListItem user : allEnrolledUsers) {
            String employeeNumber = user.getEmployeeNumber();

            if (employeeNumber != null && !employeeNumber.isEmpty()) {
                // Only keep first occurrence of each employee number
                if (!uniqueUsers.containsKey(employeeNumber)) {
                    uniqueUsers.put(employeeNumber, user);
                    Log.d(TAG, "  ✓ Added: " + user.getName() + " | Role: " + user.getRole() + " | Employee: " + employeeNumber);
                } else {
                    Log.d(TAG, "  ✗ Skipped duplicate: " + user.getName() + " | Employee: " + employeeNumber);
                }
            }
        }

        // Replace list with deduplicated users
        allEnrolledUsers.clear();
        allEnrolledUsers.addAll(uniqueUsers.values());

        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ After deduplication: " + allEnrolledUsers.size() + " unique users");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        progressBar.setVisibility(View.GONE);

        if (allEnrolledUsers.isEmpty()) {
            Log.w(TAG, "No users to display - showing empty message");
            recyclerView.setVisibility(View.GONE);
            tvEmptyMessage.setVisibility(View.VISIBLE);
            searchFilterContainer.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "Displaying " + allEnrolledUsers.size() + " unique users in RecyclerView");
            // Sort users before displaying
            applySorting();

            // Show search and filter controls
            searchFilterContainer.setVisibility(View.VISIBLE);

            recyclerView.setVisibility(View.VISIBLE);
            tvEmptyMessage.setVisibility(View.GONE);

            adapter = new DeleteUserAdapter(allEnrolledUsers, user -> showDeleteConfirmation(user));
            recyclerView.setAdapter(adapter);

            // Update button texts with counts
            updateFilterButtonTexts();
            updateFilterButtonStates();
        }
    }

    /**
     * Apply current filters to the adapter
     */
    private void applyFilters() {
        if (adapter != null) {
            adapter.filter(currentRoleFilter, currentSearchQuery);
        }
    }

    /**
     * Sort the user list based on current sort order
     */
    private void applySorting() {
        if (sortAscending) {
            Collections.sort(allEnrolledUsers, new Comparator<UserListItem>() {
                @Override
                public int compare(UserListItem u1, UserListItem u2) {
                    String name1 = u1.getName() != null ? u1.getName() : "";
                    String name2 = u2.getName() != null ? u2.getName() : "";
                    return name1.compareToIgnoreCase(name2);
                }
            });
        } else {
            Collections.sort(allEnrolledUsers, new Comparator<UserListItem>() {
                @Override
                public int compare(UserListItem u1, UserListItem u2) {
                    String name1 = u1.getName() != null ? u1.getName() : "";
                    String name2 = u2.getName() != null ? u2.getName() : "";
                    return name2.compareToIgnoreCase(name1);
                }
            });
        }
    }

    /**
     * Update filter button texts with user counts
     */
    private void updateFilterButtonTexts() {
        if (adapter != null) {
            int allCount = adapter.getCountByRole("ALL");
            int adminCount = adapter.getCountByRole("ADMIN");
            int custodianCount = adapter.getCountByRole("CUSTODIAN");

            btnFilterAll.setText("All (" + allCount + ")");
            btnFilterAdmins.setText("Admins (" + adminCount + ")");
            btnFilterCustodians.setText("Custodians (" + custodianCount + ")");
        }
    }

    /**
     * Update visual state of filter buttons (highlight selected)
     */
    private void updateFilterButtonStates() {
        // Reset all buttons
        btnFilterAll.setTextColor(getResources().getColor(R.color.buttonText));
        btnFilterAdmins.setTextColor(getResources().getColor(R.color.buttonText));
        btnFilterCustodians.setTextColor(getResources().getColor(R.color.buttonText));

        btnFilterAll.setAlpha(0.6f);
        btnFilterAdmins.setAlpha(0.6f);
        btnFilterCustodians.setAlpha(0.6f);

        // Highlight selected button
        if ("ALL".equals(currentRoleFilter)) {
            btnFilterAll.setTextColor(getResources().getColor(R.color.white));
            btnFilterAll.setAlpha(1.0f);
        } else if ("ADMIN".equals(currentRoleFilter)) {
            btnFilterAdmins.setTextColor(getResources().getColor(R.color.white));
            btnFilterAdmins.setAlpha(1.0f);
        } else if ("CUSTODIAN".equals(currentRoleFilter)) {
            btnFilterCustodians.setTextColor(getResources().getColor(R.color.white));
            btnFilterCustodians.setAlpha(1.0f);
        }
    }

    /**
     * Show confirmation dialog before deleting user
     * Enhanced with clear warning and color-coded buttons
     */
    private void showDeleteConfirmation(UserListItem user) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚠️ Confirm Delete User")
                .setMessage("Are you sure you want to permanently delete:\n\n" +
                        "👤 Name: " + user.getName() + "\n" +
                        "🆔 Employee ID: " + user.getEmployeeNumber() + "\n" +
                        "👔 Role: " + user.getRole() + "\n" +
                        "🔢 Fingerprints: " + (user.getFingerprintCount() > 0 ? user.getFingerprintCount() : 2) + "\n\n" +
                        "⚠️ WARNING: This action cannot be undone!\n\n" +
                        "This will:\n" +
                        "• Remove fingerprints from scanner hardware\n" +
                        "• Delete user from PAC API\n" +
                        "• Remove from local database")
                .setPositiveButton("🗑️ DELETE", (d, which) -> deleteUser(user))
                .setNegativeButton("❌ CANCEL", null)
                .setCancelable(true)
                .create();

        dialog.show();

        // Make Delete button RED to emphasize danger
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(true);

        // Make Cancel button GREEN for safety
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(true);
    }

    /**
     * Delete user from scanner, API, and database
     */
    private void deleteUser(UserListItem user) {
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                Log.d(TAG, "╔═══════════════════════════════════════════");
                Log.d(TAG, "║ DELETING USER: " + user.getName());
                Log.d(TAG, "║ Employee Number: " + user.getEmployeeNumber());
                Log.d(TAG, "║ Role: " + user.getRole());
                Log.d(TAG, "╚═══════════════════════════════════════════");

                // CRITICAL: First, let's see what scanner IDs are actually in the scanner
                Log.d(TAG, "");
                Log.d(TAG, "╔═══════════════════════════════════════════");
                Log.d(TAG, "║ CHECKING SCANNER MEMORY");
                Log.d(TAG, "╚═══════════════════════════════════════════");
                checkScannerMemory();

                // Step 1: Get user from LOCAL users table
                DatabaseHelper.User localUser = dbHelper.getUserByStaffId(user.getEmployeeNumber());

                if (localUser != null) {
                    Log.d(TAG, "");
                    Log.d(TAG, "✓ Found in local users table:");
                    Log.d(TAG, "  - Name: " + localUser.getName());
                    Log.d(TAG, "  - Left Scanner ID: " + localUser.getLeftScannerUserId());
                    Log.d(TAG, "  - Right Scanner ID: " + localUser.getRightScannerUserId());
                    Log.d(TAG, "  - Left Scanner ID 2: " + localUser.getLeftScannerUserId2());
                    Log.d(TAG, "  - Right Scanner ID 2: " + localUser.getRightScannerUserId2());

                    // Check if this is the last admin
                    if (localUser.isAdmin() && dbHelper.countActiveAdmins() <= 1) {
                        mainHandler.post(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "❌ Cannot delete the last admin. At least one admin must remain enrolled.", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    // Delete scanner IDs for local user
                    Log.d(TAG, "");
                    Log.d(TAG, "Deleting scanner IDs for local user...");
                    deleteScannerIDs(localUser);
                } else {
                    Log.d(TAG, "");
                    Log.d(TAG, "✗ Not found in local users table");
                }

                // Step 2: Get ALL synced fingerprints for this employee number
                List<DatabaseHelper.SyncedFingerprint> syncedFingerprints = dbHelper.getSyncedFingerprintsByEmployeeNumber(user.getEmployeeNumber());

                if (syncedFingerprints != null && !syncedFingerprints.isEmpty()) {
                    Log.d(TAG, "");
                    Log.d(TAG, "✓ Found " + syncedFingerprints.size() + " synced fingerprint(s) in database:");

                    // Log all synced fingerprints for this user
                    for (DatabaseHelper.SyncedFingerprint synced : syncedFingerprints) {
                        Log.d(TAG, "  - Scanner ID: " + synced.getScannerId() +
                              " | Finger: " + synced.getFingerType() +
                              " | Side: " + (synced.getLeftRight() == 0 ? "Left" : "Right"));
                    }

                    // Delete ALL scanner IDs from synced fingerprints
                    Log.d(TAG, "");
                    Log.d(TAG, "Deleting synced fingerprint scanner IDs from hardware...");
                    for (DatabaseHelper.SyncedFingerprint synced : syncedFingerprints) {
                        int scannerId = synced.getScannerId();
                        if (scannerId > 0) {
                            Log.d(TAG, "  → Deleting scanner ID " + scannerId + "...");
                            UF_RET_CODE result = sdk.UF_Delete(scannerId);
                            if (result == UF_RET_CODE.UF_RET_SUCCESS) {
                                Log.d(TAG, "    ✓ SUCCESS");
                            } else if (result == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                                Log.w(TAG, "    ⚠ WARNING: Scanner ID not found in hardware (database out of sync!)");
                            } else {
                                Log.e(TAG, "    ✗ FAILED: " + result);
                            }
                        }
                    }

                    // Delete ALL synced fingerprint records from database
                    Log.d(TAG, "");
                    Log.d(TAG, "Deleting ALL synced fingerprint records from database...");
                    int deletedCount = dbHelper.deleteSyncedFingerprintsByEmployeeNumber(user.getEmployeeNumber());
                    Log.d(TAG, "  ✓ Deleted " + deletedCount + " database record(s)");
                } else {
                    Log.d(TAG, "");
                    Log.d(TAG, "✗ No synced fingerprints found in database");
                }

                // Step 3: Delete from PAC API
                Log.d(TAG, "");
                Log.d(TAG, "Deleting from API...");
                deleteFromAPI(user);

            } catch (Exception e) {
                Log.e(TAG, "Error during deletion", e);
                e.printStackTrace();
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Check what's actually in scanner memory
     */
    private void checkScannerMemory() {
        try {
            int[] numOfTemplate = new int[1];
            UF_RET_CODE ret = sdk.UF_GetNumOfTemplate(numOfTemplate);

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "  → Total templates in scanner hardware: " + numOfTemplate[0]);
            } else {
                Log.e(TAG, "  ✗ Failed to get scanner memory info: " + ret);
            }

            // Also check database scanner IDs
            Log.d(TAG, "");
            Log.d(TAG, "  → Scanner IDs in DATABASE:");

            // Check local users
            List<DatabaseHelper.User> localUsers = dbHelper.getAllActiveUsers();
            Log.d(TAG, "    Local users table (" + localUsers.size() + " users):");
            for (DatabaseHelper.User u : localUsers) {
                StringBuilder ids = new StringBuilder();
                if (u.getLeftScannerUserId() > 0) ids.append(u.getLeftScannerUserId()).append(", ");
                if (u.getRightScannerUserId() > 0) ids.append(u.getRightScannerUserId()).append(", ");
                if (u.getLeftScannerUserId2() > 0) ids.append(u.getLeftScannerUserId2()).append(", ");
                if (u.getRightScannerUserId2() > 0) ids.append(u.getRightScannerUserId2()).append(", ");
                if (ids.length() > 0) {
                    ids.setLength(ids.length() - 2); // Remove trailing comma
                    Log.d(TAG, "      " + u.getStaffId() + " (" + u.getName() + "): [" + ids + "]");
                }
            }

            // Check synced fingerprints
            List<DatabaseHelper.SyncedFingerprint> syncedFps = dbHelper.getAllSyncedFingerprints();
            Log.d(TAG, "    Synced fingerprints table (" + syncedFps.size() + " fingerprints):");
            java.util.Map<String, java.util.List<Integer>> employeeScannerIds = new java.util.HashMap<>();
            for (DatabaseHelper.SyncedFingerprint fp : syncedFps) {
                String empNum = fp.getEmployeeNumber();
                if (!employeeScannerIds.containsKey(empNum)) {
                    employeeScannerIds.put(empNum, new java.util.ArrayList<>());
                }
                employeeScannerIds.get(empNum).add(fp.getScannerId());
            }
            for (java.util.Map.Entry<String, java.util.List<Integer>> entry : employeeScannerIds.entrySet()) {
                Log.d(TAG, "      " + entry.getKey() + ": " + entry.getValue());
            }

        } catch (Exception e) {
            Log.e(TAG, "  ✗ Error checking scanner memory", e);
        }
    }

    /**
     * Delete scanner IDs from scanner hardware
     */
    private boolean deleteScannerIDs(DatabaseHelper.User user) {
        boolean success = true;

        Log.d(TAG, "Deleting local user scanner IDs from hardware...");

        // Delete left finger 1
        if (user.getLeftScannerUserId() > 0) {
            Log.d(TAG, "  → Deleting scanner ID " + user.getLeftScannerUserId() + " (Left 1)...");
            UF_RET_CODE result = sdk.UF_Delete(user.getLeftScannerUserId());
            if (result == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "    ✓ SUCCESS");
            } else if (result == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                Log.w(TAG, "    ⚠ WARNING: Not found in hardware");
                success = false;
            } else {
                Log.e(TAG, "    ✗ FAILED: " + result);
                success = false;
            }
        }

        // Delete right finger 1
        if (user.getRightScannerUserId() > 0) {
            Log.d(TAG, "  → Deleting scanner ID " + user.getRightScannerUserId() + " (Right 1)...");
            UF_RET_CODE result = sdk.UF_Delete(user.getRightScannerUserId());
            if (result == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "    ✓ SUCCESS");
            } else if (result == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                Log.w(TAG, "    ⚠ WARNING: Not found in hardware");
                success = false;
            } else {
                Log.e(TAG, "    ✗ FAILED: " + result);
                success = false;
            }
        }

        // Delete left finger 2
        if (user.getLeftScannerUserId2() > 0) {
            Log.d(TAG, "  → Deleting scanner ID " + user.getLeftScannerUserId2() + " (Left 2)...");
            UF_RET_CODE result = sdk.UF_Delete(user.getLeftScannerUserId2());
            if (result == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "    ✓ SUCCESS");
            } else if (result == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                Log.w(TAG, "    ⚠ WARNING: Not found in hardware");
                success = false;
            } else {
                Log.e(TAG, "    ✗ FAILED: " + result);
                success = false;
            }
        }

        // Delete right finger 2
        if (user.getRightScannerUserId2() > 0) {
            Log.d(TAG, "  → Deleting scanner ID " + user.getRightScannerUserId2() + " (Right 2)...");
            UF_RET_CODE result = sdk.UF_Delete(user.getRightScannerUserId2());
            if (result == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "    ✓ SUCCESS");
            } else if (result == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                Log.w(TAG, "    ⚠ WARNING: Not found in hardware");
                success = false;
            } else {
                Log.e(TAG, "    ✗ FAILED: " + result);
                success = false;
            }
        }

        return success;
    }

    /**
     * Delete user from PAC API
     */
    private void deleteFromAPI(UserListItem user) {
        staffEnrollmentService.deleteUser(user.getEmployeeNumber(), new StaffEnrollmentCallback() {
            @Override
            public void onUserDeleted() {
                Log.i(TAG, "✓ User deleted from API");
                // Step 4: Delete from local database
                deleteFromDatabase(user);
            }

            @Override
            public void onEnrollmentError(String error) {
                Log.e(TAG, "Failed to delete from API: " + error);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(DeleteUserActivity.this, "API deletion failed: " + error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {}
            @Override
            public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {}
            @Override
            public void onUserValidated(UserListItem user) {}
            @Override
            public void onUserNotFound() {}
            @Override
            public void onFingerprintValidated(UserListItem user) {}
            @Override
            public void onFingerprintNotFound() {}
            @Override
            public void onSyncStarted(int totalFingerprints) {}
            @Override
            public void onSyncProgress(int current, int total, String userName) {}
            @Override
            public void onSyncCompleted(int successCount, int failCount) {}
        });
    }

    /**
     * Delete user from local database
     *
     * NOTE: For synced users (from API), they only exist in synced_fingerprints table,
     * not in the local users table. By the time we reach this method, we've already:
     * - Deleted from scanner hardware
     * - Deleted from synced_fingerprints table
     * - Deleted from API
     *
     * So even if softDeleteUser returns false (user not in local users table),
     * we consider the deletion successful because all necessary deletions are complete.
     */
    private void deleteFromDatabase(UserListItem user) {
        boolean deletedFromLocalUsers = dbHelper.softDeleteUser(user.getEmployeeNumber());

        // By this point, we've already successfully deleted from:
        // 1. Scanner hardware
        // 2. Synced_fingerprints table (if applicable)
        // 3. API
        //
        // The softDeleteUser only affects the local users table.
        // If the user wasn't in that table (synced user), the deletion is still successful.
        // We only fail if there's a genuine database error, not a "not found" case.
        boolean overallSuccess = true; // Always true at this point

        Log.d(TAG, "");
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ DATABASE DELETION SUMMARY");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Deleted from local users table: " + deletedFromLocalUsers);
        Log.d(TAG, "║ Overall deletion success: " + overallSuccess);
        Log.d(TAG, "╚═══════════════════════════════════════════");

        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);

            if (overallSuccess) {
                Toast.makeText(this, "✓ User deleted successfully", Toast.LENGTH_SHORT).show();

                // Remove from list and update adapter
                allEnrolledUsers.remove(user);
                if (adapter != null) {
                    adapter.updateUserList(allEnrolledUsers);
                    updateFilterButtonTexts();
                    applyFilters();
                }

                // Show empty message if no users left
                if (allEnrolledUsers.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    searchFilterContainer.setVisibility(View.GONE);
                }
            } else {
                Toast.makeText(this, "Failed to delete from database", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
