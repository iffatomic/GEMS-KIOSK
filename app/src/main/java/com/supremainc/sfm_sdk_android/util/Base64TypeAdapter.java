/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.util;

import android.util.Base64;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Gson TypeAdapter for converting between Base64 strings and byte arrays
 *
 * ASP.NET Core serializes byte[] to Base64 string in JSON, but Gson by default
 * expects a JSON array of integers. This adapter handles the conversion.
 *
 * Usage:
 * GsonBuilder builder = new GsonBuilder();
 * builder.registerTypeAdapter(byte[].class, new Base64TypeAdapter());
 * Gson gson = builder.create();
 */
public class Base64TypeAdapter extends TypeAdapter<byte[]> {

    @Override
    public void write(JsonWriter out, byte[] value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // Encode byte[] to Base64 string when writing JSON
            out.value(Base64.encodeToString(value, Base64.NO_WRAP));
        }
    }

    @Override
    public byte[] read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        // Read as string and decode from Base64
        String base64String = in.nextString();
        if (base64String == null || base64String.isEmpty()) {
            return new byte[0];
        }

        try {
            // CRITICAL FIX: Use NO_WRAP to match encoding format and PAC_API output
            // Base64.DEFAULT expects newlines every 76 chars, but PAC_API sends continuous string
            return Base64.decode(base64String, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            // If decoding fails, return empty array
            return new byte[0];
        }
    }
}
