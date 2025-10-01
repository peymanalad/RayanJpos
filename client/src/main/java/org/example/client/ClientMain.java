package org.example.client;

import io.github.cdimascio.dotenv.Dotenv;
import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOResponseListener;
import org.jpos.iso.MUX;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.util.Log;
import org.jpos.util.Logger;
import org.jpos.util.SimpleLogListener;

import java.io.InputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Simple standalone client that submits an ISO 8583 authorization request and logs the response.
 */
public final class ClientMain {
    private static final Logger ROOT_LOGGER = initialiseLogger();
    private static final Log LOG = new Log(ROOT_LOGGER, ClientMain.class.getSimpleName());

    private static Logger initialiseLogger() {
        Logger logger = Logger.getLogger("rayan-jpos-client");
        if (!logger.hasListeners()) {
            logger.addListener(new SimpleLogListener(System.out));
        }
        return logger;
    }

    private static void info(String message, Object... arguments) {
        LOG.info(format(message, arguments));
    }

    private static void warn(String message, Object... arguments) {
        LOG.warn(format(message, arguments));
    }

    private static void warn(Throwable throwable) {
        LOG.warn("Error disconnecting ISO channel", throwable);
    }

    private static void error(String message, Throwable throwable) {
        LOG.error(message != null ? message : "", throwable);
    }

