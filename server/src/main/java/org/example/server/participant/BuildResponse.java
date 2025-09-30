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
 * Builds the ISO-8583 response message using the data stored in the transaction context.
 */
public class BuildResponse implements TransactionParticipant {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildResponse.class);

    @Override
    public int prepare(long id, Serializable context) {
        return PREPARED | NO_JOIN | READONLY;
    }

    @Override
    public void commit(long id, Serializable context) {
        if (context instanceof Context ctx) {
            buildResponse(ctx, false);
        } else {
            LOGGER.error("Invalid transaction context type during commit: {}", context);
        }
    }

    @Override
    public void abort(long id, Serializable context) {
        if (context instanceof Context ctx) {
            buildResponse(ctx, true);
        } else {
            LOGGER.error("Invalid transaction context type during abort: {}", context);
        }
    }

    private void buildResponse(Context ctx, boolean aborted) {
        Object candidate = ctx.get(ServerContextKeys.REQUEST);
        if (!(candidate instanceof ISOMsg request)) {
            LOGGER.error("Cannot build response because request message is missing");
            return;
        }

        try {
            ISOMsg response = (ISOMsg) request.clone();
            response.setResponseMTI();

            String responseCode = (String) ctx.get(ServerContextKeys.RESPONSE_CODE);
            if (responseCode == null) {
                responseCode = aborted ? "96" : "00";
            }
            response.set(39, responseCode);

            Object approval = ctx.get(ServerContextKeys.APPROVAL_CODE);
            if (approval instanceof String approvalCode) {
                response.set(38, approvalCode);
            }

            Object errorMessage = ctx.get(ServerContextKeys.ERROR_MESSAGE);
            if (errorMessage instanceof String message && !message.isBlank()) {
                response.set(44, message);
            }

            ctx.put(ServerContextKeys.RESPONSE, response);
            LOGGER.debug("Built ISO-8583 response with MTI {} and code {}", response.getMTI(), responseCode);
        } catch (ISOException e) {
            LOGGER.error("Failed to build ISO-8583 response", e);
        }
    }
}