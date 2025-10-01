package org.example.server.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.example.server.logging.ApplicationLogger;
import org.example.server.logging.ApplicationLoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Loads application configuration from the environment and an optional .env file.
 */
public final class EnvironmentLoader {
    private static final ApplicationLogger LOGGER = ApplicationLoggerFactory.getLogger(EnvironmentLoader.class);
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();

    private EnvironmentLoader() {
    }

    /**
     * Loads variables from the .env file into the process space so that libraries that rely on
     * {@link System#getProperty(String)} can access them. Existing environment variables take precedence.
     */
    public static void load() {
        int applied = 0;
        for (DotenvEntry entry : DOTENV.entries()) {
            if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
                applied++;
            }
        }
        if (applied > 0) {
            LOGGER.info("Loaded {} environment variables from .env file", applied);
        } else {
            LOGGER.info("No additional environment variables loaded from .env file");
        }
    }

    /**
     * Retrieves a configuration value from the operating system environment, system properties, or .env file.
     *
     * @param key configuration key
     * @return optional value
     */
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

    /**
     * Retrieves a configuration value or a fallback when not defined.
     *
     * @param key           configuration key
     * @param defaultValue  fallback value
     * @return resolved configuration value
     */
    public static String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Retrieves a required configuration value.
     *
     * @param key configuration key
     * @return resolved value
     * @throws IllegalStateException when not present
     */
    public static String getRequired(String key) {
        return get(key).orElseThrow(() ->
                new IllegalStateException("Missing required configuration value: " + key));
    }

    public static int getInt(String key, int defaultValue) {
        return get(key).map(value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                LOGGER.warn("Configuration value '{}' for key '{}' is not a valid integer. Using default {}.",
                        value, key, defaultValue);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }
}