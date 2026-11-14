package com.eventhub.framework.api;

import com.eventhub.framework.auth.BearerTokenFilter;
import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.auth.TokenProvider;
import com.eventhub.framework.config.Config;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Optional;

/**
 * Builds REST Assured request specifications. This is where authorization is attached:
 * an authenticated spec carries a {@link BearerTokenFilter}, an anonymous one doesn't.
 * Tests and API clients never set headers or tokens themselves.
 */
public final class ApiSpecFactory {

    private ApiSpecFactory() {
    }

    public static RequestSpecification anonymous() {
        return base().build();
    }

    public static RequestSpecification authenticatedAs(TestUser user) {
        return base()
                .addFilter(new BearerTokenFilter(user, TokenProvider.instance()))
                .build();
    }

    public static RequestSpecification forUser(Optional<TestUser> user) {
        return user.map(ApiSpecFactory::authenticatedAs).orElseGet(ApiSpecFactory::anonymous);
    }

    private static RequestSpecBuilder base() {
        Config cfg = Config.get();
        RestAssuredConfig raConfig = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeader("Authorization"))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", cfg.getInt("api.connectTimeoutMs"))
                        .setParam("http.socket.timeout", cfg.getInt("api.readTimeoutMs")));

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(cfg.require("api.baseUrl"))
                .setBasePath(cfg.require("api.basePath"))
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(raConfig)
                .addFilter(new ExchangeRecorder());

        if (cfg.getBoolean("api.logging")) {
            builder.addFilter(new RequestLoggingFilter()).addFilter(new ResponseLoggingFilter());
        }
        return builder;
    }
}
