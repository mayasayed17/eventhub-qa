package com.eventhub.framework.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Which configured test user a test (class, method) or an injected client (parameter) acts as.
 * Without it, everything runs as {@code default}. The framework logs in; the test only names the persona.
 * <pre>
 * void otherUsersCannotCancel(BookingsClient mine, {@literal @}AsUser("secondary") BookingsClient theirs)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
public @interface AsUser {
    String value();
}
