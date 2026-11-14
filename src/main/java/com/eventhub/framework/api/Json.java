package com.eventhub.framework.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.UncheckedIOException;

/** One shared, lenient Jackson mapper: unknown fields are ignored so additive API changes don't break tests. */
public final class Json {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot serialise " + value, e);
        }
    }

    public static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Response is not JSON: " + abbreviate(json), e);
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot map JSON to " + type.getSimpleName() + ": " + abbreviate(node.toString()), e);
        }
    }

    public static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 1000 ? s : s.substring(0, 1000) + "...(truncated)";
    }
}
