package com.eventhub.framework.auth;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Adds {@code Authorization: Bearer <token>} to every request made with an authenticated spec.
 * The token is fetched per request (from the cache), so an expiring token is renewed transparently.
 */
public final class BearerTokenFilter implements Filter {

    private final TestUser user;
    private final TokenProvider tokens;

    public BearerTokenFilter(TestUser user, TokenProvider tokens) {
        this.user = user;
        this.tokens = tokens;
    }

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification response,
                           FilterContext ctx) {
        request.replaceHeader("Authorization", "Bearer " + tokens.tokenFor(user).value());
        return ctx.next(request, response);
    }
}
