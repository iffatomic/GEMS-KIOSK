/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity to display full list of active manual overrides
 * Accessible from the dashboard via "View More" button
 */
public class OverrideListActivity extends AppCompatActivity {

    private static final String TAG = "OverrideListActivity";

    // UI Elements
    private ImageButton backButton;
    private TextView titleText;
    private TextView overrideCountText;
    private LinearLayout overrideListContainer;
    private LinearLayout noOverridesMessage;
    private ImageButton refreshButton;

    // Data
    private VaultOverrideManager vaultManager;

    // Auto-refresh
    private Handler updateHandler;
    private Runnable updateRunnable;

    // Date formatter
    private SimpleDateFormat dateFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_override_list);

        initializeViews();
        setupListeners();

        // Initialize vault manager
        vaultManager = VaultOverrideManager.getInstance(this);

        // Initialize date formatter
        dateFormatter = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        // Load override list
        refreshOverrideList();

        // Start auto-refresh (update every second for countdown)
        startAutoRefresh();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        titleText = findViewById(R.id.titleText);
        overrideCountText = findViewById(R.id.overrideCountText);
        overrideListContainer = findViewById(R.id.overrideListContainer);
        noOverridesMessage = findViewById(R.id.noOverridesMessage);
        refreshButton = findViewById(R.id.refreshButton);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        refreshButton.setOnClickListener(v -> refreshOverrideList());
    }

    /**
     * Refresh the list of active overrides
     */
    private void refreshOverrideList() {
        List<VaultOverrideManager.VaultOverride> activeOverrides = vaultManager.getActiveOverrides();

        // Update count
        int count = activeOverrides.size();
        overrideCountText.setText("Active Overrides: " + count);

        if (count == 0) {
            // Show "no overrides" message
            noOverridesMessage.setVisibility(View.VISIBLE);
            overrideListContainer.setVisibility(View.GONE);
        } else {
            // Hide message and show list
            noOverridesMessage.setVisibility(View.GONE);
            overrideListContainer.setVisibility(View.VISIBLE);

            // Clear existing cards
            overrideListContainer.removeAllViews();

            // Add card for each override
            for (VaultOverrideManager.VaultOverride override : activeOverrides) {
                addOverrideCard(override);
            }
        }
    }

    /**
     * Create and add an override card to the list
     */
    private void addOverrideCard(VaultOverrideManager.VaultOverride override) {
        // Create card
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(8);
        cardView.setRadius(12);
        cardView.setCardBackgroundColor(getResources().getColor(R.color.colorSurface));
        cardView.setContentPadding(24, 24, 24, 24);

        // Content layout
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Header row (vault name + status badge)
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Vault name
        TextView vaultNameText = new TextView(this);
        vaultNameText.setText(override.vaultName);
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
        statusBadge.setText(override.isExpired() ? "EXPIRED" : "ACTIVE");
        statusBadge.setTextSize(12);
        statusBadge.setTextColor(Color.WHITE);
        statusBadge.setBackgroundColor(override.isExpired() ?
                Color.parseColor("#FF5252") : Color.parseColor("#4CAF50"));
        statusBadge.setPadding(16, 8, 16, 8);
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
        supervisorParams.setMargins(0, 8, 0, 0);
        supervisorText.setLayoutParams(supervisorParams);
        contentLayout.addView(supervisorText);

        // Activation time
        TextView activationText = new TextView(this);
        activationText.setText("Activated: " + formatDateTime(override.startTimeMillis));
        activationText.setTextSize(14);
        activationText.setTextColor(getResources().getColor(R.color.textSecondary));
        LinearLayout.LayoutParams activationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        activationParams.setMargins(0, 4, 0, 0);
        activationText.setLayoutParams(activationParams);
        contentLayout.addView(activationText);

        // Expiration time
        TextView expirationText = new TextView(this);
        expirationText.setText("Expires: " + formatDateTime(override.endTimeMillis));
        expirationText.setTextSize(14);
        expirationText.setTextColor(getResources().getColor(R.color.textSecondary));
        LinearLayout.LayoutParams expirationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        expirationParams.setMargins(0, 4, 0, 8);
        expirationText.setLayoutParams(expirationParams);
        contentLayout.addView(expirationText);

        // Time remaining with countdown (use tag for updates)
        TextView timeRemainingText = new TextView(this);
        timeRemainingText.setText("Time Remaining: " + override.getRemainingTimeFormatted());
        timeRemainingText.setTextSize(16);
        timeRemainingText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
        timeRemainingText.setTypeface(null, android.graphics.Typeface.BOLD);
        timeRemainingText.setTag(override); // Store override reference for updates
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, 8, 0, 16);
        timeRemainingText.setLayoutParams(timeParams);
        contentLayout.addView(timeRemainingText);

        // Deactivate button
        Button deactivateButton = new Button(this);
        deactivateButton.setText("Deactivate Override");
        deactivateButton.setTextColor(Color.WHITE);
        deactivateButton.setBackgroundColor(Color.parseColor("#FF5252"));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        deactivateButton.setLayoutParams(buttonParams);
        deactivateButton.setOnClickListener(v -> showDeactivateConfirmation(override.vaultName));
        contentLayout.addView(deactivateButton);

        cardView.addView(contentLayout);
        overrideListContainer.addView(cardView);
    }

    /**
     * Format timestamp to readable date/time string
     */
    private String formatDateTime(long timeMillis) {
        return dateFormatter.format(new Date(timeMillis));
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
                    refreshOverrideList();
                    Toast.makeText(this, vaultName + " override deactivated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Start auto-refresh for countdown timers
     */
    private void startAutoRefresh() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCountdownTimers();
                updateHandler.postDelayed(this, 1000);
            }
        };
        updateHandler.post(updateRunnable);
    }

    /**
     * Update countdown timers for all cards
     */
    private void updateCountdownTimers() {
        for (int i = 0; i < overrideListContainer.getChildCount(); i++) {
            View cardView = overrideListContainer.getChildAt(i);
            if (cardView instanceof CardView) {
                LinearLayout contentLayout = (LinearLayout) ((CardView) cardView).getChildAt(0);

                // Find the time remaining TextView
                for (int j = 0; j < contentLayout.getChildCount(); j++) {
                    View child = contentLayout.getChildAt(j);
                    if (child instanceof TextView && child.getTag() instanceof VaultOverrideManager.VaultOverride) {
                        TextView timeText = (TextView) child;
                        VaultOverrideManager.VaultOverride override =
                                (VaultOverrideManager.VaultOverride) child.getTag();

                        if (override.isExpired()) {
                            // Override expired, refresh the list
                            refreshOverrideList();
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
        refreshOverrideList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}