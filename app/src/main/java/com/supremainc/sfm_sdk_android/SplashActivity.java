/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;
import com.supremainc.sfm_sdk_android.network.callbacks.StaffEnrollmentCallback;
import com.supremainc.sfm_sdk_android.service.StaffEnrollmentService;
import com.supremainc.sfm_sdk_android.util.ApiConstants;
import com.supremainc.sfm_sdk_android.util.ConfigManager;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private DatabaseHelper dbHelper;
    private StaffEnrollmentService staffEnrollmentService;
    private ImageView gemsLogo;
    private TextView gemsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize configuration from JSON (loads config.json and syncs to ApiConstants)
        // Using initialize() to use persisted config (allows user changes via SystemSettings)
        // If config.json doesn't exist, it will create one from default_config.json in assets
        ConfigManager.initialize(this);
        String apiUrl = ConfigManager.getConfig().getBaseUrl();
        Log.d(TAG, "Config initialized - Base URL: " + apiUrl);

        // Show API connection toast
        Toast.makeText(this, "Connecting to API: " + apiUrl, Toast.LENGTH_LONG).show();

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Initialize staff enrollment service for API verification
        staffEnrollmentService = new StaffEnrollmentService(this);

        // Get views
        gemsLogo = findViewById(R.id.gemsLogo);
        gemsText = findViewById(R.id.gemsText);

        // Check if database reset is requested from config
        if (ConfigManager.getConfig().getTestingFlags().isResetDatabase()) {
            Log.w(TAG, "╔════════════════════════════════════════════════════════════");
            Log.w(TAG, "║ RESET DATABASE FLAG DETECTED IN CONFIG");
            Log.w(TAG, "║ Performing full system reset...");
            Log.w(TAG, "╚════════════════════════════════════════════════════════════");
            performConfigBasedReset();
            return; // Don't start animation, reset will handle navigation
        }

        // Start animation sequence
        startSplashAnimation();
    }

    /**
     * Start the splash screen animation sequence:
     * 1. Logo fades in from slightly above center to center
     * 2. Text fades in from slightly below center to center
     * 3. Navigate to next screen
     */
    private void startSplashAnimation() {
        // Animation 1: Logo fade in from top to center
        gemsLogo.setTranslationY(-100f);  // Start 100px above
        gemsLogo.setAlpha(0f);

        gemsLogo.animate()
                .translationY(0f)  // Move to center
                .alpha(1f)         // Fade in
                .setDuration(1000) // 1 second
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // After logo animation, start text animation
                        animateText();
                    }
                })
                .start();
    }

    /**
     * Animate the GEMS text after logo animation completes
     */
    private void animateText() {
        // Animation 2: Text fade in from bottom to center
        gemsText.setTranslationY(100f);  // Start 100px below
        gemsText.setAlpha(0f);

        gemsText.animate()
                .translationY(0f)  // Move to center
                .alpha(1f)         // Fade in
                .setDuration(1000) // 1 second
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setStartDelay(200)  // Slight delay after logo
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // After text animation, navigate to next screen
                        navigateToNextScreen();
                    }
                })
                .start();
    }

    /**
     * Check API connectivity and navigate to MainMenuActivity
     *
     * SIMPLIFIED FLOW:
     * - Attempt to connect to PAC API
     * - Show connection status via Toast (success/failure)
     * - Always navigate to MainMenuActivity regardless of result
     */
    private void navigateToNextScreen() {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ SPLASH SCREEN - CHECKING API CONNECTION");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ API URL: " + ConfigManager.getConfig().getBaseUrl());
        Log.d(TAG, "║ Testing connection...");
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Try to connect to API to verify connectivity
        staffEnrollmentService.getEnrolledAdminUsers(new StaffEnrollmentCallback() {
            @Override
            public void onPendingUsersRetrieved(List<UserListItem> users) {
                // API connection successful
                runOnUiThread(() -> {
                    Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.d(TAG, "║ ✓ API CONNECTION SUCCESSFUL");
                    Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                    Log.d(TAG, "║ Server responded successfully");
                    Log.d(TAG, "║ Enrolled users found: " + (users != null ? users.size() : 0));
                    Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                    // Check if there are any enrolled users
                    boolean hasEnrolledUsers = users != null && !users.isEmpty();

                    if (hasEnrolledUsers) {
                        // Show success toast
                        Toast.makeText(SplashActivity.this,
                            "✓ API Connected Successfully",
                            Toast.LENGTH_SHORT).show();

                        // Navigate to MainMenuActivity after short delay
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            startActivity(new Intent(SplashActivity.this, MainMenuActivity.class));
                            finish();
                        }, 500);
                    } else {
                        // No enrolled users - navigate to first-time setup
                        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                        Log.d(TAG, "║ NO ENROLLED USERS FOUND - FIRST TIME SETUP REQUIRED");
                        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                        Toast.makeText(SplashActivity.this,
                            "No enrolled users found. Please login...",
                            Toast.LENGTH_LONG).show();

                        // Navigate to LoginActivity (GEMS Original - no local enrollment)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        }, 1000);
                    }
                });
            }

            @Override
            public void onEnrollmentError(String error) {
                // API connection failed - check local database for enrolled users
                runOnUiThread(() -> {
                    Log.w(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.w(TAG, "║ ✗ API CONNECTION FAILED");
                    Log.w(TAG, "╠════════════════════════════════════════════════════════════");
                    Log.w(TAG, "║ Error: " + error);
                    Log.w(TAG, "║ Checking local database for enrolled users...");
                    Log.w(TAG, "╚════════════════════════════════════════════════════════════");

                    // Check if there are any enrolled users in local database
                    boolean hasLocalUsers = dbHelper.hasAnyEnrolledUsers();

                    if (hasLocalUsers) {
                        // Has local users - proceed to MainMenu
                        Log.w(TAG, "║ Found enrolled users in local database");
                        Log.w(TAG, "║ Proceeding to MainMenu...");

                        Toast.makeText(SplashActivity.this,
                            "✗ API Connection Failed\nProceeding with local data...",
                            Toast.LENGTH_LONG).show();

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            startActivity(new Intent(SplashActivity.this, MainMenuActivity.class));
                            finish();
                        }, 1000);
                    } else {
                        // No local users - navigate to login
                        Log.w(TAG, "║ No enrolled users found");
                        Log.w(TAG, "║ Navigating to login...");

                        Toast.makeText(SplashActivity.this,
                            "No enrolled users found. Please login...",
                            Toast.LENGTH_LONG).show();

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        }, 1000);
                    }
                });
            }

            @Override
            public void onEnrollmentSuccess(com.supremainc.sfm_sdk_android.data.model.response.EnrollUserResponse response) {
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
        });
    }

    /**
     * Perform full system reset based on config.json flag
     * Resets both database and scanner memory, then sets the flag back to false
     */
    private void performConfigBasedReset() {
        Toast.makeText(this, "Resetting system from config... Please wait", Toast.LENGTH_LONG).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean databaseReset = false;
            boolean scannerReset = false;
            String errorMessage = null;
            SFM_SDK_ANDROID sdk = null;

            // Step 1: Reset database
            try {
                Log.d(TAG, "║ Resetting database...");
                dbHelper.resetDatabase();
                databaseReset = true;
                Log.d(TAG, "║ ✓ Database reset successful");
            } catch (Exception e) {
                Log.e(TAG, "║ ✗ Database reset failed", e);
                errorMessage = "Database reset failed: " + e.getMessage();
            }

            // Step 2: Clear scanner memory
            try {
                Log.d(TAG, "║ Initializing scanner for memory clear...");
                sdk = new SFM_SDK_ANDROID();
                sdk.UF_InitSysParameter();

                // Try ACM0 first
                UF_RET_CODE ret = sdk.UF_InitCommPort("/dev/ttyACM0", 115200, false);
                if (ret == UF_RET_CODE.UF_ERR_CANNOT_OPEN_SERIAL) {
                    ret = sdk.UF_InitCommPort("/dev/ttyACM1", 115200, false);
                }

                if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                    Thread.sleep(300);
                    sdk.UF_Reconnect();
                    Thread.sleep(200);

                    Log.d(TAG, "║ Clearing scanner memory...");
                    UF_RET_CODE deleteRet = sdk.UF_DeleteAll();

                    if (deleteRet == UF_RET_CODE.UF_RET_SUCCESS) {
                        scannerReset = true;
                        Log.d(TAG, "║ ✓ Scanner memory cleared successfully");
                    } else {
                        Log.e(TAG, "║ ✗ Scanner memory clear failed: " + deleteRet);
                        errorMessage = (errorMessage != null ? errorMessage + "\n" : "") +
                                      "Scanner clear failed: " + deleteRet;
                    }
                } else {
                    Log.e(TAG, "║ ✗ Scanner connection failed: " + ret);
                    errorMessage = (errorMessage != null ? errorMessage + "\n" : "") +
                                  "Scanner connection failed: " + ret;
                }
            } catch (Exception e) {
                Log.e(TAG, "║ ✗ Error with scanner", e);
                errorMessage = (errorMessage != null ? errorMessage + "\n" : "") +
                              "Scanner error: " + e.getMessage();
            } finally {
                if (sdk != null) {
                    try {
                        sdk.UF_CloseCommPort();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing scanner port", e);
                    }
                }
            }

            // Step 3: Set resetDatabase flag back to false in config
            try {
                Log.d(TAG, "║ Setting resetDatabase flag back to false...");
                ConfigManager.getConfig().getTestingFlags().setResetDatabase(false);
                ConfigManager.saveConfig();
                Log.d(TAG, "║ ✓ Config flag reset to false");
            } catch (Exception e) {
                Log.e(TAG, "║ ✗ Failed to save config", e);
            }

            // Step 4: Show results and navigate
            final boolean finalDatabaseReset = databaseReset;
            final boolean finalScannerReset = scannerReset;
            final String finalErrorMessage = errorMessage;

            mainHandler.post(() -> {
                StringBuilder message = new StringBuilder();

                if (finalDatabaseReset && finalScannerReset) {
                    Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.d(TAG, "║ FULL RESET SUCCESSFUL");
                    Log.d(TAG, "║ ✓ Database reset");
                    Log.d(TAG, "║ ✓ Scanner memory cleared");
                    Log.d(TAG, "║ ✓ Sample personnel added");
                    Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                    message.append("✓ Full reset successful!\n\n");
                    message.append("✓ Database reset\n");
                    message.append("✓ Scanner memory cleared\n");
                    message.append("✓ Sample personnel added");
                    Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                } else if (finalDatabaseReset) {
                    Log.w(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.w(TAG, "║ PARTIAL RESET");
                    Log.w(TAG, "║ ✓ Database reset");
                    Log.w(TAG, "║ ✗ Scanner reset failed");
                    Log.w(TAG, "║ Error: " + finalErrorMessage);
                    Log.w(TAG, "╚════════════════════════════════════════════════════════════");
                    message.append("⚠ Partial reset\n\n");
                    message.append("✓ Database reset\n");
                    message.append("✗ Scanner reset failed\n\n");
                    message.append("Error: ").append(finalErrorMessage);
                    Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                } else {
                    Log.e(TAG, "╔════════════════════════════════════════════════════════════");
                    Log.e(TAG, "║ RESET FAILED");
                    Log.e(TAG, "║ Error: " + finalErrorMessage);
                    Log.e(TAG, "╚════════════════════════════════════════════════════════════");
                    message.append("✗ Reset failed\n\n");
                    message.append(finalErrorMessage);
                    Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                }

                // Navigate to LoginActivity (GEMS Original - no local enrollment)
                android.os.Handler delayHandler = new android.os.Handler();
                delayHandler.postDelayed(() -> {
                    Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                }, 2000); // 2 second delay to show toast
            });

            executor.shutdown();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}