package com.eventhub.framework.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an API test class: tagged {@code api}, with authenticated clients injected and cleanup handled. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("api")
@ExtendWith(EventHubExtension.class)
public @interface ApiTest {
}
