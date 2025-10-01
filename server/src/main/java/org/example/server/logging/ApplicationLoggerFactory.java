package org.example.server.logging;
import org.jpos.util.Log;
import org.jpos.util.Logger;
import org.jpos.util.SimpleLogListener;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Creates {@link ApplicationLogger} instances backed by the jPOS logging framework.
 * The factory initialises a shared {@link Logger} with a {@link SimpleLogListener}
 * so the application can emit log messages without depending on SLF4J at runtime.
 */
public final class ApplicationLoggerFactory {
    private static final String LOGGER_NAME = "rayan-jpos-server";
    private static final Logger ROOT_LOGGER = initialiseRootLogger();
    private ApplicationLoggerFactory() {
    }

    public static ApplicationLogger getLogger(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return new JposApplicationLogger(type);
    }

    private static Logger initialiseRootLogger() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        if (!logger.hasListeners()) {
            logger.addListener(new SimpleLogListener(defaultStream()));
        }
        return logger;
    }
    private static PrintStream defaultStream() {
        return System.out;
    }

    private static final class JposApplicationLogger implements ApplicationLogger {
        private final Log delegate;

        private JposApplicationLogger(Class<?> type) {
            this.delegate = new Log(ROOT_LOGGER, type.getSimpleName());
        }

        @Override
        public void info(String message, Object... arguments) {
            delegate.info(format(message, arguments));
        }

        @Override
        public void warn(String message, Object... arguments) {
            delegate.warn(format(message, arguments));
        }

        @Override
        public void warn(String message, Throwable throwable) {
            delegate.warn(message != null ? message : "", throwable);
        }

        @Override
        public void error(String message, Object... arguments) {
            delegate.error(format(message, arguments));
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.error(message != null ? message : "", throwable);
        }

        @Override
        public void debug(String message, Object... arguments) {
            delegate.debug(format(message, arguments));
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

    private static final class Slf4jBridge {
        private static final Method GET_LOGGER;
        private static final Method INFO;
        private static final Method WARN;
        private static final Method WARN_WITH_THROWABLE;
        private static final Method ERROR;
        private static final Method ERROR_WITH_THROWABLE;
        private static final Method DEBUG;
        private static final boolean AVAILABLE;

        static {
            Method getLogger = null;
            Method info = null;
            Method warn = null;
            Method warnWithThrowable = null;
            Method error = null;
            Method errorWithThrowable = null;
            Method debug = null;
            boolean available;
            try {
                ClassLoader classLoader = ApplicationLoggerFactory.class.getClassLoader();
                Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory", false, classLoader);
                Class<?> loggerClass = Class.forName("org.slf4j.Logger", false, classLoader);
                getLogger = loggerFactoryClass.getMethod("getLogger", Class.class);
                info = loggerClass.getMethod("info", String.class, Object[].class);
                warn = loggerClass.getMethod("warn", String.class, Object[].class);
                warnWithThrowable = loggerClass.getMethod("warn", String.class, Throwable.class);
                error = loggerClass.getMethod("error", String.class, Object[].class);
                errorWithThrowable = loggerClass.getMethod("error", String.class, Throwable.class);
                debug = loggerClass.getMethod("debug", String.class, Object[].class);
                available = true;
            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ex) {
                available = false;
            }
            GET_LOGGER = getLogger;
            INFO = info;
            WARN = warn;
            WARN_WITH_THROWABLE = warnWithThrowable;
            ERROR = error;
            ERROR_WITH_THROWABLE = errorWithThrowable;
            DEBUG = debug;
            AVAILABLE = available;
        }

        private Slf4jBridge() {
        }

        static ApplicationLogger tryCreate(Class<?> type) {
            if (!AVAILABLE) {
                return null;
            }
            try {
                Object delegate = GET_LOGGER.invoke(null, type);
                return new Slf4jLoggerProxy(delegate);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to create SLF4J logger", ex);
            }
        }

        static boolean isAvailable() {
            return AVAILABLE;
        }

        private static final class Slf4jLoggerProxy implements ApplicationLogger {
            private final Object delegate;

            private Slf4jLoggerProxy(Object delegate) {
                this.delegate = delegate;
            }

            @Override
            public void info(String message, Object... arguments) {
                invoke(INFO, message, arguments);
            }

            @Override
            public void warn(String message, Object... arguments) {
                invoke(WARN, message, arguments);
            }

            @Override
            public void warn(String message, Throwable throwable) {
                invoke(WARN_WITH_THROWABLE, message, throwable);
            }

            @Override
            public void error(String message, Object... arguments) {
                invoke(ERROR, message, arguments);
            }

            @Override
            public void error(String message, Throwable throwable) {
                invoke(ERROR_WITH_THROWABLE, message, throwable);
            }

            @Override
            public void debug(String message, Object... arguments) {
                invoke(DEBUG, message, arguments);
            }

            private void invoke(Method method, String message, Object... arguments) {
                if (method == null) {
                    return;
                }
                try {
                    if (arguments != null && method.getParameterCount() == 2 && method.getParameterTypes()[1].isArray()) {
                        method.invoke(delegate, message, arguments);
                    } else if (arguments == null || arguments.length == 0) {
                        method.invoke(delegate, message);
                    } else {
                        method.invoke(delegate, message, arguments);
                    }
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Failed to invoke SLF4J logger", ex);
                } catch (InvocationTargetException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    throw new IllegalStateException("SLF4J logger threw an exception", cause);
                }
            }

            private void invoke(Method method, String message, Throwable throwable) {
                if (method == null) {
                    return;
                }
                try {
                    method.invoke(delegate, message, throwable);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Failed to invoke SLF4J logger", ex);
                } catch (InvocationTargetException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    throw new IllegalStateException("SLF4J logger threw an exception", cause);
                }
            }
        }
    }
}