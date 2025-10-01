package org.example.server.util;

/**
 * Keys used to store data in the jPOS transaction {@link org.jpos.transaction.Context}.
 */
public final class ServerContextKeys {
    /**
     * jPOS stores the inbound {@link org.jpos.iso.ISOMsg} under the upper-case
     * key {@code REQUEST}. The previous lowercase value caused transaction
     * participants to miss the ISO message and prevented QMUX from finding the
     * response produced by {@link org.example.server.participant.BuildResponse}.
     */
    public static final String REQUEST = "REQUEST";
    /**
     * TMUX expects the response message to be available under the upper-case
     * {@code RESPONSE} key before it can write it back to the network channel.
     */
    public static final String RESPONSE = "RESPONSE";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String APPROVAL_CODE = "approvalCode";

    private ServerContextKeys() {
    }
}