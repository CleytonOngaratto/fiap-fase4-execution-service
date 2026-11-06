package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TraceHeaders {

    public static final List<String> NAMES = List.of("newrelic", "traceparent", "tracestate");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> FLAT_MAP = new TypeReference<>() {
    };

    private TraceHeaders() {
    }

    public static Map<String, String> only(Map<String, String> headers) {
        Map<String, String> known = new TreeMap<>();
        if (headers == null) {
            return known;
        }
        for (String name : NAMES) {
            String value = headers.get(name);
            if (value != null && !value.isBlank()) {
                known.put(name, value);
            }
        }
        return known;
    }

    public static String toJson(Map<String, String> headers) {
        Map<String, String> known = only(headers);
        if (known.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(known);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return only(MAPPER.readValue(json, FLAT_MAP));
        } catch (JsonProcessingException | RuntimeException e) {
            return Map.of();
        }
    }
}
