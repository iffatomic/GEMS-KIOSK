/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

/**
 * DTO for individual key switch state change events
 * Matches PAC_API KeySwitchEventDto structure
 */
public class KeySwitchEventDto {

    private int doorId;
    private String doorName;           // Use door name instead of door ID
    private int keySwitchNumber;       // 1, 2, or 3
    private boolean isOn;              // true = turned/on, false = locked/off
    private String timestamp;          // ISO 8601
    private KeySwitchStatesDto allKeySwitches;

    public KeySwitchEventDto() {
    }

    // Getters and Setters

    public int getDoorId() {
        return doorId;
    }

    public void setDoorId(int doorId) {
        this.doorId = doorId;
    }

    public String getDoorName() {
        return doorName;
    }

    public void setDoorName(String doorName) {
        this.doorName = doorName;
    }

    public int getKeySwitchNumber() {
        return keySwitchNumber;
    }

    public void setKeySwitchNumber(int keySwitchNumber) {
        this.keySwitchNumber = keySwitchNumber;
    }

    public boolean isOn() {
        return isOn;
    }

    public void setOn(boolean on) {
        isOn = on;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public KeySwitchStatesDto getAllKeySwitches() {
        return allKeySwitches;
    }

    public void setAllKeySwitches(KeySwitchStatesDto allKeySwitches) {
        this.allKeySwitches = allKeySwitches;
    }

    @Override
    public String toString() {
        return "KeySwitchEvent{" +
                "doorName='" + doorName + '\'' +
                ", switchNumber=" + keySwitchNumber +
                ", state=" + (isOn ? "ON" : "OFF") +
                ", timestamp='" + timestamp + '\'' +
                ", allSwitches=" + allKeySwitches +
                '}';
    }
}
