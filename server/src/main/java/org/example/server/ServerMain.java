package org.example.server;

import org.example.server.config.DataSourceProvider;
import org.example.server.config.EnvironmentLoader;
import org.jpos.q2.Q2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Entry point for the Rayan jPOS server module.
 */
public final class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);
    private static final List<String> RESOURCES = List.of(
            "deploy/server-channel.xml",
            "deploy/server-mux.xml",
            "deploy/server-txnmgr.xml",
            "logback.xml",
            "packager/iso87ascii.xml"
    );

    private ServerMain() {
    }

    public static void main(String[] args) {
        EnvironmentLoader.load();

        try {
            Path workingDirectory = prepareWorkingDirectory();
            System.setProperty("jpos.home", workingDirectory.toString());
            System.setProperty("q2.deploy.dir", workingDirectory.resolve("deploy").toString());
            System.setProperty("logback.configurationFile", workingDirectory.resolve("logback.xml").toString());

            LOGGER.info("Starting jPOS Q2 from {}", workingDirectory);
            Q2 q2 = new Q2();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(q2)));
            q2.start();
            if (!q2.ready(10_000L)) {
                LOGGER.warn("jPOS Q2 did not reach ready state within 10 seconds");
            }
            LOGGER.info("jPOS Q2 started successfully");
            waitForShutdown(q2);
        } catch (IOException e) {
            LOGGER.error("Failed to prepare jPOS working directory", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Server interrupted", e);
        } finally {
            DataSourceProvider.close();
        }
    }

    private static void shutdown(Q2 q2) {
        try {
            LOGGER.info("Shutting down jPOS Q2");
            q2.shutdown();
        } catch (Exception e) {
            LOGGER.warn("Unexpected error while shutting down Q2", e);
        } finally {
            DataSourceProvider.close();
        }
    }

    private static void waitForShutdown(Q2 q2) throws InterruptedException {
        while (q2.running()) {
            Thread.sleep(1000L);
        }
    }

    private static Path prepareWorkingDirectory() throws IOException {
        Path configuredHome = EnvironmentLoader.get("JPOS_HOME")
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .orElseGet(() -> {
                    try {
                        return Files.createTempDirectory("rayan-jpos-q2");
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to create temporary jPOS home directory", e);
                    }
                });

        if (!Files.exists(configuredHome)) {
            Files.createDirectories(configuredHome);
        }

        for (String resource : RESOURCES) {
            Path target = configuredHome.resolve(resource);
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = ServerMain.class.getClassLoader().getResourceAsStream(resource)) {
                if (inputStream == null) {
                    throw new IOException("Resource not found on classpath: " + resource);
                }
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return configuredHome;
    }
}