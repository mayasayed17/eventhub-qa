package com.eventhub.framework.api;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the last few HTTP exchanges of the current test thread so a failing test can print them.
 * This gives readable failure output without logging every call of every passing test.
 */
public final class ExchangeRecorder implements Filter {

    private static final int MAX = 15;
    private static final ThreadLocal<Deque<String>> EXCHANGES = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        long start = System.nanoTime();
        Response response = ctx.next(request, responseSpec);
        long ms = (System.nanoTime() - start) / 1_000_000;
        Object body = request.getBody();
        String requestBody = body == null ? "" : " body=" + mask(Json.abbreviate(String.valueOf(body)));
        record(request.getMethod() + " " + request.getURI() + requestBody
                + "\n   -> " + response.statusCode() + " (" + ms + " ms) " + mask(Json.abbreviate(response.asString())));
        return response;
    }

    /** Secrets never reach logs: passwords and tokens are blanked in both directions. */
    static String mask(String text) {
        return SECRET_FIELDS.matcher(text).replaceAll("\"$1\":\"***\"");
    }

    private static final java.util.regex.Pattern SECRET_FIELDS = java.util.regex.Pattern.compile(
            "\"(password|token|accessToken|refreshToken)\"\\s*:\\s*\"[^\"]*\"",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static void record(String line) {
        Deque<String> deque = EXCHANGES.get();
        if (deque.size() == MAX) {
            deque.removeFirst();
        }
        deque.addLast(line);
    }

    public static List<String> drain() {
        List<String> copy = new ArrayList<>(EXCHANGES.get());
        EXCHANGES.get().clear();
        return copy;
    }

    public static void reset() {
        EXCHANGES.get().clear();
    }
}
