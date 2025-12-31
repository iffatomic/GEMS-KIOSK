/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.util.ApiConstants;
import com.supremainc.sfm_sdk_android.util.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced HTTP Client for communicating with PAC API
 * Supports both legacy methods and new generic typed methods
 */
public class PacApiClient {

    private static final String TAG = "PacApiClient";

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SimpleDateFormat isoDateFormat;

    public PacApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // ISO 8601 UTC date format
        this.isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.isoDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // Legacy constructor for backward compatibility
    public PacApiClient() {
        this.context = null;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.isoDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Get base URL from context or use default
     */
    private String getBaseUrl() {
        if (context != null) {
            return ApiConstants.getBaseUrl(context);
        }
        // Fallback URL (should not be used if context is provided)
        return "http://192.2.2.117:7000";
    }

    /**
     * Get connect timeout
     */
    private int getConnectTimeout() {
        if (context != null) {
            return ApiConstants.getConnectTimeout(context);
        }
        return 10000;
    }

    /**
     * Get read timeout
     */
    private int getReadTimeout() {
        if (context != null) {
            return ApiConstants.getReadTimeout(context);
        }
        return 10000;
    }

    // ========== NEW GENERIC METHODS ==========

    /**
     * Generic POST request with typed response
     * @param endpoint API endpoint (e.g., "/api/UserRegistration/register")
     * @param requestBody Request body object (will be serialized to JSON)
     * @param responseType Class of response type
     * @param callback Response callback
     * @param <T> Type of response
     */
    public <T> void post(String endpoint, Object requestBody, Class<T> responseType, ApiCallback<T> callback) {
        executor.execute(() -> {
            try {
                String url = getBaseUrl() + endpoint;
                String jsonBody = JsonParser.toJson(requestBody);

                Log.d(TAG, "POST " + endpoint);
                Log.d(TAG, "Request: " + jsonBody);

                String responseText = sendHttpRequest(url, ApiConstants.HTTP_POST, jsonBody);
                Log.d(TAG, "Response: " + responseText);

                T response = JsonParser.fromJson(responseText, responseType);
                mainHandler.post(() -> callback.onSuccess(response));

            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON parsing error", e);
                mainHandler.post(() -> callback.onError("Invalid response format: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Generic POST request with ApiResponse wrapper
     * @param endpoint API endpoint
     * @param requestBody Request body object
     * @param dataType Class of data type inside ApiResponse
     * @param callback Response callback
     * @param <T> Type of data inside ApiResponse
     */
    public <T> void postWithWrapper(String endpoint, Object requestBody, Class<T> dataType,
                                    ApiCallback<ApiResponse<T>> callback) {
        executor.execute(() -> {
            try {
                String url = getBaseUrl() + endpoint;
                String jsonBody = JsonParser.toJson(requestBody);

                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ HTTP POST REQUEST");
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ URL: " + url);
                Log.d(TAG, "║ Endpoint: " + endpoint);
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ REQUEST BODY (Pretty):");
                Log.d(TAG, JsonParser.toJsonPretty(requestBody));
                Log.d(TAG, "╠════════════════════════════════════════════════════════════");

                String responseText = sendHttpRequest(url, ApiConstants.HTTP_POST, jsonBody);

                Log.d(TAG, "║ RESPONSE BODY:");
                Log.d(TAG, responseText);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                // Parse ApiResponse<T>
                Type type = TypeToken.getParameterized(ApiResponse.class, dataType).getType();
                ApiResponse<T> response = JsonParser.fromJson(responseText, type);

                mainHandler.post(() -> callback.onSuccess(response));

            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON parsing error", e);
                mainHandler.post(() -> callback.onError("Invalid response format: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Generic GET request with query parameters
     * @param endpoint API endpoint
     * @param queryParams Query parameters (can be null)
     * @param responseType Type of response (use TypeToken for generic types)
     * @param callback Response callback
     * @param <T> Type of response
     */
    public <T> void get(String endpoint, Map<String, String> queryParams, Type responseType,
                       ApiCallback<T> callback) {
        executor.execute(() -> {
            try {
                String url = getBaseUrl() + endpoint;

                // Add query parameters
                if (queryParams != null && !queryParams.isEmpty()) {
                    StringBuilder queryString = new StringBuilder("?");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                        if (!first) queryString.append("&");
                        queryString.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                                  .append("=")
                                  .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                        first = false;
                    }
                    url += queryString.toString();
                }

                Log.d(TAG, "GET " + url);

                String responseText = sendHttpRequest(url, ApiConstants.HTTP_GET, null);
                Log.d(TAG, "Response: " + responseText);

                T response = JsonParser.fromJson(responseText, responseType);
                mainHandler.post(() -> callback.onSuccess(response));

            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON parsing error", e);
                mainHandler.post(() -> callback.onError("Invalid response format: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Generic DELETE request
     * @param endpoint API endpoint
     * @param responseType Type of response (use TypeToken for generic types)
     * @param callback Response callback
     * @param <T> Type of response
     */
    public <T> void delete(String endpoint, Type responseType, ApiCallback<T> callback) {
        executor.execute(() -> {
            try {
                String url = getBaseUrl() + endpoint;

                Log.d(TAG, "DELETE " + url);

                String responseText = sendHttpRequest(url, ApiConstants.HTTP_DELETE, null);
                Log.d(TAG, "Response: " + responseText);

                T response = JsonParser.fromJson(responseText, responseType);
                mainHandler.post(() -> callback.onSuccess(response));

            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON parsing error", e);
                mainHandler.post(() -> callback.onError("Invalid response format: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Send HTTP request (GET, POST, or DELETE)
     * @param urlString URL to send request to
     * @param method HTTP method (GET, POST, or DELETE)
     * @param jsonBody JSON body (null for GET/DELETE)
     * @return Response string
     * @throws IOException if network error occurs
     */
    private String sendHttpRequest(String urlString, String method, String jsonBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty(ApiConstants.HEADER_CONTENT_TYPE, ApiConstants.CONTENT_TYPE_JSON);
            connection.setRequestProperty(ApiConstants.HEADER_ACCEPT, ApiConstants.CONTENT_TYPE_JSON);
            connection.setConnectTimeout(getConnectTimeout());
            connection.setReadTimeout(getReadTimeout());

            if (ApiConstants.HTTP_POST.equals(method) && jsonBody != null && !jsonBody.isEmpty()) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Check for HTTP errors
            if (responseCode >= 400) {
                throw new IOException("HTTP " + responseCode + ": " + response.toString());
            }

            return response.toString();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ========== LEGACY METHODS (for backward compatibility) ==========

    /**
     * Activate manual override with specific start and end times
     * @param doorId Door ID (e.g., 3 for "Vault Door C")
     * @param startTimeMillis Start time in milliseconds (usually current time)
     * @param endTimeMillis End time in milliseconds (when override should expire)
     * @param callback Response callback
     */
    public void activateManualOverride(
            int doorId,
            long startTimeMillis,
            long endTimeMillis,
            OverrideCallback callback) {

        Log.i(TAG, String.format("Activating override | Door: %d | Start: %s | End: %s",
                doorId, new Date(startTimeMillis), new Date(endTimeMillis)));

        executor.execute(() -> {
            try {
                // Map DoorId to VariableName
                String variableName = getVariableNameForDoor(doorId);
                if (variableName == null) {
                    mainHandler.post(() -> callback.onError("Invalid door ID: " + doorId));
                    return;
                }

                // Format times as ISO 8601 UTC
                String startDateTime = isoDateFormat.format(new Date(startTimeMillis));
                String endDateTime = isoDateFormat.format(new Date(endTimeMillis));

                // Create request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("variableName", variableName);
                requestBody.put("startDateTime", startDateTime);
                requestBody.put("endDateTime", endDateTime);

                Log.d(TAG, "Request: " + requestBody.toString());

                // Send request
                String responseText = sendPostRequest(
                        getBaseUrl() + "/api/manualoverride/activate",
                        requestBody.toString()
                );

                Log.d(TAG, "Response: " + responseText);

                // Parse response
                JSONObject response = new JSONObject(responseText);
                boolean success = response.getBoolean("success");

                if (success) {
                    OverrideResponse result = new OverrideResponse();
                    result.success = true;
                    result.message = response.getString("message");
                    result.profileId = response.getString("profileId");
                    result.doorName = response.getString("doorName");
                    result.variableName = variableName;
                    result.startTimeMillis = startTimeMillis;
                    result.endTimeMillis = endTimeMillis;

                    if (response.has("startDateTime")) {
                        result.startDateTime = response.getString("startDateTime");
                    }
                    if (response.has("endDateTime")) {
                        result.endDateTime = response.getString("endDateTime");
                    }

                    Log.i(TAG, "✓ Override activated: " + result.profileId);
                    mainHandler.post(() -> callback.onSuccess(result));

                } else {
                    String errorMessage = response.getString("message");
                    Log.e(TAG, "✗ API Error: " + errorMessage);
                    mainHandler.post(() -> callback.onError(errorMessage));
                }

            } catch (JSONException e) {
                Log.e(TAG, "JSON parsing error", e);
                mainHandler.post(() -> callback.onError("Invalid response: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Deactivate manual override
     */
    public void deactivateManualOverride(String profileId, DeactivateCallback callback) {
        Log.i(TAG, "Deactivating override: " + profileId);

        executor.execute(() -> {
            try {
                String responseText = sendPostRequest(
                        getBaseUrl() + "/api/manualoverride/deactivate/" + profileId,
                        ""
                );

                JSONObject response = new JSONObject(responseText);
                boolean success = response.getBoolean("success");
                String message = response.getString("message");

                if (success) {
                    Log.i(TAG, "✓ Override deactivated");
                    mainHandler.post(() -> callback.onSuccess(message));
                } else {
                    Log.e(TAG, "✗ Deactivate failed: " + message);
                    mainHandler.post(() -> callback.onError(message));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error deactivating", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Map DoorId to PLC VariableName
     */
    private String getVariableNameForDoor(int doorId) {
        switch (doorId) {
            case 1: return ApiConstants.VARIABLE_DOOR_A;
            case 2: return ApiConstants.VARIABLE_DOOR_B;
            case 3: return ApiConstants.VARIABLE_DOOR_C;
            case 4: return ApiConstants.VARIABLE_DOOR_D;
            case 5: return ApiConstants.VARIABLE_DOOR_E;
            case 6: return ApiConstants.VARIABLE_DOOR_F;
            case 7: return ApiConstants.VARIABLE_DOOR_G;
            default: return null;
        }
    }

    /**
     * Send HTTP POST request (legacy method - uses new sendHttpRequest internally)
     */
    private String sendPostRequest(String urlString, String jsonBody) throws IOException {
        return sendHttpRequest(urlString, ApiConstants.HTTP_POST, jsonBody);
    }

    public static class OverrideResponse {
        public boolean success;
        public String message;
        public String profileId;
        public String doorName;
        public String variableName;
        public long startTimeMillis;
        public long endTimeMillis;
        public String startDateTime;
        public String endDateTime;

        @Override
        public String toString() {
            return "OverrideResponse{" +
                    "doorName='" + doorName + '\'' +
                    ", profileId='" + profileId + '\'' +
                    ", start=" + new Date(startTimeMillis) +
                    ", end=" + new Date(endTimeMillis) +
                    '}';
        }
    }

    public interface OverrideCallback {
        void onSuccess(OverrideResponse response);
        void onError(String error);
    }

    public interface DeactivateCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}