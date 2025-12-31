/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.supremainc.sfm_sdk_android.R;
import com.supremainc.sfm_sdk_android.data.model.response.UserListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying enrolled users with delete option
 * Supports filtering by role and search query
 */
public class DeleteUserAdapter extends RecyclerView.Adapter<DeleteUserAdapter.UserViewHolder> {

    private List<UserListItem> allUsers;           // Original unfiltered list
    private List<UserListItem> filteredUsers;      // Currently displayed list
    private OnDeleteClickListener listener;

    public interface OnDeleteClickListener {
        void onDeleteClick(UserListItem user);
    }

    public DeleteUserAdapter(List<UserListItem> userList, OnDeleteClickListener listener) {
        this.allUsers = new ArrayList<>(userList);
        this.filteredUsers = new ArrayList<>(userList);
        this.listener = listener;
    }

    /**
     * Update the full user list and reset filters
     */
    public void updateUserList(List<UserListItem> newList) {
        this.allUsers = new ArrayList<>(newList);
        this.filteredUsers = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    /**
     * Filter users by role and search query
     * @param role "ALL", "ADMIN", or "CUSTODIAN"
     * @param searchQuery Search text (searches name and employee ID)
     */
    public void filter(String role, String searchQuery) {
        filteredUsers.clear();

        String query = searchQuery.toLowerCase().trim();

        for (UserListItem user : allUsers) {
            // Check role filter
            boolean roleMatch = false;
            if ("ALL".equals(role)) {
                roleMatch = true;
            } else if ("ADMIN".equals(role) && "ADMIN".equalsIgnoreCase(user.getRole())) {
                roleMatch = true;
            } else if ("CUSTODIAN".equals(role) && "CUSTODIAN".equalsIgnoreCase(user.getRole())) {
                roleMatch = true;
            }

            // Check search query (name or employee ID)
            boolean searchMatch = false;
            if (query.isEmpty()) {
                searchMatch = true;
            } else {
                String name = user.getName() != null ? user.getName().toLowerCase() : "";
                String employeeId = user.getEmployeeNumber() != null ? user.getEmployeeNumber().toLowerCase() : "";

                if (name.contains(query) || employeeId.contains(query)) {
                    searchMatch = true;
                }
            }

            // Add if both filters match
            if (roleMatch && searchMatch) {
                filteredUsers.add(user);
            }
        }

        notifyDataSetChanged();
    }

    /**
     * Get count of users by role
     */
    public int getCountByRole(String role) {
        int count = 0;
        for (UserListItem user : allUsers) {
            if ("ALL".equals(role)) {
                count++;
            } else if ("ADMIN".equals(role) && "ADMIN".equalsIgnoreCase(user.getRole())) {
                count++;
            } else if ("CUSTODIAN".equals(role) && "CUSTODIAN".equalsIgnoreCase(user.getRole())) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delete_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserListItem user = filteredUsers.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return filteredUsers != null ? filteredUsers.size() : 0;
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private TextView tvName;
        private TextView tvEmployeeId;
        private TextView tvRole;
        private TextView tvFingerprintCount;
        private Button btnDelete;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmployeeId = itemView.findViewById(R.id.tvEmployeeId);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvFingerprintCount = itemView.findViewById(R.id.tvFingerprintCount);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(UserListItem user) {
            tvName.setText(user.getName());
            tvEmployeeId.setText("Employee ID: " + user.getEmployeeNumber());

            // Set role with color coding
            String role = user.getRole() != null ? user.getRole() : "USER";
            tvRole.setText(role);
            if ("ADMIN".equalsIgnoreCase(role)) {
                tvRole.setBackgroundColor(Color.parseColor("#2196F3")); // Blue for admin
            } else {
                tvRole.setBackgroundColor(Color.parseColor("#FF9800")); // Orange for custodian
            }

            // Show fingerprint count (default to 2 if not set)
            int fingerprintCount = user.getFingerprintCount() > 0 ? user.getFingerprintCount() : 2;
            tvFingerprintCount.setText(fingerprintCount + " fingerprint" + (fingerprintCount > 1 ? "s" : ""));

            // Delete button click
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(user);
                }
            });
        }
    }
}
