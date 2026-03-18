/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for external config.json file
 * Similar to appsettings.json in .NET applications
 */
public class AppConfig {

    // API Settings
    private String baseUrl = "http://192.2.2.117:7000";
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 10000;

    // SignalR Settings
    private String signalRHubPath = "/eventHub";

    // Endpoints
    private Endpoints endpoints = new Endpoints();

    // Door Variable Mappings
    private Map<String, String> doorVariables = new HashMap<>();

    // SignalR Event Names
    private SignalREvents signalREvents = new SignalREvents();

    // Inactivity Timeout (minutes, min 3, max 5, default 5)
    private int inactivityTimeoutMinutes = 5;

    // Update check interval (minutes, min 1, default 10)
    private int updateCheckIntervalMinutes = 10;

    // Testing Flags
    private TestingFlags testingFlags = new TestingFlags();

    public AppConfig() {
        // Initialize default door variables
        doorVariables.put("DOOR_A", "MAIN.SOFT_LOCK_A");
        doorVariables.put("DOOR_B", "MAIN.SOFT_LOCK_B");
        doorVariables.put("DOOR_C", "MAIN.SOFT_LOCK_C");
        doorVariables.put("DOOR_D", "MAIN.SOFT_LOCK_D");
        doorVariables.put("DOOR_E", "MAIN.SOFT_LOCK_E");
        doorVariables.put("DOOR_F", "MAIN.SOFT_LOCK_F");
        doorVariables.put("DOOR_G", "MAIN.SOFT_LOCK_G");
    }

    // Nested class for endpoints
    public static class Endpoints {
        private String userRegistration = "/api/UserRegistration/register";
        private String checkUsername = "/api/UserRegistration/check-username/";
        private String checkEmployee = "/api/UserRegistration/check-employee-number/";
        private String manualOverride = "/api/ManualOverride/profiles";
        private String deactivateOverride = "/api/ManualOverride/deactivate/";
        private String allProfiles = "/api/ManualOverride/all-profiles";
        private String validProfile = "/api/ManualOverride/valid-profile";

        public String getUserRegistration() { return userRegistration; }
        public void setUserRegistration(String userRegistration) { this.userRegistration = userRegistration; }

        public String getCheckUsername() { return checkUsername; }
        public void setCheckUsername(String checkUsername) { this.checkUsername = checkUsername; }

        public String getCheckEmployee() { return checkEmployee; }
        public void setCheckEmployee(String checkEmployee) { this.checkEmployee = checkEmployee; }

        public String getManualOverride() { return manualOverride; }
        public void setManualOverride(String manualOverride) { this.manualOverride = manualOverride; }

        public String getDeactivateOverride() { return deactivateOverride; }
        public void setDeactivateOverride(String deactivateOverride) { this.deactivateOverride = deactivateOverride; }

        public String getAllProfiles() { return allProfiles; }
        public void setAllProfiles(String allProfiles) { this.allProfiles = allProfiles; }

        public String getValidProfile() { return validProfile; }
        public void setValidProfile(String validProfile) { this.validProfile = validProfile; }
    }

    // Nested class for SignalR events
    public static class SignalREvents {
        private String keySwitchChanged = "KeySwitchChanged";
        private String keySwitchAggregate = "KeySwitchAggregate";
        private String manualOverride = "ManualOverrideEvent";
        private String doorStateChanged = "DoorStateChanged";
        private String vaultIncident = "VaultIncidentOccurred";

        public String getKeySwitchChanged() { return keySwitchChanged; }
        public void setKeySwitchChanged(String keySwitchChanged) { this.keySwitchChanged = keySwitchChanged; }

        public String getKeySwitchAggregate() { return keySwitchAggregate; }
        public void setKeySwitchAggregate(String keySwitchAggregate) { this.keySwitchAggregate = keySwitchAggregate; }

        public String getManualOverride() { return manualOverride; }
        public void setManualOverride(String manualOverride) { this.manualOverride = manualOverride; }

        public String getDoorStateChanged() { return doorStateChanged; }
        public void setDoorStateChanged(String doorStateChanged) { this.doorStateChanged = doorStateChanged; }

        public String getVaultIncident() { return vaultIncident; }
        public void setVaultIncident(String vaultIncident) { this.vaultIncident = vaultIncident; }
    }

    // Nested class for testing flags
    public static class TestingFlags {
        private boolean forceFirstTimeSetup = false;
        private boolean disableAdminVerification = false;
        private boolean resetDatabase = false;

        public boolean isForceFirstTimeSetup() { return forceFirstTimeSetup; }
        public void setForceFirstTimeSetup(boolean forceFirstTimeSetup) { this.forceFirstTimeSetup = forceFirstTimeSetup; }

        public boolean isDisableAdminVerification() { return disableAdminVerification; }
        public void setDisableAdminVerification(boolean disableAdminVerification) { this.disableAdminVerification = disableAdminVerification; }

        public boolean isResetDatabase() { return resetDatabase; }
        public void setResetDatabase(boolean resetDatabase) { this.resetDatabase = resetDatabase; }
    }

    // Getters and Setters
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public String getSignalRHubPath() { return signalRHubPath; }
    public void setSignalRHubPath(String signalRHubPath) { this.signalRHubPath = signalRHubPath; }

    public Endpoints getEndpoints() { return endpoints; }
    public void setEndpoints(Endpoints endpoints) { this.endpoints = endpoints; }

    public Map<String, String> getDoorVariables() { return doorVariables; }
    public void setDoorVariables(Map<String, String> doorVariables) { this.doorVariables = doorVariables; }

    public SignalREvents getSignalREvents() { return signalREvents; }
    public void setSignalREvents(SignalREvents signalREvents) { this.signalREvents = signalREvents; }

    public TestingFlags getTestingFlags() { return testingFlags; }
    public void setTestingFlags(TestingFlags testingFlags) { this.testingFlags = testingFlags; }

    // Helper method to get full SignalR URL
    public int getInactivityTimeoutMinutes() { return inactivityTimeoutMinutes; }
    public void setInactivityTimeoutMinutes(int minutes) {
        this.inactivityTimeoutMinutes = Math.max(3, Math.min(5, minutes));
    }

    public int getUpdateCheckIntervalMinutes() { return updateCheckIntervalMinutes; }
    public void setUpdateCheckIntervalMinutes(int minutes) {
        this.updateCheckIntervalMinutes = Math.max(1, minutes);
    }

    public String getSignalRHubUrl() {
        return baseUrl + signalRHubPath;
    }

    // Helper method to get door variable by key
    public String getDoorVariable(String doorKey) {
        return doorVariables.getOrDefault(doorKey, "");
    }
}
