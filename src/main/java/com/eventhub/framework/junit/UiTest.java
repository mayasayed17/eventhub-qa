package com.eventhub.framework.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a UI test class: tagged {@code ui}; each test gets a fresh, already-authenticated browser context,
 * page objects injected by type, API clients for fast setup, tracing on failure, and cleanup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("ui")
@ExtendWith({EventHubExtension.class, UiExtension.class})
public @interface UiTest {
}
