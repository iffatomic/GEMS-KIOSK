/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

/**
 * DTO for aggregate key switch events (all on/off)
 * Matches PAC_API KeySwitchAggregateEventDto structure
 */
public class KeySwitchAggregateEventDto {

    private int doorId;
    private String doorName;           // Use door name instead of door ID
    private String eventType;          // "AllKeysOn" or "AllKeysOff"
    private boolean allKeysState;      // true = all on, false = all off
    private String timestamp;
    private KeySwitchStatesDto allKeySwitches;

    public KeySwitchAggregateEventDto() {
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public boolean isAllKeysState() {
        return allKeysState;
    }

    public void setAllKeysState(boolean allKeysState) {
        this.allKeysState = allKeysState;
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

    /**
     * Check if event type is AllKeysOn
     */
    public boolean isAllKeysOn() {
        return "AllKeysOn".equals(eventType);
    }

    /**
     * Check if event type is AllKeysOff
     */
    public boolean isAllKeysOff() {
        return "AllKeysOff".equals(eventType);
    }

    @Override
    public String toString() {
        return "KeySwitchAggregateEvent{" +
                "doorName='" + doorName + '\'' +
                ", eventType='" + eventType + '\'' +
                ", allKeysState=" + allKeysState +
                ", timestamp='" + timestamp + '\'' +
                ", switches=" + allKeySwitches +
                '}';
    }
}
