package org.example.server.logging;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Creates {@link ApplicationLogger} instances while gracefully degrading to the JDK
 * logging facilities if SLF4J is absent at runtime. This avoids {@link ClassNotFoundException}
 * or {@link NoClassDefFoundError} during static initialisation of server components.
 */
public final class ApplicationLoggerFactory {
    private static final AtomicBoolean FALLBACK_REPORTED = new AtomicBoolean();

    private ApplicationLoggerFactory() {
    }

    public static ApplicationLogger getLogger(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (isSlf4jAvailable()) {
            return Slf4jLogger.create(type);
        }
        if (FALLBACK_REPORTED.compareAndSet(false, true)) {
            System.err.println("[Rayan-jPOS] SLF4J not detected on the classpath. Falling back to java.util.logging.");
        }
        return new JulLogger(java.util.logging.Logger.getLogger(type.getName()));
    }

    static boolean isSlf4jAvailable() {
        try {
            Class.forName("org.slf4j.LoggerFactory", false, ApplicationLoggerFactory.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static final class Slf4jLogger implements ApplicationLogger {
        private final org.slf4j.Logger delegate;

        private Slf4jLogger(org.slf4j.Logger delegate) {
            this.delegate = delegate;
        }

        private static ApplicationLogger create(Class<?> type) {
            return new Slf4jLogger(org.slf4j.LoggerFactory.getLogger(type));
        }

        @Override
        public void info(String message, Object... arguments) {
            delegate.info(message, arguments);
        }

        @Override
        public void warn(String message, Object... arguments) {
            delegate.warn(message, arguments);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            delegate.warn(message, throwable);
        }

        @Override
        public void error(String message, Object... arguments) {
            delegate.error(message, arguments);
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.error(message, throwable);
        }

        @Override
        public void debug(String message, Object... arguments) {
            delegate.debug(message, arguments);
        }
    }

    private static final class JulLogger implements ApplicationLogger {
        private final java.util.logging.Logger delegate;

        private JulLogger(java.util.logging.Logger delegate) {
            this.delegate = delegate;
        }

        @Override
        public void info(String message, Object... arguments) {
            delegate.log(Level.INFO, format(message, arguments));
        }

        @Override
        public void warn(String message, Object... arguments) {
            delegate.log(Level.WARNING, format(message, arguments));
        }

        @Override
        public void warn(String message, Throwable throwable) {
            delegate.log(Level.WARNING, message, throwable);
        }

        @Override
        public void error(String message, Object... arguments) {
            delegate.log(Level.SEVERE, format(message, arguments));
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.log(Level.SEVERE, message, throwable);
        }

        @Override
        public void debug(String message, Object... arguments) {
            delegate.log(Level.FINE, format(message, arguments));
        }

        private String format(String message, Object... arguments) {
            if (message == null || arguments == null || arguments.length == 0) {
                return message;
            }
            StringBuilder builder = new StringBuilder();
            int searchPosition = 0;
            int argumentIndex = 0;
            while (argumentIndex < arguments.length) {
                int placeholder = message.indexOf("{}", searchPosition);
                if (placeholder < 0) {
                    break;
                }
                builder.append(message, searchPosition, placeholder);
                builder.append(String.valueOf(arguments[argumentIndex++]));
                searchPosition = placeholder + 2;
            }
            builder.append(message.substring(searchPosition));
            while (argumentIndex < arguments.length) {
                builder.append(' ').append(String.valueOf(arguments[argumentIndex++]));
            }
            return builder.toString();
        }
    }
}