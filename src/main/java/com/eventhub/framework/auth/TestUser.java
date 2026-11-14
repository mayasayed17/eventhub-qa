package com.eventhub.framework.auth;

/** An account the suite can act as. {@code key} is the logical name used in {@link com.eventhub.framework.junit.AsUser}. */
public record TestUser(String key, String email, String password) {

    @Override
    public String toString() {
        // never print the password
        return "TestUser[" + key + ", " + email + "]";
    }
}
