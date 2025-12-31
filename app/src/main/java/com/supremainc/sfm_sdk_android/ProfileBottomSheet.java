package com.supremainc.sfm_sdk_android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ProfileBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ProfileBottomSheet";

    private TextView nameText, staffIdText, icNumberText, departmentText, roleText;
    private DatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_bottom_sheet_profile, container, false);

        // Initialize views
        nameText = view.findViewById(R.id.profileName);
        staffIdText = view.findViewById(R.id.profileStaffId);
        icNumberText = view.findViewById(R.id.profileIcNumber);
        departmentText = view.findViewById(R.id.profileDepartment);
        roleText = view.findViewById(R.id.profileRole);
        Button btnClose = view.findViewById(R.id.btnCloseProfile);

        // Initialize database
        dbHelper = new DatabaseHelper(requireContext());

        // Load user profile
        loadUserProfile();

        // Close button
        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    private void loadUserProfile() {
        try {
            // Get logged-in user's staff ID from SharedPreferences
            SharedPreferences loginPrefs = requireContext().getSharedPreferences("login_pref", 0);
            String staffId = loginPrefs.getString("logged_in_user", "");

            if (staffId.isEmpty()) {
                Log.e(TAG, "No logged-in user found");
                showError();
                return;
            }

            Log.d(TAG, "Loading profile for staff ID: " + staffId);

            // Get user from database
            DatabaseHelper.User user = dbHelper.getUserByStaffId(staffId);

            if (user != null) {
                // Display user information
                nameText.setText(user.getName());
                staffIdText.setText("Staff ID: " + user.getStaffId());
                icNumberText.setText("IC Number: " + user.getIcNumber());
                departmentText.setText("Department: " + user.getDepartment());

                // Show role
                String role = user.isAdmin() ? "Supervisor" : "Staff";
                roleText.setText("Role: " + role);

                Log.d(TAG, "Profile loaded successfully for: " + user.getName());
            } else {
                Log.e(TAG, "User not found in database for staff ID: " + staffId);
                showError();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading profile", e);
            showError();
        }
    }

    private void showError() {
        nameText.setText("Error Loading Profile");
        staffIdText.setText("");
        icNumberText.setText("");
        departmentText.setText("");
        roleText.setText("Please contact administrator");
    }

    @Override
    public void onStart() {
        super.onStart();

        try {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                View bottomSheet = dialog.findViewById(
                        getResources().getIdentifier("design_bottom_sheet", "id",
                                requireContext().getPackageName())
                );
                if (bottomSheet != null) {
                    BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting bottom sheet behavior", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}