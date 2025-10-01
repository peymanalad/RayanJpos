package org.example.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.server.logging.ApplicationLogger;
import org.example.server.logging.ApplicationLoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides a lazily initialised {@link DataSource} backed by HikariCP for Oracle databases.
 */
public final class DataSourceProvider {
    public static final String JDBC_URL_KEY = "ORACLE_JDBC_URL";
    public static final String USER_KEY = "ORACLE_DB_USER";
    public static final String PASSWORD_KEY = "ORACLE_DB_PASSWORD";
    public static final String MAX_POOL_KEY = "ORACLE_DB_MAX_POOL";
    public static final String CONNECTION_TIMEOUT_KEY = "ORACLE_DB_CONNECTION_TIMEOUT";

    private static final ApplicationLogger LOGGER = ApplicationLoggerFactory.getLogger(DataSourceProvider.class);
    private static final AtomicReference<HikariDataSource> DATA_SOURCE = new AtomicReference<>();

    private DataSourceProvider() {
    }

    public static DataSource getDataSource() {
        HikariDataSource current = DATA_SOURCE.get();
        if (current != null) {
            return current;
        }
        HikariDataSource created = createDataSource();
        if (DATA_SOURCE.compareAndSet(null, created)) {
            LOGGER.info("Initialised Oracle datasource pool targeting {}", created.getJdbcUrl());
            return created;
        }
        created.close();
        return DATA_SOURCE.get();
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(EnvironmentLoader.getRequired(JDBC_URL_KEY));
        config.setUsername(EnvironmentLoader.getRequired(USER_KEY));
        config.setPassword(EnvironmentLoader.getRequired(PASSWORD_KEY));
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMaximumPoolSize(EnvironmentLoader.getInt(MAX_POOL_KEY, 10));
        config.setConnectionTimeout(EnvironmentLoader.getInt(CONNECTION_TIMEOUT_KEY, 30000));
        config.setPoolName("rayan-jpos-oracle-pool");
        return new HikariDataSource(config);
    }

    public static void close() {
        HikariDataSource current = DATA_SOURCE.getAndSet(null);
        if (current != null) {
            LOGGER.info("Shutting down Oracle datasource pool");
            current.close();
        }
    }
}