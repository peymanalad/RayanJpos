package org.example.client.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Objects;
import java.util.Optional;

/**
 * Utility to resolve configuration values from the process environment,
 * system properties, or the optional .env file.
 */
public final class Environment {
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();

    private Environment() {
    }

    public static Optional<String> get(String key) {
        Objects.requireNonNull(key, "key");
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null) {
            value = DOTENV.get(key);
        }
        return Optional.ofNullable(value);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return get(key).map(value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        return get(key).map(value -> {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }
}