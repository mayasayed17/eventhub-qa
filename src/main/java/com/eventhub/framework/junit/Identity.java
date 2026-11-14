package com.eventhub.framework.junit;

import com.eventhub.framework.auth.TestUsers;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Works out which user applies: parameter, then method, then class, then {@code default}.
 * {@code Optional.empty()} means anonymous.
 */
final class Identity {

    private Identity() {
    }

    static Optional<String> forParameter(ParameterContext parameter, ExtensionContext context) {
        if (parameter.isAnnotated(Anonymous.class)) {
            return Optional.empty();
        }
        Optional<AsUser> onParam = parameter.findAnnotation(AsUser.class);
        return onParam.isPresent() ? Optional.of(onParam.get().value()) : forTest(context);
    }

    static Optional<String> forTest(ExtensionContext context) {
        Optional<Method> method = context.getTestMethod();
        if (method.isPresent() && declaresIdentity(method.get())) {
            return declared(method.get());
        }
        Optional<Class<?>> type = context.getTestClass();
        if (type.isPresent() && declaresIdentity(type.get())) {
            return declared(type.get());
        }
        return Optional.of(TestUsers.DEFAULT);
    }

    private static boolean declaresIdentity(AnnotatedElement element) {
        return AnnotationSupport.isAnnotated(element, Anonymous.class) || AnnotationSupport.isAnnotated(element, AsUser.class);
    }

    private static Optional<String> declared(AnnotatedElement element) {
        if (AnnotationSupport.isAnnotated(element, Anonymous.class)) {
            return Optional.empty();
        }
        return AnnotationSupport.findAnnotation(element, AsUser.class).map(AsUser::value);
    }
}
