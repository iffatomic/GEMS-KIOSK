/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.RegisterUserResponse;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.network.callbacks.UserRegistrationCallback;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;
import com.supremainc.sfm_sdk_android.service.UserRegistrationService;

import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.UsbService;
import com.supremainc.sfm_sdk.callback_interface.SFM_SDK_ANDROID_CALLBACK_INTERFACE;
import com.supremainc.sfm_sdk.enumeration.UF_ENROLL_MODE;
import com.supremainc.sfm_sdk.enumeration.UF_ENROLL_OPTION;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk.message_handler.MessageHandler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This activity handles fingerprint enrollment for new users
 *
 * IMPORTANT CONCEPT:
 * - We scan the fingerprint to get a template (digital representation)
 * - We enroll that template into the scanner's memory with a unique ID
 * - We store both the template and the scanner ID in our database
 * - Later, when someone scans their finger, the scanner will return the ID we assigned
 */
public class FingerprintEnrollmentActivity extends AppCompatActivity {

    private static final String TAG = "FingerprintEnrollment";

    // UI Components - all the buttons, text fields, etc.
    private ImageButton backButton;
    private EditText userIdEditText, nameEditText, roleEditText;
    private Button scanLeftButton, scanRightButton, scanLeftButton2, scanRightButton2, confirmButton, cancelButton;
    private ImageView leftFingerprintImage, leftFingerprintImage2, rightFingerprintImage,  rightFingerprintImage2;
    private TextView statusText, leftStatusText, rightStatusText,leftStatusText2, rightStatusText2;
    private ProgressBar progressBar;

    // SDK and Database
    private SFM_SDK_ANDROID sdk;  // This communicates with the fingerprint scanner
    private DatabaseHelper dbHelper;  // This manages our SQLite database
    private StaffEnrollmentService staffEnrollmentService;  // API service for staff enrollment

    // Fingerprint data storage
    private byte[] leftFingerprintTemplate;   // Stores the left fingerprint template data
    private byte[] rightFingerprintTemplate;  // Stores the right fingerprint template data
    private int leftScannerUserId = -1;       // The ID assigned by scanner for left finger
    private int rightScannerUserId = -1;      // The ID assigned by scanner for right finger
    private boolean isLeftScanned = false;    // Track if left finger is scanned
    private boolean isRightScanned = false;   // Track if right finger is scanned

    private byte[] leftFingerprintTemplate2;
    private byte[] rightFingerprintTemplate2;
    private int leftScannerUserId2 = -1;
    private int rightScannerUserId2 = -1;
    private boolean isLeftScanned2 = false;
    private boolean isRightScanned2 = false;
    private boolean isScanning = false;       // Prevent multiple scans at once

    // LOCAL USER SCANNER ID RANGE: 1-9999
    // SYNCED USER SCANNER ID RANGE: 10000-99999
    // This separation allows verification to distinguish between local and synced users
    private static final int ID_RANGE_START = 1;
    private static final int ID_RANGE_END = 9999;
    private int nextEnrollID = ID_RANGE_START;

    // Background task management
    private ExecutorService executor;  // For running scanner operations in background
    private Handler mainHandler;       // For updating UI from background thread

    // Role-based enrollment variables
    private String selectedRole = null;  // "ADMIN" or "CUSTODIAN"
    private DatabaseHelper.RegisteredPersonnel selectedPersonnel = null;
    private UserListItem selectedUserFromApi = null;  // User selected from API
    private AlertDialog roleSelectionDialog;
    private AlertDialog personSelectionDialog;

    // UI containers for hiding/showing form fields
    private LinearLayout userInfoContainer;
    private LinearLayout fingerprintSection;

    // First-time setup flag - when true, skip role selection and force admin enrollment
    private boolean isFirstTimeSetup = false;

    // =================== SDK CALLBACK DEFINITIONS ===================
    // These are called by the SDK when certain events happen

    /**
     * Called when scanner receives data packets
     * We don't need to do anything special here for basic enrollment
     */
    private final SFM_SDK_ANDROID.ReceiveDataPacketCallback receiveDataPacketCallback =
            new SFM_SDK_ANDROID.ReceiveDataPacketCallback() {
                @Override
                public void callback(int index, int numOfPacket) {
                    // This tracks data transfer progress - not needed for basic enrollment
                }
            };

    /**
     * Called when scanner sends raw data
     * We don't need to do anything special here for basic enrollment
     */
    private final SFM_SDK_ANDROID.SendRawDataCallback sendRawDataCallback =
            new SFM_SDK_ANDROID.SendRawDataCallback() {
                @Override
                public void callback(int writtenLen, int totalSize) {
                    // This tracks data sending progress - not needed for basic enrollment
                }
            };

    /**
     * Called when scanner receives raw data
     * We don't need to do anything special here for basic enrollment
     */
    private final SFM_SDK_ANDROID.ReceiveRawDataCallback receiveRawDataCallback =
            new SFM_SDK_ANDROID.ReceiveRawDataCallback() {
                @Override
                public void callback(int readLen, int totalSize) {
                    // This tracks data receiving progress - not needed for basic enrollment
                }
            };

    /**
     * Called when a fingerprint scan is completed
     * This tells us if the scan was successful or failed
     */
    private final SFM_SDK_ANDROID.ScanCallback scanCallback =
            new SFM_SDK_ANDROID.ScanCallback() {
                @Override
                public void callback(byte errCode) {
                    // This runs on the main UI thread
                    runOnUiThread(() -> {
                        if (errCode == 0) {
                            // errCode 0 means success
                            updateStatusText("Fingerprint scan successful!");
                            progressBar.setVisibility(View.GONE);
                        } else {
                            // Any other error code means failure
                            updateStatusText("Scan failed. Please try again.");
                            progressBar.setVisibility(View.GONE);
                            isScanning = false;
                        }
                    });
                }
            };

    /**
     * DATABASE DEBUG HELPER
     *
     * Add this method to your MainActivity.java or FingerprintEnrollmentActivity.java
     * Call it in onCreate() to see exact database location and contents
     */

    private void printDatabaseDebugInfo() {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ DATABASE DEBUG INFO");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");

        // 1. Package name
        String packageName = getPackageName();
        Log.d(TAG, "║ Package Name: " + packageName);

        // 2. Database file path
        File dbFile = getDatabasePath("FingerprintDB");
        Log.d(TAG, "║ Database Path: " + dbFile.getAbsolutePath());
        Log.d(TAG, "║ Database Exists: " + dbFile.exists());
        Log.d(TAG, "║ Database Size: " + dbFile.length() + " bytes");

        // 3. Database version
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Log.d(TAG, "║ Database Version: " + db.getVersion());
        Log.d(TAG, "║ Database Path (from SQLite): " + db.getPath());
        Log.d(TAG, "║ Database Read-Only: " + db.isReadOnly());

