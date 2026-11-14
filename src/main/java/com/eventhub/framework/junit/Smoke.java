package com.eventhub.framework.junit;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The few tests that must pass for a build to be worth looking at. Run with {@code -Dgroups=smoke}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("smoke")
public @interface Smoke {
}
