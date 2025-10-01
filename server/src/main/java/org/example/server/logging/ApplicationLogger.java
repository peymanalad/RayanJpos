package org.example.server.logging;

/**
 * Minimal logging abstraction that keeps the server running even when SLF4J is
 * not present on the runtime classpath. Implementations may delegate to SLF4J
 * when available or fall back to {@link java.util.logging}.
 */
public interface ApplicationLogger {
    void info(String message, Object... arguments);

    void warn(String message, Object... arguments);

    void warn(String message, Throwable throwable);

    void error(String message, Object... arguments);

    void error(String message, Throwable throwable);

    void debug(String message, Object... arguments);
}