        // 4. Table info
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
                null
        );
        Log.d(TAG, "║ Tables:");
        while (cursor.moveToNext()) {
            String tableName = cursor.getString(0);
            Log.d(TAG, "║   - " + tableName);
        }
        cursor.close();

        // 5. User count
        Cursor userCursor = db.rawQuery("SELECT COUNT(*) FROM users", null);
        userCursor.moveToFirst();
        int userCount = userCursor.getInt(0);
        userCursor.close();
        Log.d(TAG, "║ Total Users: " + userCount);

        // 6. List all users
        List<DatabaseHelper.User> users = dbHelper.getAllActiveUsers();
        Log.d(TAG, "║ ");
        Log.d(TAG, "║ USER LIST:");
        Log.d(TAG, "║ " + String.format("%-5s %-15s %-10s %-25s %-5s %-5s %-5s %-5s %s",
                "ID", "IC Number", "Staff ID", "Name", "F1", "F2", "F3", "F4", "Admin"));
        Log.d(TAG, "║ " + "─".repeat(90));

        for (DatabaseHelper.User user : users) {
            Log.d(TAG, "║ " + String.format("%-5d %-15s %-10s %-25s %-5s %-5s %-5s %-5s %s",
                    user.getId(),
                    user.getIcNumber(),
                    user.getStaffId(),
                    user.getName(),
                    user.getLeftFingerprint() != null ? "✓" : "✗",
                    user.getRightFingerprint() != null ? "✓" : "✗",
                    user.getLeftFingerprint2() != null ? "✓" : "✗",
                    user.getRightFingerprint2() != null ? "✓" : "✗",
                    user.isAdmin() ? "YES" : "NO"
            ));
        }

        db.close();
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // 7. Copy command to logcat for easy access
        Log.d(TAG, "");
        Log.d(TAG, "🔧 TO ACCESS DATABASE FROM COMMAND LINE:");
        Log.d(TAG, "adb shell run-as " + packageName + " sqlite3 databases/FingerprintDB");
        Log.d(TAG, "");
        Log.d(TAG, "🔍 TO QUERY ALL USERS:");
        Log.d(TAG, "adb shell run-as " + packageName + " sqlite3 databases/FingerprintDB \"SELECT * FROM users;\"");
        Log.d(TAG, "");
    }

    /**
     * Called when fingerprint enrollment is completed
     * This tells us if the enrollment into scanner memory was successful
     */
    private final SFM_SDK_ANDROID.EnrollCallback enrollCallback =
            new SFM_SDK_ANDROID_CALLBACK_INTERFACE.EnrollCallback() {
                @Override
                public void callback(byte errCode, UF_ENROLL_MODE enrollMode, int numOfSuccess) {
                    // This runs on the main UI thread
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;

                        if (errCode == 0) {
                            // Success! The fingerprint is now enrolled in scanner memory
                            updateStatusText("Enrollment successful! Templates stored in scanner.");
                            updateFingerprintStatus();
                        } else {
                            // Failed to enroll
                            updateStatusText("Enrollment failed. Error code: " + String.format("0x%02X", errCode));
                            Log.e(TAG, "Enrollment failed with error: " + errCode);
                        }
                    });
                }
            };

    /**
     * Handles messages from the USB service (if using USB connection)
     * For UART connection, this isn't used much
     */
    private final MessageHandler mHandler = new MessageHandler(this) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = "[RECV] " + msg.obj + "\n";
                    Log.d(TAG, data);
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(FingerprintEnrollmentActivity.this, "Scanner connection changed", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(FingerprintEnrollmentActivity.this, "Scanner status changed", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.SYNC_READ:
                    String buffer = (String) msg.obj;
                    Log.d(TAG, buffer);
                    break;
            }
        }
    };

    // =================== ACTIVITY LIFECYCLE METHODS ===================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_enrollment);

        // Initialize everything step by step
        printDatabaseDebugInfo();//show database pathing
        initializeComponents();
        initializeSDK();
        setupClickListeners();

        // Initialize next ID from database - only check LOCAL range (1-9999)
        // Synced users use range 10000-99999, so we exclude them
        int maxId = dbHelper.getMaxLocalScannerIdInUse();
        nextEnrollID = maxId + 1;

        Log.d(TAG, "╔════════════════════════════════════════");
        Log.d(TAG, "║ SCANNER ID INITIALIZATION (LOCAL)");
        Log.d(TAG, "╠════════════════════════════════════════");
        Log.d(TAG, "║ Max LOCAL ID in database (1-9999): " + maxId);
        Log.d(TAG, "║ Next enrollment ID: " + nextEnrollID);
        Log.d(TAG, "║ Synced users use range: 10000-99999");
        Log.d(TAG, "╚════════════════════════════════════════");

        // Check if this is first-time setup (no admin fingerprints exist)
        isFirstTimeSetup = getIntent().getBooleanExtra("isFirstTimeSetup", false);
        Log.d(TAG, "isFirstTimeSetup: " + isFirstTimeSetup);

        if (isFirstTimeSetup) {
            // First-time setup: Skip role selection, force admin enrollment
            selectedRole = "ADMIN";
            showPersonSelectionDialog();
        } else {
            // Normal flow: Show role selection dialog
            showRoleSelectionDialog();
        }
    }

    // =================== ROLE-BASED ENROLLMENT METHODS ===================

    /**
     * Show dialog to select role (Admin or Custodian)
     */
    private void showRoleSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_role_selection, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        roleSelectionDialog = builder.create();

        MaterialButton btnAdmin = dialogView.findViewById(R.id.btnAdmin);
        MaterialButton btnCustodian = dialogView.findViewById(R.id.btnCustodian);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);

        btnAdmin.setOnClickListener(v -> {
            selectedRole = "ADMIN";
            roleSelectionDialog.dismiss();
            showPersonSelectionDialog();
        });

        btnCustodian.setOnClickListener(v -> {
            selectedRole = "CUSTODIAN";
            roleSelectionDialog.dismiss();
            showPersonSelectionDialog();
        });

        btnCancel.setOnClickListener(v -> {
            roleSelectionDialog.dismiss();
            finish();  // Go back if user cancels
        });

        roleSelectionDialog.show();
    }

    /**
     * Show dialog to select a person from the list based on selected role
     */
    private void showPersonSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_person_selection, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        personSelectionDialog = builder.create();

        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvSubtitle);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewPersonnel);
        TextView tvEmptyMessage = dialogView.findViewById(R.id.tvEmptyMessage);
        ProgressBar loadingProgress = dialogView.findViewById(R.id.loadingProgress);
        MaterialButton btnBack = dialogView.findViewById(R.id.btnBack);
        EditText etSearchPersonnel = dialogView.findViewById(R.id.etSearchPersonnel);

        // Set title based on role and first-time setup status
        if (isFirstTimeSetup) {
            tvTitle.setText("First-Time Setup");
            tvSubtitle.setText("Select an admin to enroll the first fingerprint");
        } else if ("ADMIN".equals(selectedRole)) {
            tvTitle.setText("Select Admin");
            tvSubtitle.setText("Choose an admin to enroll fingerprint");
        } else {
            tvTitle.setText("Select Custodian");
            tvSubtitle.setText("Choose a custodian to enroll fingerprint");
        }

        // Show loading while fetching from API
        recyclerView.setVisibility(View.GONE);
        tvEmptyMessage.setVisibility(View.GONE);
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }

        // Fetch pending users from API
        StaffEnrollmentCallback callback = new StaffEnrollmentCallback() {
            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {
                runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    if (users.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmptyMessage.setVisibility(View.VISIBLE);
                        if (isFirstTimeSetup) {
                            tvEmptyMessage.setText("No admins available for enrollment.\nPlease contact system administrator.");
                        } else {
                            tvEmptyMessage.setText("No " + selectedRole.toLowerCase() + "s pending enrollment.\nAll have already been enrolled.");
                        }
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setVisibility(View.GONE);

                        // Convert UserListItem to RegisteredPersonnel for adapter compatibility
                        List<DatabaseHelper.RegisteredPersonnel> personnelList = convertApiUsersToPersonnel(users);

                        recyclerView.setLayoutManager(new LinearLayoutManager(FingerprintEnrollmentActivity.this));
                        PersonnelAdapter adapter = new PersonnelAdapter(personnelList, selectedRole, personnel -> {
                            selectedPersonnel = personnel;
                            // Also find and store the corresponding API user
                            for (UserListItem apiUser : users) {
                                if (apiUser.getEmployeeNumber().equals(personnel.getEmployeeId())) {
                                    selectedUserFromApi = apiUser;
                                    break;
                                }
                            }
                            // Hide keyboard when user is selected
                            if (etSearchPersonnel != null) {
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(etSearchPersonnel.getWindowToken(), 0);
                            }
                            personSelectionDialog.dismiss();
                            setupEnrollmentForSelectedPerson();
                        });
                        recyclerView.setAdapter(adapter);

                        // Set up search functionality
                        if (etSearchPersonnel != null) {
                            etSearchPersonnel.addTextChangedListener(new TextWatcher() {
                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                    // Not needed
                                }

                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {
                                    // Filter the list as user types
                                    adapter.filter(s.toString());
                                }

                                @Override
                                public void afterTextChanged(Editable s) {
                                    // Not needed
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void onEnrollmentError(String error) {
                runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                    recyclerView.setVisibility(View.GONE);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    tvEmptyMessage.setText("Error loading pending users:\n" + error + "\n\nPlease check your internet connection and try again.");
                    Toast.makeText(FingerprintEnrollmentActivity.this, "API Error: " + error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onEnrollmentSuccess(EnrollUserResponse response) {
                // Not used in this context
            }

            @Override
            public void onUserValidated(UserListItem user) {
                // Not used in this context
            }

            @Override
            public void onUserNotFound() {
                // Not used in this context
            }

            @Override
            public void onUserDeleted() {
                // Not used in this context
            }

            @Override
            public void onFingerprintValidated(UserListItem user) {
                // Not used in this context
            }

            @Override
            public void onFingerprintNotFound() {
                // Not used in this context
            }

            @Override
            public void onSyncStarted(int totalFingerprints) {
                // Not used in this context
            }

            @Override
            public void onSyncProgress(int current, int total, String userName) {
                // Not used in this context
            }

            @Override
            public void onSyncCompleted(int successCount, int failCount) {
                // Not used in this context
            }
        };

        // Call appropriate API based on role
        if ("ADMIN".equals(selectedRole)) {
            staffEnrollmentService.getPendingAdminUsers(callback);
        } else {
            staffEnrollmentService.getPendingCustodianUsers(callback);
        }

        // Handle back button differently for first-time setup
        if (isFirstTimeSetup) {
            btnBack.setText("Exit");
            btnBack.setOnClickListener(v -> {
                personSelectionDialog.dismiss();
                // Show warning that admin must be enrolled
                new AlertDialog.Builder(this)
                        .setTitle("Admin Required")
                        .setMessage("At least one admin must enroll their fingerprint to use this app. Are you sure you want to exit?")
                        .setPositiveButton("Exit", (dialog, which) -> {
                            finishAffinity(); // Close the app completely
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            showPersonSelectionDialog(); // Show selection again
                        })
                        .setCancelable(false)
                        .show();
            });
        } else {
            btnBack.setOnClickListener(v -> {
                personSelectionDialog.dismiss();
                showRoleSelectionDialog();  // Go back to role selection
            });
        }

        personSelectionDialog.show();
    }

    /**
     * Convert API UserListItem to local RegisteredPersonnel for adapter compatibility
     */
    private List<DatabaseHelper.RegisteredPersonnel> convertApiUsersToPersonnel(List<UserListItem> apiUsers) {
        List<DatabaseHelper.RegisteredPersonnel> personnelList = new ArrayList<>();

        for (UserListItem apiUser : apiUsers) {
            DatabaseHelper.RegisteredPersonnel personnel = new DatabaseHelper.RegisteredPersonnel();
            personnel.setEmployeeId(apiUser.getEmployeeNumber());
            personnel.setName(apiUser.getName());
            personnel.setRole(apiUser.getRole());
            // Note: RegisteredPersonnel doesn't have department/branch fields
            // Those are only used in the API UserListItem

            personnelList.add(personnel);
        }

        Log.d(TAG, "Converted " + apiUsers.size() + " API users to personnel records");
        return personnelList;
    }

    /**
     * Set up the enrollment screen with the selected person's information
     */
    private void setupEnrollmentForSelectedPerson() {
        if (selectedPersonnel == null) {
            Log.e(TAG, "No personnel selected!");
            finish();
            return;
        }

        // Pre-fill the form fields with selected person's data
        userIdEditText.setText(selectedPersonnel.getEmployeeId());
        nameEditText.setText(selectedPersonnel.getName());
        roleEditText.setText(selectedRole);  // Set role (ADMIN or CUSTODIAN)

        // Disable editing of the pre-filled fields
        userIdEditText.setEnabled(false);
        nameEditText.setEnabled(false);
        roleEditText.setEnabled(false);

        // Update status text
        if (isFirstTimeSetup) {
            updateStatusText("First-time setup: Ready to enroll ADMIN: " + selectedPersonnel.getName() + " (No verification required)");
        } else {
            updateStatusText("Ready to enroll " + selectedRole + ": " + selectedPersonnel.getName());
        }

        // Hide fingerprint 3 and 4 (only need 2 fingerprints)
        hideExtraFingerprints();
    }

    /**
     * Hide fingerprint 3 and 4 (only 2 fingerprints required for admin/custodian enrollment)
     * We keep scanLeftButton (index 0) and scanRightButton (index 1) which save to isLeftScanned/isRightScanned
     * We hide scanLeftButton2 (index 2) and scanRightButton2 (index 3)
     */
    private void hideExtraFingerprints() {
        // Hide the fingerprint sections for indices 2 and 3
        if (scanLeftButton2 != null) {
            ((View) scanLeftButton2.getParent()).setVisibility(View.GONE);
        }
        if (scanRightButton2 != null) {
            ((View) scanRightButton2.getParent()).setVisibility(View.GONE);
        }
    }



    /**
     * Initialize all UI components and connect them to variables
     */
    private void initializeComponents() {
        // Connect UI elements to variables
        backButton = findViewById(R.id.back_button);
        userIdEditText = findViewById(R.id.userIdEditText);
        nameEditText = findViewById(R.id.nameEditText);
        roleEditText = findViewById(R.id.roleEditText);

        scanLeftButton = findViewById(R.id.scanLeftButton);
        scanRightButton = findViewById(R.id.scanRightButton);
        scanLeftButton2 = findViewById(R.id.scanLeftButton2);
        scanRightButton2 = findViewById(R.id.scanRightButton2);
        confirmButton = findViewById(R.id.confirmButton);
        cancelButton = findViewById(R.id.cancelButton);

        leftFingerprintImage = findViewById(R.id.leftFingerprintImage);
        rightFingerprintImage = findViewById(R.id.rightFingerprintImage);
        leftFingerprintImage2 = findViewById(R.id.leftFingerprintImage2);
        rightFingerprintImage2 = findViewById(R.id.rightFingerprintImage2);

        statusText = findViewById(R.id.statusText);
        leftStatusText = findViewById(R.id.leftStatusText);
        rightStatusText = findViewById(R.id.rightStatusText);
        leftStatusText2 = findViewById(R.id.leftStatusText2);
        rightStatusText2 = findViewById(R.id.rightStatusText2);
        progressBar = findViewById(R.id.progressBar);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // NOTE: staffEnrollmentService initialization moved to initializeSDK()
        // because it needs the SDK instance which isn't created yet at this point

        // Initialize background task handler
        executor = Executors.newSingleThreadExecutor();  // Single thread for scanner operations
        mainHandler = new Handler(Looper.getMainLooper());  // Handler for UI updates

        // Initially disable confirm button until we have at least one fingerprint
        confirmButton.setEnabled(false);
    }

    /**
     * Initialize the fingerprint scanner SDK
     * Close any existing connections and open fresh
     */
    private void initializeSDK() {
        try {
            // Create new SDK instance
            sdk = new SFM_SDK_ANDROID();

            // Get and log SDK version
            String version = "SDK Version: " + sdk.UF_GetSDKVersion();
            Log.d(TAG, version);

            // Initialize system parameters (must be called before using SDK)
            sdk.UF_InitSysParameter();

            // NOW initialize staff enrollment service with the created SDK
            Log.d(TAG, "Initializing StaffEnrollmentService with SDK...");
            staffEnrollmentService = new StaffEnrollmentService(this, sdk);
            Log.d(TAG, "StaffEnrollmentService initialized successfully");

            // IMPORTANT: Don't close the port - MainMenuActivity manages the port lifecycle
            // Just reconnect to use the existing connection
            Log.d(TAG, "Reconnecting to existing scanner connection...");
            try {
                sdk.UF_Reconnect();
                Thread.sleep(300);
                Log.d(TAG, "Reconnected successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error reconnecting: " + e.getMessage());
            }

            // Try opening fresh connection only if reconnect failed
            Log.d(TAG, "Opening connection to /dev/ttyACM0...");
            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);

            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                // If first port fails, try second common port
                Log.d(TAG, "Trying /dev/ttyACM1...");
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                // Connection successful
                updateStatusText("Fingerprint scanner connected successfully");
                Log.d(TAG, "Scanner connected successfully!");
                setupSDKCallbacks();

                // Give scanner time to initialize
                Thread.sleep(300);
                sdk.UF_Reconnect();

                Log.d(TAG, "Scanner ready for enrollment");
            } else {
                // Connection failed
                updateStatusText("Failed to connect to fingerprint scanner. Error: " + ret);
                Log.e(TAG, "Failed to initialize comm port: " + ret);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
            updateStatusText("Error initializing fingerprint scanner: " + e.getMessage());
        }
    }

    /**
     * Set up callback functions that the SDK will call when events happen
     */
    private void setupSDKCallbacks() {
        sdk.UF_SetSendRawDataCallback(sendRawDataCallback);
        sdk.UF_SetReceiveRawDataCallback(receiveRawDataCallback);
        sdk.UF_SetReceiveDataPacketCallback(receiveDataPacketCallback);
        sdk.UF_SetScanCallback(scanCallback);
        sdk.UF_SetEnrollCallback(enrollCallback);
    }

    //TODO

    /**
     * Set up click listeners for all buttons
     */
    private void setupClickListeners() {
        // Back button - behavior depends on first-time setup mode
        backButton.setOnClickListener(v -> handleBackOrCancel());

        // Scan buttons - 0=finger1, 1=finger2, 2=finger3, 3=finger4
        scanLeftButton.setOnClickListener(v -> scanFingerprint(0));   // CHANGE: true → 0
        scanRightButton.setOnClickListener(v -> scanFingerprint(1));  // CHANGE: false → 1
        scanLeftButton2.setOnClickListener(v -> scanFingerprint(2));  // ADD: missing closing )
        scanRightButton2.setOnClickListener(v -> scanFingerprint(3)); // REMOVE: extra ;

        // Confirm button - save all data to database
        confirmButton.setOnClickListener(v -> saveUserData());

        // Cancel button - behavior depends on first-time setup mode
        cancelButton.setOnClickListener(v -> handleBackOrCancel());
    }

    /**
     * Handle back or cancel button press
     * For first-time setup, show warning. For normal flow, just finish.
     */
    private void handleBackOrCancel() {
        // Clean up any scanned fingerprints from scanner before exiting
        cleanupScannedFingerprints();

        if (isFirstTimeSetup) {
            new AlertDialog.Builder(this)
                    .setTitle("Admin Required")
                    .setMessage("At least one admin must enroll their fingerprint to use this app. Do you want to select a different admin or exit?")
                    .setPositiveButton("Select Different Admin", (dialog, which) -> {
                        showPersonSelectionDialog();
                    })
                    .setNegativeButton("Exit App", (dialog, which) -> {
                        finishAffinity();
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            finish();
        }
    }

    /**
     * Clean up scanned fingerprints from scanner memory
     * Called when user cancels enrollment to prevent orphaned scanner IDs
     */
    private void cleanupScannedFingerprints() {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ CLEANUP: Removing scanned fingerprints");
        Log.d(TAG, "╠═══════════════════════════════════════════");

        int deletedCount = 0;

        // Delete finger 1 (left)
        if (leftScannerUserId > 0) {
            Log.d(TAG, "║ Deleting Finger 1 (Left) - Scanner ID: " + leftScannerUserId);
            UF_RET_CODE ret = sdk.UF_Delete(leftScannerUserId);
            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                deletedCount++;
                leftScannerUserId = -1;
            }
        }

        // Delete finger 2 (right)
        if (rightScannerUserId > 0) {
            Log.d(TAG, "║ Deleting Finger 2 (Right) - Scanner ID: " + rightScannerUserId);
            UF_RET_CODE ret = sdk.UF_Delete(rightScannerUserId);
            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                deletedCount++;
                rightScannerUserId = -1;
            }
        }

        // Delete finger 3 (left2)
        if (leftScannerUserId2 > 0) {
            Log.d(TAG, "║ Deleting Finger 3 (Left2) - Scanner ID: " + leftScannerUserId2);
            UF_RET_CODE ret = sdk.UF_Delete(leftScannerUserId2);
            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                deletedCount++;
                leftScannerUserId2 = -1;
            }
        }

        // Delete finger 4 (right2)
        if (rightScannerUserId2 > 0) {
            Log.d(TAG, "║ Deleting Finger 4 (Right2) - Scanner ID: " + rightScannerUserId2);
            UF_RET_CODE ret = sdk.UF_Delete(rightScannerUserId2);
            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                deletedCount++;
                rightScannerUserId2 = -1;
            }
        }

        Log.d(TAG, "║ Cleanup complete: " + deletedCount + " fingerprint(s) deleted");
        Log.d(TAG, "╚═══════════════════════════════════════════");
    }


    /**
     * Get next available scanner ID in Interim's range
     * Uses cached nextEnrollID to prevent duplicate IDs during enrollment
     * (Database is only queried once during onCreate for initialization)
     */
    private int getNextAvailableID() {
        // BUG FIX: Use cached nextEnrollID instead of querying database every time
        // This prevents duplicate IDs when scanning multiple fingers before saving to DB
        //
        // Previous bug:
        // 1. Scan left finger → query DB, get ID 10001
        // 2. Scan right finger → query DB again, get SAME ID 10001 (left not in DB yet!)
        //
        // New approach:
        // 1. Scan left finger → use nextEnrollID (10001), increment to 10002
        // 2. Scan right finger → use nextEnrollID (10002), increment to 10003
        // Each finger gets unique ID even before DB save!

        int id = nextEnrollID;

        // Sanity check: ensure we're in valid range
        if (id < ID_RANGE_START) {
            id = ID_RANGE_START;
        } else if (id > ID_RANGE_END) {
            Log.e(TAG, "╔════════════════════════════════════════");
            Log.e(TAG, "║ ✗ CRITICAL ERROR: ID RANGE EXHAUSTED");
            Log.e(TAG, "╠════════════════════════════════════════");
            Log.e(TAG, "║ Next ID would be: " + id);
            Log.e(TAG, "║ Range End: " + ID_RANGE_END);
            Log.e(TAG, "╚════════════════════════════════════════");
            return -1;
        }

        Log.d(TAG, "╔════════════════════════════════════════");
        Log.d(TAG, "║ ASSIGNING NEW SCANNER ID");
        Log.d(TAG, "╠════════════════════════════════════════");
        Log.d(TAG, "║ Assigning ID: " + id);
        Log.d(TAG, "║ Next available: " + (id + 1));
        Log.d(TAG, "╚════════════════════════════════════════");

        // Increment for next finger
        nextEnrollID = id + 1;

        return id;
    }

    // =================== FINGERPRINT SCANNING METHODS ===================

    /**
     * Main method to scan a fingerprint
     * @param fingerIndex - 0=left1, 1=right1, 2=left2, 3=right2
     */
    private void scanFingerprint(int fingerIndex) {
        // Determine finger name for logging and UI
        String fingerName = "";
        switch (fingerIndex) {
            case 0: fingerName = "Finger 1 (Left Hand)"; break;
            case 1: fingerName = "Finger 2 (Right Hand)"; break;
            case 2: fingerName = "Finger 3 (Left Hand)"; break;
            case 3: fingerName = "Finger 4 (Right Hand)"; break;
        }

        Log.d(TAG, "=== scanFingerprint called for " + fingerName + " (index: " + fingerIndex + ") ===");

        // Check if we're already scanning
        if (isScanning) {
            Toast.makeText(this, "Please wait for current scan to complete", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Already scanning, ignoring request");
            return;
        }

        // Check if basic user info is filled
        if (!validateBasicInfo()) {
            Toast.makeText(this, "Please fill in all required fields first", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Basic info validation failed");
            return;
        }

        // Check if SDK is initialized
        if (sdk == null) {
            Toast.makeText(this, "Scanner not initialized", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "SDK is null!");
            return;
        }

        // Set scanning state
        isScanning = true;
        progressBar.setVisibility(View.VISIBLE);
        updateStatusText("Scanning " + fingerName + "...");

        Log.d(TAG, "Starting background scan task...");

        // Run scanner operations in background thread to avoid blocking UI
        executor.execute(() -> {
            Log.d(TAG, "Background task started");
            try {
                // Make sure scanner connection is stable
                Log.d(TAG, "Calling UF_Reconnect...");
                sdk.UF_Reconnect();
                Log.d(TAG, "UF_Reconnect completed");

                // Update UI from main thread
                mainHandler.post(() -> {
                    updateStatusText("Please place finger on scanner...");
                });

                // Step 1: Scan the fingerprint to get template data
                byte[] templateData = new byte[384];  // Standard template size
                int[] templateSize = new int[1];      // Will contain actual template size
                int[] imageQuality = new int[1];      // Will contain image quality score

                // Cancel any previous operations to ensure scanner is ready
                sdk.UF_Cancel(false);
                Log.d(TAG, "UF_Cancel called to clear previous operations");

                Log.d(TAG, "Calling UF_ScanTemplate...");

                // Scan template from fingerprint
                UF_RET_CODE ret = sdk.UF_ScanTemplate(templateData, templateSize, imageQuality);

                Log.d(TAG, "UF_ScanTemplate returned: " + ret);
                Log.d(TAG, "Template size: " + templateSize[0] + ", Quality: " + imageQuality[0]);

                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    Log.d(TAG, "Scan successful, now enrolling...");

                    // Update UI from main thread
                    mainHandler.post(() -> {
                        updateStatusText("Scan successful! Enrolling template...");
                    });

                    // RESCAN FIX: Delete old scanner ID if this finger was previously scanned
                    int oldScannerId = -1;
                    switch (fingerIndex) {
                        case 0: oldScannerId = leftScannerUserId; break;
                        case 1: oldScannerId = rightScannerUserId; break;
                        case 2: oldScannerId = leftScannerUserId2; break;
                        case 3: oldScannerId = rightScannerUserId2; break;
                    }

                    if (oldScannerId > 0) {
                        Log.d(TAG, "╔═══════════════════════════════════════════");
                        Log.d(TAG, "║ RESCAN DETECTED: Finger " + (fingerIndex + 1));
                        Log.d(TAG, "║ Deleting old scanner ID: " + oldScannerId);
                        UF_RET_CODE deleteRet = sdk.UF_Delete(oldScannerId);
                        if (deleteRet == UF_RET_CODE.UF_RET_SUCCESS) {
                            Log.d(TAG, "║ ✓ Old fingerprint deleted successfully");
                        } else {
                            Log.w(TAG, "║ ⚠ Failed to delete old fingerprint: " + deleteRet);
                        }
                        Log.d(TAG, "╚═══════════════════════════════════════════");
                    }

                    // Step 2: Enroll the template into scanner memory
                    int[] enrollID = new int[1];
                    int specificID = getNextAvailableID();

                    if (specificID == -1) {
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Cannot enroll: ID range exhausted!", Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                            isScanning = false;
                        });
                        return;
                    }

                    Log.d(TAG, "Enrolling with specific ID: " + specificID);
                    ret = sdk.UF_EnrollTemplate(
                            specificID,                         // Use specific ID in Interim range
                            UF_ENROLL_OPTION.UF_ENROLL_NONE,   // NOT AUTO_ID - we specify the ID
                            templateSize[0],
                            templateData,
                            enrollID
                    );

                    Log.d(TAG, "UF_EnrollTemplate returned: " + ret);
                    Log.d(TAG, "Enrolled ID: " + enrollID[0]);

                    // Create final variables for use in lambda expression
                    final byte[] finalTemplateData = Arrays.copyOf(templateData, templateSize[0]);
                    final int finalTemplateSize = templateSize[0];
                    final int finalImageQuality = imageQuality[0];
                    final int finalEnrollID = enrollID[0];
                    final UF_RET_CODE finalRet = ret;
                    final int finalFingerIndex = fingerIndex;

                    // Update UI on main thread
                    mainHandler.post(() -> {
                        if (finalRet == UF_RET_CODE.UF_RET_SUCCESS) {
                            Log.d(TAG, "Enrollment successful for finger index: " + finalFingerIndex);

                            // Store data based on which finger was scanned
                            switch (finalFingerIndex) {
                                case 0: // Left finger 1
                                    leftFingerprintTemplate = finalTemplateData;
                                    leftScannerUserId = finalEnrollID;
                                    isLeftScanned = true;
                                    leftStatusText.setText("✓ Finger 1 captured (ID: " + finalEnrollID + ")");
                                    leftStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    Log.d(TAG, "Stored Finger 1 data");
                                    break;

                                case 1: // Right finger 1
                                    rightFingerprintTemplate = finalTemplateData;
                                    rightScannerUserId = finalEnrollID;
                                    isRightScanned = true;
                                    rightStatusText.setText("✓ Finger 2 captured (ID: " + finalEnrollID + ")");
                                    rightStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    Log.d(TAG, "Stored Finger 2 data");
                                    break;

                                case 2: // Left finger 2
                                    leftFingerprintTemplate2 = finalTemplateData;
                                    leftScannerUserId2 = finalEnrollID;
                                    isLeftScanned2 = true;
                                    leftStatusText2.setText("✓ Finger 3 captured (ID: " + finalEnrollID + ")");
                                    leftStatusText2.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    Log.d(TAG, "Stored Finger 3 data");
                                    break;

                                case 3: // Right finger 2
                                    rightFingerprintTemplate2 = finalTemplateData;
                                    rightScannerUserId2 = finalEnrollID;
                                    isRightScanned2 = true;
                                    rightStatusText2.setText("✓ Finger 4 captured (ID: " + finalEnrollID + ")");
                                    rightStatusText2.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    Log.d(TAG, "Stored Finger 4 data");
                                    break;
                            }

                            updateStatusText("Fingerprint enrolled successfully! Scanner assigned ID: " + finalEnrollID +
                                    ", Quality: " + finalImageQuality);
                            updateConfirmButtonState();
                            updateFingerprintStatus();

                        } else {
                            // Failed to enroll
                            Log.e(TAG, "Enrollment failed: " + finalRet);
                            updateStatusText("Failed to enroll fingerprint. Please try again. Error: " + finalRet);
                        }

                        progressBar.setVisibility(View.GONE);
                        isScanning = false;
                    });

                } else {
                    // Failed to scan
                    final UF_RET_CODE finalScanRet = ret;
                    Log.e(TAG, "Scan failed: " + finalScanRet);

                    mainHandler.post(() -> {
                        String errorMsg = "Failed to capture fingerprint: " + finalScanRet;
                        updateStatusText(errorMsg);
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during fingerprint scan", e);
                e.printStackTrace();

                mainHandler.post(() -> {
                    updateStatusText("Error during scan: " + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                    isScanning = false;
                });
            }
        });

        Log.d(TAG, "=== scanFingerprint method completed (background task queued) ===");
    }

    /**
     * Check if all required user information fields are filled
     */
    private boolean validateBasicInfo() {
        return !TextUtils.isEmpty(userIdEditText.getText()) &&
                !TextUtils.isEmpty(nameEditText.getText()) &&
                !TextUtils.isEmpty(roleEditText.getText());
    }

    /**
     * Enable/disable confirm button based on whether we have required data
     */
    private void updateConfirmButtonState() {
        // Enable confirm button if we have at least one fingerprint and all basic info
        confirmButton.setEnabled((isLeftScanned || isRightScanned) && validateBasicInfo());
    }

    /**
     * Update fingerprint images to show success status
     */
    private void updateFingerprintStatus() {
        if (isLeftScanned) {
            leftFingerprintImage.setImageResource(R.drawable.fingerprint_success);
        }
        if (isRightScanned) {
            rightFingerprintImage.setImageResource(R.drawable.fingerprint_success);
        }
        if (isRightScanned2){
            rightFingerprintImage2.setImageResource(R.drawable.fingerprint_success);
        }
        if (isLeftScanned2){
            leftFingerprintImage2.setImageResource(R.drawable.fingerprint_success);
        }
    }

    // =================== DATABASE METHODS ===================

    /**
     * Save all user data to the database
     * This includes personal info, fingerprint templates, and scanner IDs
     */
    private void saveUserData() {
        // Final validation
        if (!validateBasicInfo()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isLeftScanned && !isRightScanned) {
            Toast.makeText(this, "Please scan at least one fingerprint", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        updateStatusText("Saving user data...");

        // Run database operations in background
        executor.execute(() -> {
            try {
                // Get user input data
                String userId = userIdEditText.getText().toString().trim();
                String name = nameEditText.getText().toString().trim();
                String role = roleEditText.getText().toString().trim();

                        // Check if user already exists
                if (dbHelper.userExists(userId, userId)) {
                    Log.w(TAG, "User with Employee ID " + userId + " already exists in database!");
                    Log.w(TAG, "This enrollment will proceed with API but skip local database save.");
                    // Don't return - let the API enrollment proceed
                    // The local database save will be skipped if user exists
                }

                // Count enrolled fingerprints
                int fingerprintCount = 0;
                if (isLeftScanned) fingerprintCount++;
                if (isRightScanned) fingerprintCount++;

                final int finalFingerprintCount = fingerprintCount;
                final boolean isSupervisor = "ADMIN".equals(selectedRole);

                // DEBUG: Log what we're about to save
                Log.d(TAG, "╔═══════════════════════════════════════════");
                Log.d(TAG, "║ PREPARING TO SAVE USER");
                Log.d(TAG, "╠═══════════════════════════════════════════");
                Log.d(TAG, "║ User ID: " + userId);
                Log.d(TAG, "║ Name: " + name);
                Log.d(TAG, "║ Role from text field: " + role);
                Log.d(TAG, "║ selectedRole variable: " + selectedRole);
                Log.d(TAG, "║ isSupervisor flag: " + isSupervisor);
                Log.d(TAG, "║ Comparison: \"ADMIN\".equals(\"" + selectedRole + "\") = " + "ADMIN".equals(selectedRole));
                Log.d(TAG, "║ selectedUserFromApi: " + (selectedUserFromApi != null ? selectedUserFromApi.getName() : "null"));
                Log.d(TAG, "╚═══════════════════════════════════════════");

                // API-FIRST APPROACH: Register with PAC API first
                mainHandler.post(() -> {
                    updateStatusText("Enrolling with PAC API...");
                    progressBar.setVisibility(View.VISIBLE);
                });

                // Check if this is a pre-registered user from API or manual entry
                if (selectedUserFromApi != null) {
                    // Pre-registered user from API - use enrollPreRegisteredUser
                    enrollPreRegisteredUser(selectedUserFromApi, finalFingerprintCount);
                } else {
                    // Manual entry or fallback - use JIT registration
                    registerWithPacApi(name, userId, role, isSupervisor, finalFingerprintCount);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error saving user data", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving user data", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Register user with PAC_API server - API-FIRST APPROACH
     * Success/failure is determined by API response only
     * Local database is updated AFTER successful API response for caching only
     */
    private void registerWithPacApi(String name, String employeeId, String role,
                                     boolean isSupervisor, int fingerprintCount) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ API-FIRST USER REGISTRATION");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Name: " + name);
        Log.d(TAG, "║ Employee ID: " + employeeId);
        Log.d(TAG, "║ Role: " + role);
        Log.d(TAG, "║ Fingerprints: " + fingerprintCount);
        Log.d(TAG, "║ Sending to PAC API...");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        UserRegistrationService service = new UserRegistrationService(this);

        service.registerUserWithFingerprints(
                name,                        // username (use name as username)
                name,                        // full name
                employeeId,                  // employeeNumber (Staff ID/Employee ID)
                role,                        // role (ADMIN or CUSTODIAN)
                role,                        // department (send role for now)
                "Main Branch",               // branch (default)
                leftFingerprintTemplate,     // left fingerprint template
                rightFingerprintTemplate,    // right fingerprint template
                new UserRegistrationCallback() {
                    @Override
                    public void onRegistrationSuccess(RegisterUserResponse response) {
                        runOnUiThread(() -> {
                            Log.i(TAG, "╔═══════════════════════════════════════════");
                            Log.i(TAG, "║ ✓ PAC API REGISTRATION SUCCESS");
                            Log.i(TAG, "╠═══════════════════════════════════════════");
                            Log.i(TAG, "║ Username: " + response.getUsername());
                            Log.i(TAG, "║ Fingerprints: " + response.getFingerprintsRegistered());
                            Log.i(TAG, "╚═══════════════════════════════════════════");

                            progressBar.setVisibility(View.GONE);

                            // OPTIONAL: Save to local database for caching/logging
                            saveToLocalDatabaseForCaching(employeeId, name, role, isSupervisor, fingerprintCount);

                            // Show success based on API response
                            showEnrollmentConfirmationDialog(name, employeeId, selectedRole, fingerprintCount);
                        });
                    }

                    @Override
                    public void onRegistrationError(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "╔═══════════════════════════════════════════");
                            Log.e(TAG, "║ ✗ PAC API REGISTRATION FAILED");
                            Log.e(TAG, "╠═══════════════════════════════════════════");
                            Log.e(TAG, "║ Error: " + error);
                            Log.e(TAG, "║ Employee ID: " + employeeId);
                            Log.e(TAG, "╚═══════════════════════════════════════════");

                            progressBar.setVisibility(View.GONE);

                            // Show failure based on API response
                            new AlertDialog.Builder(FingerprintEnrollmentActivity.this)
                                    .setTitle("Registration Failed")
                                    .setMessage("Failed to register with PAC API:\n\n" + error +
                                            "\n\nPlease try again or contact support.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                }
        );
    }

    /**
     * Enroll fingerprints for pre-registered user from API
     * Uses StaffEnrollmentService.enrollPreRegisteredUser()
     */
    private void enrollPreRegisteredUser(UserListItem apiUser, int fingerprintCount) {
        // IMPORTANT: Use selectedRole (set during role selection/first-time setup)
        // instead of apiUser.getRole() which may have different casing or format
        boolean willBeAdmin = "ADMIN".equals(selectedRole);

        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ ENROLL PRE-REGISTERED USER (API)");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Name: " + apiUser.getName());
        Log.d(TAG, "║ Employee Number: " + apiUser.getEmployeeNumber());
        Log.d(TAG, "║ Username: " + apiUser.getUsername());
        Log.d(TAG, "║ API Role: " + apiUser.getRole());
        Log.d(TAG, "║ Selected Role (App): " + selectedRole);
        Log.d(TAG, "║ Will be saved as ADMIN: " + willBeAdmin);
        Log.d(TAG, "║ Comparison: \"ADMIN\".equals(\"" + selectedRole + "\") = " + willBeAdmin);
        Log.d(TAG, "║ Fingerprints: " + fingerprintCount);
        Log.d(TAG, "║ Sending to PAC API (Staff Enrollment)...");
        Log.d(TAG, "╚═══════════════════════════════════════════");

        staffEnrollmentService.enrollPreRegisteredUser(
                apiUser.getUsername(),
                apiUser.getName(),
                apiUser.getEmployeeNumber(),
                apiUser.getRole(),
                apiUser.getDepartment(),
                apiUser.getBranch(),
                leftFingerprintTemplate,
                rightFingerprintTemplate,
                new StaffEnrollmentCallback() {
                    @Override
                    public void onEnrollmentSuccess(EnrollUserResponse response) {
                        runOnUiThread(() -> {
                            Log.i(TAG, "╔═══════════════════════════════════════════");
                            Log.i(TAG, "║ ✓ STAFF ENROLLMENT SUCCESS");
                            Log.i(TAG, "╠═══════════════════════════════════════════");
                            Log.i(TAG, "║ Username: " + response.getUsername());
                            Log.i(TAG, "║ Employee Number: " + response.getEmployeeNumber());
                            Log.i(TAG, "║ Fingerprints: " + response.getFingerprintsRegistered());
                            Log.i(TAG, "╚═══════════════════════════════════════════");

                            progressBar.setVisibility(View.GONE);

                            // OPTIONAL: Save to local database for caching/logging
                            // Use selectedRole (from app) instead of apiUser.getRole() for is_admin flag
                            boolean isSupervisor = "ADMIN".equals(selectedRole);
                            Log.d(TAG, "Saving to local DB - isSupervisor: " + isSupervisor + " (selectedRole: " + selectedRole + ")");
                            saveToLocalDatabaseForCaching(
                                    apiUser.getEmployeeNumber(),
                                    apiUser.getName(),
                                    selectedRole,  // Use selectedRole instead of apiUser.getRole()
                                    isSupervisor,
                                    fingerprintCount
                            );

                            // Show success message
                            showEnrollmentConfirmationDialog(
                                    apiUser.getName(),
                                    apiUser.getEmployeeNumber(),
                                    apiUser.getRole(),
                                    fingerprintCount
                            );
                        });
                    }

                    @Override
                    public void onEnrollmentError(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "╔═══════════════════════════════════════════");
                            Log.e(TAG, "║ ✗ STAFF ENROLLMENT FAILED");
                            Log.e(TAG, "╠═══════════════════════════════════════════");
                            Log.e(TAG, "║ Error: " + error);
                            Log.e(TAG, "║ Employee Number: " + apiUser.getEmployeeNumber());
                            Log.e(TAG, "╚═══════════════════════════════════════════");

                            progressBar.setVisibility(View.GONE);

                            // Show error message
                            new AlertDialog.Builder(FingerprintEnrollmentActivity.this)
                                    .setTitle("Enrollment Failed")
                                    .setMessage("Failed to enroll with PAC API:\n\n" + error +
                                            "\n\nPlease check your internet connection and try again.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }

                    @Override
                    public void onPendingUsersRetrieved(List<UserListItem> users) {
                        // Not used in this context
                    }

                    @Override
                    public void onUserValidated(UserListItem user) {
                        // Not used in this context
                    }

                    @Override
                    public void onUserNotFound() {
                        // Not used in this context
                    }

                    @Override
                    public void onUserDeleted() {
                        // Not used in this context
                    }

                    @Override
                    public void onFingerprintValidated(UserListItem user) {
                        // Not used in this context
                    }

                    @Override
                    public void onFingerprintNotFound() {
                        // Not used in this context
                    }

                    @Override
                    public void onSyncStarted(int totalFingerprints) {
                        // Not used in this context
                    }

                    @Override
                    public void onSyncProgress(int current, int total, String userName) {
                        // Not used in this context
                    }

                    @Override
                    public void onSyncCompleted(int successCount, int failCount) {
                        // Not used in this context
                    }
                }
        );
    }

    /**
     * OPTIONAL: Save user to local database for caching/logging purposes only
     * This is called AFTER successful API response
     * This does NOT affect success/failure shown to user
     */
    private void saveToLocalDatabaseForCaching(String userId, String name, String role,
                                               boolean isSupervisor, int fingerprintCount) {
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ SAVING TO LOCAL DATABASE FOR CACHING");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ User ID: " + userId);
        Log.d(TAG, "║ Name: " + name);
        Log.d(TAG, "║ Role: " + role);
        Log.d(TAG, "║ Is Supervisor (is_admin flag): " + isSupervisor);
        Log.d(TAG, "║ Fingerprint Count: " + fingerprintCount);
        Log.d(TAG, "║ Left Scanner ID: " + (isLeftScanned ? leftScannerUserId : 0));
        Log.d(TAG, "║ Right Scanner ID: " + (isRightScanned ? rightScannerUserId : 0));
        Log.d(TAG, "╚═══════════════════════════════════════════");

        // Check if user already exists in local database
        if (dbHelper.userExists(userId, userId)) {
            Log.w(TAG, "User already exists in local database - skipping local save");
            Log.w(TAG, "API enrollment was successful, but local database already has this user");

            // Verify the existing user has admin flag set correctly
            DatabaseHelper.User existingUser = dbHelper.getUserByStaffId(userId);
            if (existingUser != null) {
                Log.i(TAG, "Existing user in database:");
                Log.i(TAG, "  Name: " + existingUser.getName());
                Log.i(TAG, "  Is Admin: " + existingUser.isAdmin());
                Log.i(TAG, "  Left Scanner ID: " + existingUser.getLeftScannerUserId());
                Log.i(TAG, "  Right Scanner ID: " + existingUser.getRightScannerUserId());

                if (existingUser.isAdmin() != isSupervisor) {
                    Log.e(TAG, "❌ WARNING: Admin flag mismatch!");
                    Log.e(TAG, "   Expected: " + isSupervisor + ", Actual: " + existingUser.isAdmin());
                }
            }
            return;
        }

        try {
            long result = dbHelper.insertUserWithScannerMapping(
                    userId,                     // Use employee ID for both IC and user ID
                    userId,
                    name,
                    role,                       // Store role (ADMIN or CUSTODIAN)
                    leftFingerprintTemplate,    // Template data for finger 1
                    rightFingerprintTemplate,   // Template data for finger 2
                    isLeftScanned ? leftScannerUserId : 0,     // Scanner ID for finger 1
                    isRightScanned ? rightScannerUserId : 0,   // Scanner ID for finger 2
                    leftFingerprintTemplate2,                  // Template data for finger 3
                    rightFingerprintTemplate2,                 // Template data for finger 4
                    isLeftScanned2 ? leftScannerUserId2 : 0,   // Scanner ID for finger 3
                    isRightScanned2 ? rightScannerUserId2 : 0, // Scanner ID for finger 4
                    isSupervisor
            );

            if (result != -1) {
                Log.i(TAG, "╔═══════════════════════════════════════════");
                Log.i(TAG, "║ ✓ LOCAL DATABASE SAVE SUCCESS");
                Log.i(TAG, "╠═══════════════════════════════════════════");
                Log.i(TAG, "║ Database Row ID: " + result);
                Log.i(TAG, "║ User will be recognized as " + (isSupervisor ? "ADMIN" : "CUSTODIAN"));
                Log.i(TAG, "╚═══════════════════════════════════════════");

                // Mark personnel as enrolled in the registered_personnel table
                if (selectedPersonnel != null) {
                    boolean marked = dbHelper.markPersonnelAsEnrolled(selectedPersonnel.getEmployeeId(), (int) result);
                    Log.d(TAG, "Personnel marked as enrolled: " + marked);
                }

                // VERIFY the save by querying back
                DatabaseHelper.User savedUser = dbHelper.getUserByStaffId(userId);
                if (savedUser != null) {
                    Log.i(TAG, "VERIFICATION: User retrieved from DB");
                    Log.i(TAG, "  Name: " + savedUser.getName());
                    Log.i(TAG, "  Staff ID: " + savedUser.getStaffId());
                    Log.i(TAG, "  Is Admin: " + savedUser.isAdmin());
                    Log.i(TAG, "  Is Active: " + savedUser.isActive());
                    Log.i(TAG, "  Left Scanner ID: " + savedUser.getLeftScannerUserId());
                    Log.i(TAG, "  Right Scanner ID: " + savedUser.getRightScannerUserId());
                } else {
                    Log.e(TAG, "❌ VERIFICATION FAILED: Could not retrieve saved user from database!");
                }
            } else {
                Log.e(TAG, "╔═══════════════════════════════════════════");
                Log.e(TAG, "║ ❌ LOCAL DATABASE SAVE FAILED");
                Log.e(TAG, "╠═══════════════════════════════════════════");
                Log.e(TAG, "║ insertUserWithScannerMapping returned -1");
                Log.e(TAG, "║ This means the database insert failed!");
                Log.e(TAG, "║ Possible reasons:");
                Log.e(TAG, "║   1. Duplicate IC Number or Staff ID");
                Log.e(TAG, "║   2. Database permission issue");
                Log.e(TAG, "║   3. Database corruption");
                Log.e(TAG, "╚═══════════════════════════════════════════");
            }
        } catch (Exception e) {
            Log.e(TAG, "╔═══════════════════════════════════════════");
            Log.e(TAG, "║ ❌ EXCEPTION DURING LOCAL DATABASE SAVE");
            Log.e(TAG, "╠═══════════════════════════════════════════");
            Log.e(TAG, "║ Exception: " + e.getClass().getName());
            Log.e(TAG, "║ Message: " + e.getMessage());
            Log.e(TAG, "╚═══════════════════════════════════════════");
            e.printStackTrace();
        }
    }

    /**
     * Show enrollment confirmation dialog with person details
     */
    private void showEnrollmentConfirmationDialog(String name, String employeeId, String role, int fingerprintCount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enrollment_confirmation, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog confirmationDialog = builder.create();

        TextView tvRoleLabel = dialogView.findViewById(R.id.tvRoleLabel);
        TextView tvPersonName = dialogView.findViewById(R.id.tvPersonName);
        TextView tvEmployeeId = dialogView.findViewById(R.id.tvEmployeeId);
        TextView tvFingerprintsCount = dialogView.findViewById(R.id.tvFingerprintsCount);
        MaterialButton btnDone = dialogView.findViewById(R.id.btnDone);

        // Set the data
        tvRoleLabel.setText(role != null ? role : "USER");
        tvPersonName.setText(name);
        tvEmployeeId.setText(employeeId);
        tvFingerprintsCount.setText(fingerprintCount + " fingerprint" + (fingerprintCount > 1 ? "s" : "") + " enrolled");

        // For first-time setup, navigate to MainMenuActivity after enrollment
        if (isFirstTimeSetup) {
            btnDone.setText("Continue to Main Menu");
            btnDone.setOnClickListener(v -> {
                confirmationDialog.dismiss();
                // Navigate to MainMenuActivity
                Intent intent = new Intent(FingerprintEnrollmentActivity.this, MainMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        } else {
            btnDone.setOnClickListener(v -> {
                confirmationDialog.dismiss();
                finish();  // Close the activity
            });
        }

        confirmationDialog.show();
    }

    // =================== UTILITY METHODS ===================

    /**
     * Update the status text with current time
     */
    private void updateStatusText(String message) {
        statusText.setText(getCurrentTime() + " - " + message);
    }

    /**
     * Get current time as formatted string
     */
    private String getCurrentTime() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(new Date());
    }

    // =================== ACTIVITY LIFECYCLE METHODS ===================

    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect to scanner when activity resumes
        if (sdk != null) {
            sdk.UF_Reconnect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Cancel any ongoing scan when activity pauses
        if (isScanning && sdk != null) {
            sdk.UF_Cancel(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up resources
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }

        // IMPORTANT: Cancel any ongoing scan but DON'T close the port
        // Port lifecycle is managed by MainMenuActivity (the parent activity)
        if (sdk != null) {
            if (isScanning) {
                sdk.UF_Cancel(false);
                Log.d(TAG, "Scanner scan cancelled in onDestroy");
            }
            // DO NOT call UF_CloseCommPort() - let MainMenuActivity manage the port
        }
    }
}