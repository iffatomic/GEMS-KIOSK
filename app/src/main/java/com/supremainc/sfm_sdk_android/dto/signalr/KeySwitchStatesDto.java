/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.dto.signalr;

/**
 * State of all 3 key switches for a door
 * Matches PAC_API KeySwitchStatesDto structure
 */
public class KeySwitchStatesDto {

    private boolean keySwitch1;
    private boolean keySwitch2;
    private boolean keySwitch3;

    public KeySwitchStatesDto() {
    }

    public KeySwitchStatesDto(boolean keySwitch1, boolean keySwitch2, boolean keySwitch3) {
        this.keySwitch1 = keySwitch1;
        this.keySwitch2 = keySwitch2;
        this.keySwitch3 = keySwitch3;
    }

    // Getters and Setters

    public boolean isKeySwitch1() {
        return keySwitch1;
    }

    public void setKeySwitch1(boolean keySwitch1) {
        this.keySwitch1 = keySwitch1;
    }

    public boolean isKeySwitch2() {
        return keySwitch2;
    }

    public void setKeySwitch2(boolean keySwitch2) {
        this.keySwitch2 = keySwitch2;
    }

    public boolean isKeySwitch3() {
        return keySwitch3;
    }

    public void setKeySwitch3(boolean keySwitch3) {
        this.keySwitch3 = keySwitch3;
    }

    /**
     * Check if all keyswitches are ON
     */
    public boolean isAllOn() {
        return keySwitch1 && keySwitch2 && keySwitch3;
    }

    /**
     * Check if all keyswitches are OFF
     */
    public boolean isAllOff() {
        return !keySwitch1 && !keySwitch2 && !keySwitch3;
    }

    /**
     * Get count of switches that are ON
     */
    public int getOnCount() {
        int count = 0;
        if (keySwitch1) count++;
        if (keySwitch2) count++;
        if (keySwitch3) count++;
        return count;
    }

    @Override
    public String toString() {
        return "KeySwitchStates{" +
                "switch1=" + (keySwitch1 ? "ON" : "OFF") +
                ", switch2=" + (keySwitch2 ? "ON" : "OFF") +
                ", switch3=" + (keySwitch3 ? "ON" : "OFF") +
                ", onCount=" + getOnCount() +
                '}';
    }
}
