/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/**
 * JSON parsing utility using Gson
 * Provides centralized JSON serialization/deserialization
 */
public class JsonParser {

    private static final Gson gson;
    private static final Gson gsonPretty;

    static {
        // Configure Gson with custom settings
        gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .serializeNulls()
                // No Base64 adapter needed - using templateDataByteArraysString (Arrays.toString format)
                .create();

        // Pretty-printing version for debugging
        gsonPretty = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .serializeNulls()
                // No Base64 adapter needed - using templateDataByteArraysString (Arrays.toString format)
                .setPrettyPrinting()
                .create();
    }

    /**
     * Convert object to JSON string
     * @param object Object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        return gson.toJson(object);
    }

    /**
     * Convert object to pretty-printed JSON string (for debugging)
     * @param object Object to serialize
     * @return Formatted JSON string
     */
    public static String toJsonPretty(Object object) {
        if (object == null) {
            return null;
        }
        return gsonPretty.toJson(object);
    }

    /**
     * Parse JSON string to object
     * @param json JSON string
     * @param classOfT Class of the target object
     * @param <T> Type parameter
     * @return Deserialized object
     * @throws JsonSyntaxException if JSON is malformed
     */
    public static <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return gson.fromJson(json, classOfT);
    }

    /**
     * Parse JSON string to object with Type
     * Use this for generic types like List<T>, Map<K,V>, etc.
     * @param json JSON string
     * @param typeOfT Type of the target object (use TypeToken)
     * @param <T> Type parameter
     * @return Deserialized object
     * @throws JsonSyntaxException if JSON is malformed
     */
    public static <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return gson.fromJson(json, typeOfT);
    }

    /**
     * Get Gson instance for custom operations
     * @return Gson instance
     */
    public static Gson getGson() {
        return gson;
    }

    /**
     * Create TypeToken for generic types
     * Example: Type listType = JsonParser.getType(new TypeToken<List<String>>() {});
     * @param typeToken TypeToken instance
     * @param <T> Type parameter
     * @return Type object
     */
    public static <T> Type getType(TypeToken<T> typeToken) {
        return typeToken.getType();
    }

    // Prevent instantiation
    private JsonParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
