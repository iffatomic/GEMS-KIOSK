/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

/**
 * Response DTO for vault incident log
 * Matches PAC_API VaultIncidentLogDto structure
 */
public class VaultIncidentLog {

    private String id;
    private String vaultName;
    private String location;
    private String occurredAt;      // ISO-8601 date string
    private String doorState;
    private String eventType;
    private String severity;
    private String createdAt;       // ISO-8601 date string
    private Boolean isSentToAws;
    private String sentToAwsAt;     // ISO-8601 date string

    public VaultIncidentLog() {
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDoorState() {
        return doorState;
    }

    public void setDoorState(String doorState) {
        this.doorState = doorState;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsSentToAws() {
        return isSentToAws;
    }

    public void setIsSentToAws(Boolean isSentToAws) {
        this.isSentToAws = isSentToAws;
    }

    public String getSentToAwsAt() {
        return sentToAwsAt;
    }

    public void setSentToAwsAt(String sentToAwsAt) {
        this.sentToAwsAt = sentToAwsAt;
    }

    @Override
    public String toString() {
        return "VaultIncidentLog{" +
                "id='" + id + '\'' +
                ", vaultName='" + vaultName + '\'' +
                ", location='" + location + '\'' +
                ", occurredAt='" + occurredAt + '\'' +
                ", doorState='" + doorState + '\'' +
                ", eventType='" + eventType + '\'' +
                ", severity='" + severity + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", isSentToAws=" + isSentToAws +
                ", sentToAwsAt='" + sentToAwsAt + '\'' +
                '}';
    }
}
