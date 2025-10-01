package org.example.server.participant;

import org.example.server.config.DataSourceProvider;
import org.example.server.logging.ApplicationLogger;
import org.example.server.logging.ApplicationLoggerFactory;
import org.example.server.util.ServerContextKeys;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import javax.sql.DataSource;
import java.io.Serializable;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Persists the ISO-8583 transaction data into an Oracle database using a HikariCP datasource.
 */
public class PersistToOracle implements TransactionParticipant {
    private static final ApplicationLogger LOGGER = ApplicationLoggerFactory.getLogger(PersistToOracle.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INSERT_SQL = "INSERT INTO ISO_TRANSACTIONS " +
            "(MTI, PAN, PROCESSING_CODE, AMOUNT, TRANSMISSION_DATETIME, STAN, TERMINAL_ID) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    public PersistToOracle() {
        this(DataSourceProvider.getDataSource());
    }

    public PersistToOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, request.getMTI());
            statement.setString(2, request.getString(2));
            statement.setString(3, request.getString(3));
            statement.setString(4, request.getString(4));
            statement.setString(5, request.getString(7));
            statement.setString(6, request.getString(11));
            statement.setString(7, request.getString(41));
            statement.executeUpdate();

            String approvalCode = generateApprovalCode();
            ctx.put(ServerContextKeys.APPROVAL_CODE, approvalCode);
            if (ctx.get(ServerContextKeys.RESPONSE_CODE) == null) {
                ctx.put(ServerContextKeys.RESPONSE_CODE, "00");
            }

            LOGGER.info("Persisted transaction with STAN {}", request.getString(11));
            return PREPARED | NO_JOIN;
        } catch (SQLException | ISOException e) {
            LOGGER.error("Failed to persist transaction to Oracle", e);
            ctx.put(ServerContextKeys.ERROR_MESSAGE, "Database failure");
            ctx.put(ServerContextKeys.RESPONSE_CODE, "96");
            return ABORTED | NO_JOIN;
        }
    }

    private String generateApprovalCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}