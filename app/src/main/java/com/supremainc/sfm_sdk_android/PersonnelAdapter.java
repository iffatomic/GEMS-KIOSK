/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying list of pre-registered personnel (admins/custodians)
 * Used in the person selection dialog during fingerprint enrollment
 */
public class PersonnelAdapter extends RecyclerView.Adapter<PersonnelAdapter.PersonnelViewHolder> {

    private List<DatabaseHelper.RegisteredPersonnel> personnelList;
    private List<DatabaseHelper.RegisteredPersonnel> personnelListFull;  // Full list for filtering
    private OnPersonnelClickListener listener;
    private String role;  // "ADMIN" or "CUSTODIAN"

    public interface OnPersonnelClickListener {
        void onPersonnelClick(DatabaseHelper.RegisteredPersonnel personnel);
    }

    public PersonnelAdapter(List<DatabaseHelper.RegisteredPersonnel> personnelList, String role, OnPersonnelClickListener listener) {
        this.personnelList = personnelList;
        this.personnelListFull = new ArrayList<>(personnelList);  // Keep a copy of the full list
        this.role = role;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PersonnelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_personnel, parent, false);
        return new PersonnelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonnelViewHolder holder, int position) {
        DatabaseHelper.RegisteredPersonnel personnel = personnelList.get(position);
        holder.bind(personnel);
    }

    @Override
    public int getItemCount() {
        return personnelList != null ? personnelList.size() : 0;
    }

    public void updateList(List<DatabaseHelper.RegisteredPersonnel> newList) {
        this.personnelList = newList;
        this.personnelListFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    /**
     * Filter the personnel list by name
     * @param query Search query (case-insensitive)
     */
    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            // If search is empty, show full list
            personnelList = new ArrayList<>(personnelListFull);
        } else {
            // Filter by name (case-insensitive)
            List<DatabaseHelper.RegisteredPersonnel> filteredList = new ArrayList<>();
            String lowerCaseQuery = query.toLowerCase().trim();

            for (DatabaseHelper.RegisteredPersonnel personnel : personnelListFull) {
                if (personnel.getName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(personnel);
                }
            }

            personnelList = filteredList;
        }
        notifyDataSetChanged();
    }

    class PersonnelViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;
        private ImageView ivPersonIcon;
        private TextView tvPersonName;
        private TextView tvEmployeeId;

        PersonnelViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            ivPersonIcon = itemView.findViewById(R.id.ivPersonIcon);
            tvPersonName = itemView.findViewById(R.id.tvPersonName);
            tvEmployeeId = itemView.findViewById(R.id.tvEmployeeId);
        }

        void bind(DatabaseHelper.RegisteredPersonnel personnel) {
            tvPersonName.setText(personnel.getName());
            tvEmployeeId.setText(personnel.getEmployeeId());

            // Set card stroke color based on role
            if ("ADMIN".equals(role)) {
                cardView.setStrokeColor(itemView.getContext().getResources().getColor(R.color.colorPrimary));
            } else {
                cardView.setStrokeColor(itemView.getContext().getResources().getColor(R.color.colorAccent));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPersonnelClick(personnel);
                }
            });
        }
    }
}