    private static String format(String message, Object... arguments) {
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
            builder.append(arguments[argumentIndex++]);
            searchPosition = placeholder + 2;
        }
        builder.append(message.substring(searchPosition));
        while (argumentIndex < arguments.length) {
            builder.append(' ').append(arguments[argumentIndex++]);
        }
        return builder.toString();
    }
    private static final DateTimeFormatter TRANSMISSION_DATETIME = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter LOCAL_TRANSACTION_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter LOCAL_TRANSACTION_DATE = DateTimeFormatter.ofPattern("MMdd");

    private ClientMain() {
        // Utility class
    }

    public static void main(String[] args) {
        try {
            runClient();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error("Client execution interrupted", e);
            System.exit(1);
        } catch (Exception e) {
            error("Client execution failed", e);
            System.exit(1);
        }
    }

    private static void runClient() throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        List<String> hosts = determineHosts(dotenv.get("ISO_SERVER_HOST"));
        int port = parseInt(dotenv.get("ISO_SERVER_PORT"));
        long connectTimeout = parseLong(dotenv.get("ISO_CONNECT_TIMEOUT_MS"), TimeUnit.SECONDS.toMillis(30));
        long responseTimeout = parseLong(dotenv.get("ISO_RESPONSE_TIMEOUT_MS"), TimeUnit.SECONDS.toMillis(30));

        ISOException lastIsoException = null;
        IllegalStateException lastIllegalStateException = null;
        for (int index = 0; index < hosts.size(); index++) {
            String host = hosts.get(index);
            boolean hasFallback = index < hosts.size() - 1;
            try {
                executeClient(host, port, connectTimeout, responseTimeout, dotenv);
                return;
            } catch (ISOException e) {
                if (hasFallback && isRetryableHostException(e)) {
                    lastIsoException = e;
                    warn("Connection attempt to {}:{} failed ({}). Trying next candidate...", host, port, rootCauseMessage(e));
                    continue;
                }
                throw e;
            } catch (IllegalStateException e) {
                if (hasFallback) {
                    lastIllegalStateException = e;
                    warn("Connection attempt to {}:{} failed ({}). Trying next candidate...", host, port, e.getMessage());
                    continue;
                }
                throw e;
            }
        }

        if (lastIsoException != null) {
            throw new IllegalStateException("Unable to connect to any configured ISO server host " + hosts, lastIsoException);
        }
        if (lastIllegalStateException != null) {
            throw lastIllegalStateException;
        }
        throw new IllegalStateException("No ISO server host could be resolved from configuration");
    }

    private static void executeClient(String host, int port, long connectTimeout, long responseTimeout, Dotenv dotenv) throws Exception {

        try (InputStream packagerStream = ClientMain.class.getResourceAsStream("/packager/iso87ascii.xml")) {
            if (packagerStream == null) {
                throw new IllegalStateException("Unable to load ISO packager configuration");
            }

            GenericPackager packager = new GenericPackager(packagerStream);
            ASCIIChannel channel = new ASCIIChannel(host, port, packager);
            channel.setTimeout(safeTimeout(responseTimeout));

            try (SynchronousMux mux = startMux(channel)) {
                if (!mux.connect(connectTimeout)) {
                    throw new IllegalStateException("Unable to connect to ISO host " + host + ':' + port);
                }

                ISOMsg request = buildAuthorizationRequest(packager, dotenv);
                info("Sending ISO 0200 request: {}", describeIsoMessage(request));

                ISOMsg response = mux.request(request, responseTimeout);
                if (response == null) {
                    warn("No response received from ISO host within {} ms", responseTimeout);
                } else {
                    info("Received ISO 0210 response: {}", describeIsoMessage(response));
                }
            }
        }
    }

    private static List<String> determineHosts(String rawHostValue) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (rawHostValue != null) {
            for (String value : rawHostValue.split(",")) {
                String candidate = value.trim();
                if (!candidate.isEmpty()) {
                    hosts.add(candidate);
                }
            }
        }

        if (hosts.isEmpty()) {
            hosts.add("localhost");
        } else {
            boolean hasServerAlias = hosts.stream().anyMatch("server"::equalsIgnoreCase);
            boolean hasLoopback = hosts.stream().anyMatch(ClientMain::isLoopbackHost);
            if (hasServerAlias && !hasLoopback) {
                hosts.add("localhost");
            }
        }

        return new ArrayList<>(hosts);
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static boolean isRetryableHostException(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof UnknownHostException || cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }


    private static SynchronousMux startMux(ASCIIChannel channel) {
        return new SynchronousMux(channel);
    }

    private static ISOMsg buildAuthorizationRequest(GenericPackager packager, Dotenv dotenv) throws ISOException {
        ISOMsg request = new ISOMsg();
        request.setPackager(packager);
        request.setMTI("0200");

        LocalDateTime now = LocalDateTime.now(ZoneId.of(Optional.ofNullable(dotenv.get("ISO_CLIENT_TIMEZONE")).orElse("UTC")));
        String pan = Optional.ofNullable(dotenv.get("ISO_PAN")).orElse("4242424242424242");
        String processingCode = Optional.ofNullable(dotenv.get("ISO_PROCESSING_CODE")).orElse("000000");
        String amount = Optional.ofNullable(dotenv.get("ISO_AMOUNT")).orElse("000000010000");
        String posEntryMode = Optional.ofNullable(dotenv.get("ISO_POS_ENTRY_MODE")).orElse("012");
        String posConditionCode = Optional.ofNullable(dotenv.get("ISO_POS_CONDITION_CODE")).orElse("00");
        String acquiringInstitutionId = Optional.ofNullable(dotenv.get("ISO_ACQUIRER_ID")).orElse("000000");
        String terminalId = Optional.ofNullable(dotenv.get("ISO_TERMINAL_ID")).orElse("TERMID01");
        String merchantId = Optional.ofNullable(dotenv.get("ISO_MERCHANT_ID")).orElse("MERCHANT0001");
        String currency = Optional.ofNullable(dotenv.get("ISO_CURRENCY_CODE")).orElse("840");

        request.set(2, pan);
        request.set(3, processingCode);
        request.set(4, amount);
        request.set(7, TRANSMISSION_DATETIME.format(now));
        request.set(11, generateStan());
        request.set(12, LOCAL_TRANSACTION_TIME.format(now));
        request.set(13, LOCAL_TRANSACTION_DATE.format(now));
        request.set(22, posEntryMode);
        request.set(25, posConditionCode);
        request.set(32, acquiringInstitutionId);
        request.set(41, terminalId);
        request.set(42, merchantId);
        request.set(49, currency);

        return request;
    }

    private static String describeIsoMessage(ISOMsg message) {
        StringBuilder builder = new StringBuilder();
        try {
            builder.append("MTI=").append(message.getMTI());
        } catch (ISOException e) {
            builder.append("MTI=<err:").append(e.getMessage()).append('>');
        }
        for (int i = 2; i <= 128; i++) {
            if (message.hasField(i)) {
                builder.append(", F").append(i).append('=').append(message.getString(i));
            }
        }
        return builder.toString();
    }

    private static String generateStan() {
        long stan = System.currentTimeMillis() % 1_000_000L;
        return String.format("%06d", stan);
    }

    private static int parseInt(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 5000;
        } catch (NumberFormatException e) {
            warn("Invalid integer value '{}', using default {}", value, 5000);
            return 5000;
        }
    }

    private static long parseLong(String value, long defaultValue) {
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            warn("Invalid long value '{}', using default {}", value, defaultValue);
            return defaultValue;
        }
    }

    private static int safeTimeout(long timeout) {
        if (timeout <= 0) {
            return 0;
        }
        return timeout > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeout;
    }

    private static final class SynchronousMux implements MUX, AutoCloseable {
        private final BaseChannel channel;

        SynchronousMux(BaseChannel channel) {
            this.channel = channel;
        }

        private synchronized boolean ensureConnected() throws ISOException {
            if (channel.isConnected()) {
                return true;
            }
            try {
                channel.connect();
                return true;
            } catch (IOException e) {
                throw new ISOException(e);
            }
        }

        public boolean connect(long timeoutMs) throws ISOException, InterruptedException {
            long effectiveTimeout = timeoutMs <= 0 ? TimeUnit.SECONDS.toMillis(5) : timeoutMs;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(effectiveTimeout);
            ISOException lastException = null;
            while (System.nanoTime() < deadline) {
                try {
                    if (ensureConnected()) {
                        return true;
                    }
                } catch (ISOException e) {
                    lastException = e;
                    Thread.sleep(200);
                }
            }
            if (lastException != null) {
                throw lastException;
            }
            return channel.isConnected();
        }

        @Override
        public synchronized ISOMsg request(ISOMsg message, long timeout) throws ISOException {
            ensureConnected();
            try {
                channel.setTimeout(safeTimeout(timeout));
            } catch (SocketException e) {
                throw new ISOException(e);
            }

            try {
                channel.send(message);
                return channel.receive();
            } catch (SocketTimeoutException e) {
                return null;
            } catch (IOException e) {
                throw new ISOException(e);
            }
        }

        @Override
        public void request(ISOMsg message, long timeout, ISOResponseListener listener, Object handBack) throws ISOException {
            ISOMsg response = request(message, timeout);
            if (listener == null) {
                return;
            }
            if (response != null) {
                listener.responseReceived(response, handBack);
            } else {
                listener.expired(handBack);
            }
        }

        @Override
        public synchronized void send(ISOMsg m) throws IOException, ISOException {
            ensureConnected();
            channel.send(m);
        }

        @Override
        public synchronized boolean isConnected() {
            return channel.isConnected();
        }

        @Override
        public synchronized void close() {
            if (channel.isConnected()) {
                try {
                    channel.disconnect();
                } catch (IOException e) {
                    warn(e);                }
            }
        }
    }
}