package org.example.server.util;

/**
 * Keys used to store data in the jPOS transaction {@link org.jpos.transaction.Context}.
 */
public final class ServerContextKeys {
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String APPROVAL_CODE = "approvalCode";

    private ServerContextKeys() {
    }
}