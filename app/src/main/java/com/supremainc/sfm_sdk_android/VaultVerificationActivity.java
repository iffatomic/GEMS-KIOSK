/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;


import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class VaultVerificationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault_activation_verification);

        // Initialize Views
        ImageButton backButton = findViewById(R.id.back_button);
        LinearLayout userBox1 = findViewById(R.id.UserBox1);
        LinearLayout userBox2 = findViewById(R.id.UserBox2);
        LinearLayout userBox3 = findViewById(R.id.UserBox3);
        TextView awaitingText = findViewById(R.id.AwaitingConfirmation);
        ProgressBar verificationSpinner = findViewById(R.id.VerificationLoadingSpinner);

        // Back Button functionality
        backButton.setOnClickListener(v -> finish());  // Finish the activity when back button is pressed

        // Set initial state: make user boxes and spinner invisible and translated up by 100 pixels
        userBox1.setAlpha(0f);
        userBox1.setTranslationY(-100f);
        userBox2.setAlpha(0f);
        userBox2.setTranslationY(-100f);
        userBox3.setAlpha(0f);
        userBox3.setTranslationY(-100f);
        awaitingText.setVisibility(View.VISIBLE);  // Show awaiting text initially

        // Start animation: Fade in and move up
        int duration = 800;  // 500 ms animation duration
        int delay = 300;     // 300 ms delay between animations

        // Animate the user boxes
        userBox1.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(0)
                .start();

        userBox2.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(delay)
                .start();

        userBox3.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(delay * 2)
                .start();

        // Simulate the vault activation process
    }


}