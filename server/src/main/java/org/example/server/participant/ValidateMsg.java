package org.example.server.participant;

import org.example.server.util.ServerContextKeys;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Validates that the inbound ISO-8583 message contains the required fields.
 */
public class ValidateMsg implements TransactionParticipant {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateMsg.class);
    private static final int[] REQUIRED_FIELDS = {2, 3, 4, 7, 11, 41};

    @Override
    public int prepare(long id, Serializable context) {
        if (!(context instanceof Context ctx)) {
            LOGGER.error("Invalid transaction context type: {}", context == null ? "null" : context.getClass());
            return ABORTED | NO_JOIN;
        }
        Object candidate = ctx.get(ServerContextKeys.REQUEST);
        if (!(candidate instanceof ISOMsg request)) {
            LOGGER.error("Missing ISO message in transaction context");
            ctx.put(ServerContextKeys.ERROR_MESSAGE, "No ISO message present");
            ctx.put(ServerContextKeys.RESPONSE_CODE, "96");
            return ABORTED | NO_JOIN;
        }

        for (int field : REQUIRED_FIELDS) {
            if (!request.hasField(field)) {
                LOGGER.warn("Incoming transaction missing required field {}", field);
                ctx.put(ServerContextKeys.ERROR_MESSAGE, "Missing required field " + field);
                ctx.put(ServerContextKeys.RESPONSE_CODE, "12");
                return ABORTED | NO_JOIN;
            }
        }

        try {
            String mti = request.getMTI();
            if (!"0200".equals(mti)) {
                LOGGER.warn("Unsupported MTI {} received", mti);
                ctx.put(ServerContextKeys.ERROR_MESSAGE, "Unsupported MTI");
                ctx.put(ServerContextKeys.RESPONSE_CODE, "12");
                return ABORTED | NO_JOIN;
            }
            LOGGER.debug("Validated inbound ISO-8583 message with MTI {}", mti);
        } catch (ISOException e) {
            LOGGER.error("Unable to read MTI from request", e);
            ctx.put(ServerContextKeys.ERROR_MESSAGE, "Invalid MTI");
            ctx.put(ServerContextKeys.RESPONSE_CODE, "96");
            return ABORTED | NO_JOIN;
        }

        return PREPARED | NO_JOIN | READONLY;
    }
}