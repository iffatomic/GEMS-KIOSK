/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Manages external configuration file (config.json)
 * Similar to appsettings.json in .NET applications
 *
 * Config file location: /sdcard/Android/data/{package}/files/config.json
 *
 * Usage:
 * 1. Call ConfigManager.initialize(context) on app startup
 * 2. Access config via ConfigManager.getConfig()
 * 3. Edit config.json externally via file manager or ADB
 * 4. Call ConfigManager.reload(context) to pick up changes
 */
public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String ASSETS_DEFAULT_CONFIG = "default_config.json";

    private static AppConfig config;
    private static Gson gson;
    private static File configFile;

    // Prevent instantiation
    private ConfigManager() {}

    /**
     * Initialize the ConfigManager. Call this in Application.onCreate() or MainActivity.onCreate()
     * @param context Android context
     * @return true if config loaded successfully
     */
    public static synchronized boolean initialize(Context context) {
        gson = new GsonBuilder().setPrettyPrinting().create();

        // Get external files directory
        File externalFilesDir = context.getExternalFilesDir(null);

        // Ensure directory exists
        if (externalFilesDir != null && !externalFilesDir.exists()) {
            boolean created = externalFilesDir.mkdirs();
            Log.i(TAG, "Created external files directory: " + created);
        }

        configFile = new File(externalFilesDir, CONFIG_FILE_NAME);

        Log.i(TAG, "Config file path: " + configFile.getAbsolutePath());

        // Check if config file exists
        if (!configFile.exists()) {
            Log.i(TAG, "Config file not found, creating default...");
            createDefaultConfig(context);
        }

        // Load config from file
        boolean success = loadConfig();

        // Sync to ApiConstants so all API calls use the JSON config
        syncToApiConstants(context);

        return success;
    }

    /**
     * Get the current configuration
     * @return AppConfig instance (never null after initialization)
     */
    public static AppConfig getConfig() {
        if (config == null) {
            Log.w(TAG, "Config not initialized! Returning default config.");
            config = new AppConfig();
        }
        return config;
    }

    /**
     * Reload configuration from file (call after external edits)
     * @param context Android context
     * @return true if reload successful
     */
    public static synchronized boolean reload(Context context) {
        if (configFile == null) {
            return initialize(context);
        }
        boolean success = loadConfig();

        // Sync to ApiConstants after reload
        syncToApiConstants(context);

        return success;
    }

    /**
     * Save current configuration to file
     * @return true if save successful
     */
    public static synchronized boolean saveConfig() {
        if (config == null || configFile == null) {
            Log.e(TAG, "Cannot save - config not initialized");
            return false;
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
            Log.i(TAG, "Config saved successfully");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving config: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the config file path for display/debugging
     * @return Absolute path to config.json
     */
    public static String getConfigFilePath() {
        return configFile != null ? configFile.getAbsolutePath() : "Not initialized";
    }

    /**
     * Check if config file exists
     * @return true if file exists
     */
    public static boolean configFileExists() {
        return configFile != null && configFile.exists();
    }

    /**
     * Reset config to defaults (recreates config.json)
     * @param context Android context
     * @return true if reset successful
     */
    public static synchronized boolean resetToDefaults(Context context) {
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
        return initialize(context);
    }

    /**
     * Force reload from assets (overwrites existing config file)
     * Use this when you've updated default_config.json in assets
     * @param context Android context
     * @return true if reload successful
     */
    public static synchronized boolean forceReloadFromAssets(Context context) {
        Log.i(TAG, "Force reloading config from assets...");

        // Set configFile path first
        gson = new GsonBuilder().setPrettyPrinting().create();
        configFile = new File(context.getExternalFilesDir(null), CONFIG_FILE_NAME);

        // Delete existing config file to force copy from assets
        if (configFile.exists()) {
            boolean deleted = configFile.delete();
            Log.i(TAG, "Deleted existing config file: " + deleted);
        } else {
            Log.i(TAG, "No existing config file to delete");
        }

        // Now load from assets
        boolean success = loadConfig();

        if (!success || config == null) {
            // If load failed, create from assets
            Log.w(TAG, "Load failed, creating from assets...");
            createDefaultConfig(context);
            success = loadConfig();
        }

        // Sync to ApiConstants
        syncToApiConstants(context);

        Log.i(TAG, "Force reload complete - Base URL: " + (config != null ? config.getBaseUrl() : "NULL"));
        return success;
    }

    // ==================== Private Methods ====================

    private static boolean loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            config = gson.fromJson(reader, AppConfig.class);

            if (config == null) {
                Log.w(TAG, "Config file empty or invalid, using defaults");
                config = new AppConfig();
                saveConfig();
            }

            Log.i(TAG, "Config loaded - BaseUrl: " + config.getBaseUrl());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading config: " + e.getMessage(), e);
            config = new AppConfig();
            return false;
        }
    }

    /**
     * Sync config values to ApiConstants (for backward compatibility)
     * This ensures ApiConstants always reflects the JSON config
     * @param context Android context
     */
    private static void syncToApiConstants(Context context) {
        if (config == null) {
            Log.w(TAG, "Cannot sync - config is null");
            return;
        }

        // Sync base URL
        ApiConstants.setBaseUrl(context, config.getBaseUrl());

        // Sync timeouts
        ApiConstants.setConnectTimeout(context, config.getConnectTimeoutMs());
        ApiConstants.setReadTimeout(context, config.getReadTimeoutMs());

        Log.i(TAG, "╔════════════════════════════════════════════════════════════");
        Log.i(TAG, "║ CONFIG SYNCED TO API CONSTANTS");
        Log.i(TAG, "╠════════════════════════════════════════════════════════════");
        Log.i(TAG, "║ Base URL: " + config.getBaseUrl());
        Log.i(TAG, "║ Connect Timeout: " + config.getConnectTimeoutMs() + "ms");
        Log.i(TAG, "║ Read Timeout: " + config.getReadTimeoutMs() + "ms");
        Log.i(TAG, "╚════════════════════════════════════════════════════════════");
    }

    private static void createDefaultConfig(Context context) {
        // Ensure parent directory exists
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            Log.i(TAG, "Created parent directory: " + created + " at " + parentDir.getAbsolutePath());
        }

        // Try to copy from assets first
        if (copyFromAssets(context)) {
            return;
        }

        // Otherwise create new default config
        config = new AppConfig();
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
            Log.i(TAG, "Default config created at: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error creating default config: " + e.getMessage(), e);
        }
    }

    private static boolean copyFromAssets(Context context) {
        try {
            // Ensure parent directory exists before writing
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                Log.i(TAG, "Created parent directory for assets copy: " + created);
            }

            InputStream is = context.getAssets().open(ASSETS_DEFAULT_CONFIG);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            // Write to external file
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(sb.toString());
            }

            Log.i(TAG, "Config copied from assets to: " + configFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.d(TAG, "No default config in assets, will create new one");
            return false;
        }
    }
}
