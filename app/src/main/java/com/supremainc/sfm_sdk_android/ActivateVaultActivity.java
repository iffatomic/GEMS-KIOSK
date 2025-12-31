/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.UsbService;
import com.supremainc.sfm_sdk.callback_interface.SFM_SDK_ANDROID_CALLBACK_INTERFACE;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;
import com.supremainc.sfm_sdk.message_handler.MessageHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ActivateVaultActivity handles 3-user fingerprint verification for vault access
 *
 * SIMPLE 3-USER VERIFICATION CONCEPT:
 * - Track up to 3 different users who verify their fingerprints
 * - Only allow vault opening when all 3 users have been verified
 * - Use simple session management with timeout
 * - Reset session after vault opens or timeout
 */
public class ActivateVaultActivity extends AppCompatActivity {

    private static final String TAG = "ActivateVaultActivity";

    // 3-User Verification Constants
    private static final int REQUIRED_USERS = 3;           // Need exactly 3 users
    private static final long SESSION_TIMEOUT = 10 * 60 * 1000; // 10 minutes in milliseconds

    // UI Components
    private ImageButton backButton;
    private TextView titleText, statusText, instructionText;
    private LinearLayout vaultProgressContainer;
    private TextView progressText, waitingMessage;
    private LinearLayout verifiedUsersList;
    private View progressDot1, progressDot2, progressDot3;
    private Button scanButton, confirmButton, retryButton;
    private ProgressBar progressBar;
    private ImageView fingerprintImage;
    private Spinner languageSpinner;

    // SDK and Database
    private SFM_SDK_ANDROID sdk;
    private DatabaseHelper dbHelper;

    // 3-User Verification Data
    private ArrayList<VerifiedUser> verifiedUsers;  // List of verified users
    private long sessionStartTime;                  // When current session started
    private boolean isSessionActive;                // Whether session is active
    private boolean isScanning;                     // Whether currently scanning

    // Language preferences
    private SharedPreferences languagePrefs;
    private static final String LANGUAGE_PREF = "language_pref";
    private static final String SELECTED_LANGUAGE = "selected_language";
    private final String[] languageCodes = {"en", "ms"};
    private final String[] languageNames = {"English", "Bahasa Malaysia"};

    // Background task management
    private ExecutorService executor;
    private Handler mainHandler;

    // =================== VERIFIED USER CLASS ===================

    /**
     * Simple class to store information about a verified user
     */
    private static class VerifiedUser {
        int userId;
        String name;
        String department;
        String fingerUsed;
        String timestamp;

        VerifiedUser(int userId, String name, String department, String fingerUsed, String timestamp) {
            this.userId = userId;
            this.name = name;
            this.department = department;
            this.fingerUsed = fingerUsed;
            this.timestamp = timestamp;
        }
    }

    // =================== SDK CALLBACK DEFINITIONS ===================

    private final SFM_SDK_ANDROID.ReceiveDataPacketCallback receiveDataPacketCallback =
            new SFM_SDK_ANDROID.ReceiveDataPacketCallback() {
                @Override
                public void callback(int index, int numOfPacket) {
                    // Handle data packet reception - not critical for identification
                }
            };

    private final SFM_SDK_ANDROID.SendRawDataCallback sendRawDataCallback =
            new SFM_SDK_ANDROID.SendRawDataCallback() {
                @Override
                public void callback(int writtenLen, int totalSize) {
                    // Handle raw data sending - not critical for identification
                }
            };

    private final SFM_SDK_ANDROID.ReceiveRawDataCallback receiveRawDataCallback =
            new SFM_SDK_ANDROID.ReceiveRawDataCallback() {
                @Override
                public void callback(int readLen, int totalSize) {
                    // Handle raw data reception - not critical for identification
                }
            };

    /**
     * Called when fingerprint scan is completed
     */
    private final SFM_SDK_ANDROID.ScanCallback scanCallback =
            new SFM_SDK_ANDROID.ScanCallback() {
                @Override
                public void callback(byte errCode) {
                    runOnUiThread(() -> {
                        if (errCode == 0) {
                            updateStatus("Fingerprint scan successful! Identifying user...");
                        } else {
                            updateStatus("Scan failed. Please try again.");
                            resetScanningState();
                        }
                    });
                }
            };

