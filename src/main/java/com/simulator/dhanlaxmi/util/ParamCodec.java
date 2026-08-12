package com.simulator.dhanlaxmi.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the "key=value&key=value" plain-text format used for both the
 * checksum input and the AES payload. Order matters for checksum
 * verification, so this preserves whatever order it's given.
 */
public final class ParamCodec {

    private ParamCodec() {
    }

    /** Parses "A=1&B=2" into an ordered map, preserving the order received. */
    public static Map<String, String> parse(String data) {
        Map<String, String> params = new LinkedHashMap<>();
        if (data == null || data.isBlank()) {
            return params;
        }
        for (String pair : data.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > -1) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value);
            }
        }
        return params;
    }

    /** Builds "A=1&B=2" from an ordered map, in the order the map iterates. */
    public static String build(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
