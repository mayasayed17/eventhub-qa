package com.eventhub.framework.api;

import io.restassured.response.Response;
import org.assertj.core.api.Assertions;

import java.util.Arrays;

/** Status assertions whose failure message always includes the response body. */
public final class ApiAssert {

    private ApiAssert() {
    }

    public static Response status(Response response, int expected, String action) {
        Assertions.assertThat(response.statusCode())
                .as("HTTP status of '%s'. Body: %s", action, Json.abbreviate(response.asString()))
                .isEqualTo(expected);
        return response;
    }

    /** For behaviour the story leaves open (e.g. 400 vs 409); the README lists these as questions for the PO. */
    public static Response statusIn(Response response, String action, Integer... allowed) {
        Assertions.assertThat(response.statusCode())
                .as("HTTP status of '%s' (allowed %s). Body: %s", action, Arrays.toString(allowed),
                        Json.abbreviate(response.asString()))
                .isIn((Object[]) allowed);
        return response;
    }
}
