/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

/**
 * SignalR DTO for vault incident broadcast events
 * Matches PAC_API VaultIncidentBroadcastDto structure
 * Received via SignalR when vault security incidents occur
 */
public class VaultIncidentBroadcastDto {

    private String vaultName;
    private String location;
    private String occurredAt;      // ISO-8601 date string
    private String doorState;
    private String eventType;
    private String severity;

    public VaultIncidentBroadcastDto() {
    }

    // Getters and Setters

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

    @Override
    public String toString() {
        return "VaultIncidentBroadcastDto{" +
                "vaultName='" + vaultName + '\'' +
                ", location='" + location + '\'' +
                ", occurredAt='" + occurredAt + '\'' +
                ", doorState='" + doorState + '\'' +
                ", eventType='" + eventType + '\'' +
                ", severity='" + severity + '\'' +
                '}';
    }
}