    /**
     * CRITICAL CALLBACK: Called when fingerprint identification is completed
     */
    private final SFM_SDK_ANDROID.IdentifyCallback identifyCallback =
            new SFM_SDK_ANDROID_CALLBACK_INTERFACE.IdentifyCallback() {
                @Override
                public void callback(byte errCode) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        isScanning = false;
                        scanButton.setEnabled(true);

                        if (errCode == 0) {
                            updateStatus("User identified successfully!");
                            // Note: The actual user ID will be retrieved in the main scanning method
                        } else {
                            updateStatus("User not found or identification failed.");
                            showToast("User not recognized. Please try again.");
                        }
                    });
                }
            };

    /**
     * Handles messages from USB service
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
                    showToast("Scanner connection changed");
                    break;
                case UsbService.DSR_CHANGE:
                    showToast("Scanner status changed");
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
        loadLanguagePreference();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activate_vault);

        initializeComponents();
        initializeSDK();
        setupLanguageSpinner();
        setupClickListeners();
        initializeSession();
    }

    /**
     * Initialize all UI components and variables
     */
    private void initializeComponents() {
        // Connect UI elements to variables
        backButton = findViewById(R.id.back_button);
        titleText = findViewById(R.id.title_text);
        statusText = findViewById(R.id.status_text);
        instructionText = findViewById(R.id.instruction_text);

        // Vault progress components
        vaultProgressContainer = findViewById(R.id.vault_progress_container);
        progressText = findViewById(R.id.progress_text);
        waitingMessage = findViewById(R.id.waiting_message);
        verifiedUsersList = findViewById(R.id.verified_users_list);
        progressDot1 = findViewById(R.id.progress_dot_1);
        progressDot2 = findViewById(R.id.progress_dot_2);
        progressDot3 = findViewById(R.id.progress_dot_3);

        scanButton = findViewById(R.id.scan_button);
        confirmButton = findViewById(R.id.confirm_button);
        retryButton = findViewById(R.id.retry_button);
        progressBar = findViewById(R.id.progress_bar);
        fingerprintImage = findViewById(R.id.fingerprint_image);
        languageSpinner = findViewById(R.id.language_spinner);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Initialize background task management
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize language preferences
        languagePrefs = getSharedPreferences(LANGUAGE_PREF, MODE_PRIVATE);

        // Initialize 3-user verification data
        verifiedUsers = new ArrayList<>();
        isSessionActive = false;
        isScanning = false;

        // Initially hide progress and action buttons
        vaultProgressContainer.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    /**
     * Initialize the fingerprint scanner SDK
     */
    private void initializeSDK() {
        try {
            sdk = new SFM_SDK_ANDROID();
            String version = "SDK Version: " + sdk.UF_GetSDKVersion();
            Log.d(TAG, version);

            sdk.UF_InitSysParameter();

            UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);
            if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
                Log.d(TAG, "Trying /dev/ttyACM1");
            }

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                updateStatus("Fingerprint scanner connected successfully");
                setupSDKCallbacks();
                sdk.UF_Reconnect();
            } else {
                updateStatus("Failed to connect to fingerprint scanner");
                Log.e(TAG, "Failed to initialize comm port: " + ret);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing SDK", e);
            updateStatus("Error initializing fingerprint scanner");
        }
    }

    /**
     * Set up callback functions for SDK events
     */
    private void setupSDKCallbacks() {
        sdk.UF_SetSendRawDataCallback(sendRawDataCallback);
        sdk.UF_SetReceiveRawDataCallback(receiveRawDataCallback);
        sdk.UF_SetReceiveDataPacketCallback(receiveDataPacketCallback);
        sdk.UF_SetScanCallback(scanCallback);
        sdk.UF_SetIdentifyCallback(identifyCallback);
    }

    // =================== SESSION MANAGEMENT ===================

    /**
     * Initialize a new vault opening session
     */
    private void initializeSession() {
        verifiedUsers.clear();
        isSessionActive = false;
        sessionStartTime = 0;
        updateUI();
        updateStatus("Ready to scan fingerprint - Need 3 authorized users");
    }

    /**
     * Start a new vault opening session
     */
    private void startSession() {
        verifiedUsers.clear();
        isSessionActive = true;
        sessionStartTime = System.currentTimeMillis();
        vaultProgressContainer.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        updateUI();
        updateStatus("Vault session started - Scan first fingerprint");
        Log.d(TAG, "New vault session started");
    }

    /**
     * Check if current session has expired
     */
    private boolean isSessionExpired() {
        if (!isSessionActive) return false;
        return (System.currentTimeMillis() - sessionStartTime) > SESSION_TIMEOUT;
    }

    /**
     * Reset the current session
     */
    private void resetSession() {
        verifiedUsers.clear();
        isSessionActive = false;
        sessionStartTime = 0;
        vaultProgressContainer.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        updateUI();
        updateStatus("Session reset - Ready to start new vault opening");
        showToast("Session reset");
        Log.d(TAG, "Vault session reset");
    }

    /**
     * Complete the session and open vault
     */
    private void completeSession() {
        // Record attendance for all verified users
        for (VerifiedUser user : verifiedUsers) {
            dbHelper.insertAttendance(user.userId, "vault_access", user.fingerUsed);
        }

        updateStatus("VAULT UNLOCKED! Access granted to all verified users.");
        showToast("VAULT OPENED SUCCESSFULLY!");

        // Reset session after successful opening
        mainHandler.postDelayed(() -> {
            resetSession();
            finish(); // Close activity
        }, 3000); // 3 second delay to show success message
    }

    // =================== USER VERIFICATION METHODS ===================

    /**
     * Check if user is already verified in current session
     */
    private boolean isUserAlreadyVerified(int userId) {
        for (VerifiedUser user : verifiedUsers) {
            if (user.userId == userId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a verified user to the session
     */
    private void addVerifiedUser(DatabaseHelper.User user) {
        String timestamp = getCurrentTime();
        VerifiedUser verifiedUser = new VerifiedUser(
                user.getId(),
                user.getName(),
                user.getDepartment(),
                user.getLastFingerUsed(),
                timestamp
        );

        verifiedUsers.add(verifiedUser);
        Log.d(TAG, "User verified: " + user.getName() + " (" + verifiedUsers.size() + "/" + REQUIRED_USERS + ")");

        updateUI();

        if (verifiedUsers.size() >= REQUIRED_USERS) {
            // All users verified - show open vault button
            confirmButton.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
            updateStatus("All users verified! Ready to open vault.");
            showToast("All 3 users verified - Ready to open vault!");
        } else {
            // Need more users
            int remaining = REQUIRED_USERS - verifiedUsers.size();
            updateStatus("User verified! Need " + remaining + " more user" + (remaining > 1 ? "s" : ""));
            showToast("User verified: " + user.getName());
        }
    }

    // =================== FINGERPRINT SCANNING METHODS ===================

    /**
     * Start fingerprint identification process
     */
    private void startFingerprintScan() {
        // Check if session expired
        if (isSessionActive && isSessionExpired()) {
            resetSession();
            showToast("Session expired - Starting new session");
            return;
        }

        // Start session if not active
        if (!isSessionActive) {
            startSession();
        }

        if (isScanning) {
            showToast("Please wait for current scan to complete");
            return;
        }

        // Check if all users already verified
        if (verifiedUsers.size() >= REQUIRED_USERS) {
            showToast("All users already verified - Click 'OPEN VAULT'");
            return;
        }

        isScanning = true;
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        fingerprintImage.setImageResource(R.drawable.fingerprint_icon);

        int remaining = REQUIRED_USERS - verifiedUsers.size();
        updateStatus("Place finger on scanner... (" + remaining + " more user" + (remaining > 1 ? "s" : "") + " needed)");

        // Run identification in background thread
        executor.execute(() -> {
            try {
                sdk.UF_Reconnect();

                // Identify the fingerprint
                int[] userID = new int[1];
                byte[] subID = new byte[1];
                UF_RET_CODE ret = sdk.UF_Identify(userID, subID);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isScanning = false;
                    scanButton.setEnabled(true);

                    if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                        Log.d(TAG, "Fingerprint identified with scanner ID: " + userID[0]);
                        processIdentifiedUser(userID[0]);
                    } else {
                        updateStatus("Identification failed. Please try again.");
                        showToast("User not recognized");
                        Log.e(TAG, "Identify failed: " + ret);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error during fingerprint scan", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isScanning = false;
                    scanButton.setEnabled(true);
                    updateStatus("Error during scan. Please try again.");
                    showToast("Scanner error occurred");
                });
            }
        });
    }

    /**
     * Process an identified user from scanner ID
     */
    private void processIdentifiedUser(int scannerUserId) {
        executor.execute(() -> {
            try {
                // Look up user by scanner user ID
                DatabaseHelper.User user = dbHelper.getUserByScannerUserId(scannerUserId);

                mainHandler.post(() -> {
                    if (user != null) {
                        // Check if user already verified in this session
                        if (isUserAlreadyVerified(user.getId())) {
                            updateStatus("User " + user.getName() + " already verified in this session");
                            showToast("Already verified: " + user.getName());
                        } else {
                            // Add user to verified list
                            addVerifiedUser(user);
                        }
                    } else {
                        updateStatus("Scanner ID " + scannerUserId + " not found in database");
                        showToast("User record not found");
                        Log.e(TAG, "No user found for scanner ID: " + scannerUserId);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error retrieving user from database", e);
                mainHandler.post(() -> {
                    updateStatus("Error retrieving user data");
                    showToast("Database error");
                });
            }
        });
    }

    /**
     * Reset scanning state
     */
    private void resetScanningState() {
        isScanning = false;
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        fingerprintImage.setImageResource(R.drawable.fingerprint_icon);
    }

    // =================== UI UPDATE METHODS ===================

    /**
     * Update all UI elements based on current session state
     */
    private void updateUI() {
        updateProgressDisplay();
        updateVerifiedUsersList();
        updateWaitingMessage();
    }

    /**
     * Update progress dots and text
     */
    private void updateProgressDisplay() {
        int verifiedCount = verifiedUsers.size();

        // Update progress text
        progressText.setText(verifiedCount + " of " + REQUIRED_USERS + " users verified");

        // Update progress dots
        updateProgressDot(progressDot1, verifiedCount >= 1);
        updateProgressDot(progressDot2, verifiedCount >= 2);
        updateProgressDot(progressDot3, verifiedCount >= 3);
    }

    /**
     * Update individual progress dot appearance
     */
    private void updateProgressDot(View dot, boolean filled) {
        if (filled) {
            dot.setBackgroundResource(R.drawable.progress_dot_filled);
        } else {
            dot.setBackgroundResource(R.drawable.progress_dot_empty);
        }
    }

    /**
     * Update the list of verified users with clickable card-style entries
     */
    private void updateVerifiedUsersList() {
        // Clear existing views
        verifiedUsersList.removeAllViews();

        // Add card-style TextView for each verified user
        for (int i = 0; i < verifiedUsers.size(); i++) {
            VerifiedUser user = verifiedUsers.get(i);

            // Create container for card effect
            LinearLayout cardContainer = new LinearLayout(this);
            cardContainer.setOrientation(LinearLayout.VERTICAL);

            // Set white background first (this was working in your version 1)
            cardContainer.setBackgroundColor(getResources().getColor(android.R.color.white));
            cardContainer.setPadding(16, 12, 16, 12);

            // Make it clickable and focusable
            cardContainer.setClickable(true);
            cardContainer.setFocusable(true);

            // Add elevation for modern look (if supported)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                cardContainer.setElevation(4);
            }

            // Create the main text view
            TextView userView = new TextView(this);
            userView.setText("✅ " + user.name + " (" + user.department + ") - " + user.timestamp);
            userView.setTextSize(14);
            userView.setTextColor(getResources().getColor(android.R.color.black)); // Black text as requested
            userView.setTypeface(null, Typeface.NORMAL);


            // Add views to card container
            cardContainer.addView(userView);

            // Add click listener to show user details
            final int userIndex = i; // Final variable for lambda
            cardContainer.setOnClickListener(v -> {
                // Add visual feedback on click
                cardContainer.setBackgroundColor(getResources().getColor(R.color.cardview_light_background));

                // Reset background after short delay
                cardContainer.postDelayed(() -> {
                    cardContainer.setBackgroundColor(getResources().getColor(android.R.color.white));
                }, 150);

                // Show user details
                showUserDetailsDialog(user, userIndex + 1);
            });

            // Add margin between cards
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 12); // Space between cards
            cardContainer.setLayoutParams(params);

            verifiedUsersList.addView(cardContainer);
        }
    }

    /**
     * Show detailed user information in a modal dialog
     */
    private void showUserDetailsDialog(VerifiedUser verifiedUser, int verificationOrder) {
        // Get full user details from database
        executor.execute(() -> {
            try {
                DatabaseHelper.User fullUser = dbHelper.getUserById(verifiedUser.userId);

                mainHandler.post(() -> {
                    if (fullUser != null) {
                        showUserDetailsModal(fullUser, verifiedUser, verificationOrder);
                    } else {
                        showToast("Could not load user details");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading user details", e);
                mainHandler.post(() -> showToast("Error loading user details"));
            }
        });
    }

    /**
     * Display the user details modal with complete information
     */
    private void showUserDetailsModal(DatabaseHelper.User fullUser, VerifiedUser verifiedUser, int order) {
        // Create a custom dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        // Create custom view for the dialog
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 30, 40, 20);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText("👤 User Verification Details");
        titleView.setTextSize(20);
        titleView.setTextColor(getResources().getColor(android.R.color.black));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 20);
        dialogLayout.addView(titleView);

        // Verification order
        TextView orderView = new TextView(this);
        orderView.setText("🔢 Verification Order: " + getOrdinalNumber(order) + " user to verify");
        orderView.setTextSize(14);
        orderView.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        orderView.setTypeface(null, android.graphics.Typeface.BOLD);
        orderView.setPadding(0, 0, 0, 15);
        dialogLayout.addView(orderView);

        // User details
        addDetailRow(dialogLayout, "👤 Name", fullUser.getName());
        addDetailRow(dialogLayout, "🆔 IC Number", fullUser.getIcNumber());
        addDetailRow(dialogLayout, "🏢 Staff ID", fullUser.getStaffId());
        addDetailRow(dialogLayout, "🏛️ Department", fullUser.getDepartment());
        addDetailRow(dialogLayout, "👆 Finger Used", capitalizeFirst(verifiedUser.fingerUsed) + " finger");
        addDetailRow(dialogLayout, "⏰ Verification Time", verifiedUser.timestamp);
        addDetailRow(dialogLayout, "📅 Account Created", formatDate(fullUser.getCreatedAt()));

        // Add fingerprint status
        String fingerprintStatus = "";
        if (fullUser.getLeftFingerprint() != null && fullUser.getRightFingerprint() != null) {
            fingerprintStatus = "Both fingers enrolled";
        } else if (fullUser.getLeftFingerprint() != null) {
            fingerprintStatus = "Left finger only";
        } else if (fullUser.getRightFingerprint() != null) {
            fingerprintStatus = "Right finger only";
        } else {
            fingerprintStatus = "No fingerprints";
        }
        addDetailRow(dialogLayout, "🔐 Enrolled Fingers", fingerprintStatus);

        // Security info
        if (fullUser.isAdmin()) {
            TextView securityView = new TextView(this);
            securityView.setText("🛡️ ADMINISTRATOR PRIVILEGES");
            securityView.setTextSize(12);
            securityView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            securityView.setTypeface(null, android.graphics.Typeface.BOLD);
            securityView.setPadding(0, 10, 0, 0);
            securityView.setGravity(android.view.Gravity.CENTER);
            dialogLayout.addView(securityView);
        }

        // Set the custom view
        builder.setView(dialogLayout);

        // Add close button
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        // Add remove user button (optional - for security purposes)
        builder.setNegativeButton("Remove from Session", (dialog, which) -> {
            removeUserFromSession(verifiedUser.userId);
            dialog.dismiss();
        });

        // Create and show dialog
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        if (dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(
                    getResources().getColor(android.R.color.holo_blue_dark));
        }
        if (dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    /**
     * Add a detail row to the dialog layout
     */
    private void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 5, 0, 5);

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setTextSize(14);
        labelView.setTextColor(getResources().getColor(android.R.color.black));
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView valueView = new TextView(this);
        valueView.setText(value != null ? value : "N/A");
        valueView.setTextSize(14);
        valueView.setTextColor(getResources().getColor(android.R.color.black));
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        rowLayout.addView(labelView);
        rowLayout.addView(valueView);
        parent.addView(rowLayout);
    }

    /**
     * Remove a user from the current verification session
     */
    private void removeUserFromSession(int userId) {
        for (int i = 0; i < verifiedUsers.size(); i++) {
            if (verifiedUsers.get(i).userId == userId) {
                VerifiedUser removedUser = verifiedUsers.remove(i);
                updateUI();

                // Re-enable scan button if we're no longer at max users
                if (verifiedUsers.size() < REQUIRED_USERS) {
                    scanButton.setEnabled(true);
                    confirmButton.setVisibility(View.GONE);
                }

                updateStatus("User " + removedUser.name + " removed from session");
                showToast("User removed: " + removedUser.name);
                Log.d(TAG, "User removed from session: " + removedUser.name);
                break;
            }
        }
    }

    /**
     * Helper method to get ordinal numbers (1st, 2nd, 3rd)
     */
    private String getOrdinalNumber(int number) {
        if (number >= 11 && number <= 13) {
            return number + "th";
        }
        switch (number % 10) {
            case 1: return number + "st";
            case 2: return number + "nd";
            case 3: return number + "rd";
            default: return number + "th";
        }
    }

    /**
     * Helper method to capitalize first letter
     */
    private String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    /**
     * Helper method to format date strings
     */
    private String formatDate(String dateString) {
        if (dateString == null) return "Unknown";
        try {
            // If the date is already in a good format, return as is
            if (dateString.contains("-") && dateString.length() >= 10) {
                return dateString.substring(0, 10); // Just the date part
            }
            return dateString;
        } catch (Exception e) {
            return dateString;
        }
    }

    /**
     * Update waiting message based on progress
     */
    private void updateWaitingMessage() {
        int verifiedCount = verifiedUsers.size();
        int remaining = REQUIRED_USERS - verifiedCount;

        if (verifiedCount == 0) {
            waitingMessage.setText("⏳ Waiting for first user...");
        } else if (verifiedCount < REQUIRED_USERS) {
            waitingMessage.setText("⏳ Waiting for " + remaining + " more user" + (remaining > 1 ? "s" : "") + "...");
        } else {
            waitingMessage.setText("✅ All users verified! Ready to open vault.");
            waitingMessage.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    // =================== LANGUAGE HANDLING (Same as before) ===================

    private void setupLanguageSpinner() {
        if (languageSpinner == null) return;

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
            public void onNothingSelected(AdapterView<?> parent) {}
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

    // =================== BUTTON CLICK HANDLERS ===================

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        scanButton.setOnClickListener(v -> startFingerprintScan());
        confirmButton.setOnClickListener(v -> completeSession());
        retryButton.setOnClickListener(v -> resetSession());
    }

    // =================== UTILITY METHODS ===================

    private void updateStatus(String message) {
        statusText.setText(getCurrentTime() + " - " + message);
    }

    private String getCurrentTime() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(new Date());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // =================== ACTIVITY LIFECYCLE METHODS ===================

    @Override
    protected void onResume() {
        super.onResume();
        if (sdk != null) {
            sdk.UF_Reconnect();
        }

        // Check session timeout on resume
        if (isSessionActive && isSessionExpired()) {
            resetSession();
            showToast("Session expired due to inactivity");
        }

        if (languageSpinner != null) {
            String currentLanguage = languagePrefs.getString(SELECTED_LANGUAGE, "en");
            int currentPosition = getLanguagePosition(currentLanguage);
            languageSpinner.setSelection(currentPosition);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isScanning && sdk != null) {
            sdk.UF_Cancel(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
        if (sdk != null && isScanning) {
            sdk.UF_Cancel(false);
        }
    }
}