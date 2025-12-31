/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.data.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * Generic API response wrapper
 * Matches PAC_API Response<T> structure
 * Supports both "flag" and "success" field names from different endpoints
 */
public class ApiResponse<T> {

    @SerializedName(value = "flag", alternate = {"success"})
    private boolean flag;     // Success/failure indicator (maps to "flag" or "success" in JSON)

    @SerializedName("message")
    private String message;   // Response message

    @SerializedName("data")
    private T data;           // Actual data payload

    public ApiResponse() {
    }

    public ApiResponse(boolean flag, String message, T data) {
        this.flag = flag;
        this.message = message;
        this.data = data;
    }

    // Getters and Setters

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * Check if the response indicates success
     */
    public boolean isSuccess() {
        return flag;
    }

    /**
     * Check if the response has data
     */
    public boolean hasData() {
        return data != null;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "flag=" + flag +
                ", message='" + message + '\'' +
                ", hasData=" + hasData() +
                '}';
    }
}
