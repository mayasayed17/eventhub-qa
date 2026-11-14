package com.eventhub.framework.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * EventHub wraps payloads in a {@code data} envelope. This class is the single place that knows how to
 * unwrap it, so if the envelope changes, only this class changes.
 */
public final class Envelope {

    private Envelope() {
    }

    /** Reads a dotted path such as {@code data.booking.id}; returns a missing node if any segment is absent. */
    public static JsonNode at(JsonNode root, String dottedPath) {
        JsonNode node = root;
        for (String segment : dottedPath.split("\\.")) {
            node = node.path(segment);
        }
        return node;
    }

    /** First candidate path that holds an object with an {@code id} field, mapped to {@code type}. */
    public static <T> T object(Response response, Class<T> type, String... candidatePaths) {
        return findObject(response, type, candidatePaths).orElseThrow(() -> new IllegalStateException(
                "No " + type.getSimpleName() + " object found at " + String.join(" | ", candidatePaths)
                        + " in response (" + response.statusCode() + "): " + Json.abbreviate(response.asString())));
    }

    public static <T> Optional<T> findObject(Response response, Class<T> type, String... candidatePaths) {
        JsonNode root = Json.read(response.asString());
        for (String path : candidatePaths) {
            JsonNode node = at(root, path);
            if (node.isObject() && node.has("id")) {
                return Optional.of(Json.convert(node, type));
            }
        }
        return Optional.empty();
    }

    /** First candidate path that holds an array, each element mapped to {@code type}. */
    public static <T> List<T> list(Response response, Class<T> type, String... candidatePaths) {
        JsonNode root = Json.read(response.asString());
        for (String path : candidatePaths) {
            JsonNode node = at(root, path);
            if (node.isArray()) {
                List<T> items = new ArrayList<>();
                node.forEach(element -> items.add(Json.convert(element, type)));
                return items;
            }
        }
        throw new IllegalStateException("No array found at " + String.join(" | ", candidatePaths)
                + " in response (" + response.statusCode() + "): " + Json.abbreviate(response.asString()));
    }

    public static Optional<String> text(Response response, List<String> candidatePaths) {
        JsonNode root = Json.read(response.asString());
        for (String path : candidatePaths) {
            JsonNode node = at(root, path);
            if (node.isTextual() && !node.asText().isBlank()) {
                return Optional.of(node.asText());
            }
        }
        return Optional.empty();
    }
}
