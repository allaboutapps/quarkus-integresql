package at.allaboutapps.quarkus.integresql.it; // Create a suitable package

import at.allaboutapps.integresql.client.IntegresqlJavaClient;
import at.allaboutapps.integresql.client.dto.DatabaseConfig;
import at.allaboutapps.integresql.client.dto.TemplateDatabase;
import at.allaboutapps.integresql.client.dto.TestDatabase;
import at.allaboutapps.integresql.exception.IntegresqlException;
import at.allaboutapps.integresql.exception.ManagerNotReadyException;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test for the IntegreSQL Client Quarkus Extension.
 * This test will run with Dev Services enabled (if not otherwise configured).
 */
@QuarkusTest
public class IntegresqlClientIT {

    private static final Logger log = Logger.getLogger(IntegresqlClientIT.class);

    @Inject
    IntegresqlJavaClient client; // Your CDI bean provided by the extension

    @BeforeEach
    void checkClientAndReset() {
        assertNotNull(client, "IntegresqlJavaClient should be injected by Quarkus CDI.");
        // log.infof("IntegreSQL Base URL from config: %s",
        // ConfigProvider.getConfig().getValue("quarkus.integresql.base-url",
        // String.class));
        // log.infof("IntegreSQL DB port from config: %d",
        // ConfigProvider.getConfig().getValue("quarkus.integresql.dev-services.db.port",
        // int.class));

        // Attempt to reset tracking. This also serves as a basic connectivity check.
        // It might fail if the service is still starting up, so handle potential
        // exceptions.
        try {
            log.info("Attempting to reset IntegreSQL tracking...");
            client.resetAllTracking();
            log.info("IntegreSQL tracking reset successfully.");
        } catch (IntegresqlException e) {
            // Log a warning, as the service might still be initializing, especially on the
            // first test.
            // Depending on the IntegreSQL server's startup speed, this might sometimes be a
            // ManagerNotReadyException.
            log.warnf("Failed to reset IntegreSQL tracking (service might be starting or not fully ready): %s - %s",
                    e.getClass().getSimpleName(), e.getMessage());
            // You might choose to add a small delay/retry here in a real-world complex test
            // setup
            // or ensure your Dev Service wait strategy is very robust.
        } catch (Exception e) {
            log.error("Unexpected error during resetAllTracking", e);
            fail("Unexpected error during resetAllTracking: " + e.getMessage());
        }
    }

    @Test
    @Order(0)
    @DisplayName("Should inject client and initialize a template successfully")
    void testInitializeTemplate() {
        String testHash = "quarkus-it-hash-" + System.currentTimeMillis(); // Unique hash

        try {
            log.infof("Attempting to initialize template with hash: %s", testHash);
            TemplateDatabase template = client.initializeTemplate(testHash);

            assertNotNull(template, "TemplateDatabase object should not be null.");
            assertNotNull(template.database, "Template's inner database object should not be null.");
            assertNotNull(template.database.config, "Template's DatabaseConfig should not be null.");
            assertNotNull(template.database.config.database, "Template database name should not be null or empty.");
            assertFalse(template.database.config.database.isEmpty(), "Template database name should not be empty.");

            log.infof("Successfully initialized template: %s, DB name: %s", testHash,
                    template.database.config.database);

            // Clean up by discarding the template
            try {
                client.discardTemplate(testHash);
                log.infof("Successfully discarded template: %s", testHash);
            } catch (Exception e) {
                log.warnf("Failed to discard template %s during cleanup: %s", testHash, e.getMessage());
            }

        } catch (ManagerNotReadyException e) {
            // This might happen if the Dev Service isn't fully "warm" yet.
            // For a CI test, a more robust wait/retry or a very solid Dev Service wait
            // strategy is needed.
            log.warn(
                    "ManagerNotReadyException during initializeTemplate. Dev Service might need more time or a better wait strategy.",
                    e);
            fail("IntegreSQL manager was not ready. Dev Service might need more time or a better wait strategy. Error: "
                    + e.getMessage());
        } catch (Exception e) {
            log.error("testInitializeTemplate failed", e);
            fail("testInitializeTemplate failed with exception: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should discard a template and re-initialize it successfully")
    void testDiscardTemplate() {
        String hash = "java-discard-hash2";

        // 1. Initialize
        TemplateDatabase template = client.initializeTemplate(hash);
        assertNotNull(template, "TemplateDatabase should not be null after initialization.");
        assertNotNull(template.database, "Template's inner database object should not be null.");
        assertNotNull(template.database.config, "Template's DatabaseConfig should not be null.");
        assertNotNull(template.database.config.database, "Template database name should not be null or empty.");
        assertFalse(template.database.config.database.isEmpty(), "Template database name should not be empty.");
        log.infof("Initialized template for discard test: %s, DB name: %s", hash, template.database.config.database);

        // 2. Discard
        try {
            client.discardTemplate(hash);
            log.info("Successfully discarded template: " + hash);
        } catch (IntegresqlException e) {
            // If the discard fails, we log it but continue to test re-initialization.
            log.warn("Failed to discard template during testDiscardTemplate: " + e.getMessage());
            // Depending on your requirements, you might want to fail the test here.
            // fail("Failed to discard template: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to discard template during testDiscardTemplate", e);
            fail("Failed to discard template: " + e.getMessage());
        }

        // 3. Re-initialize (should succeed after discard)
        AtomicReference<TemplateDatabase> templateRef = new AtomicReference<>();
        templateRef.set(client.initializeTemplate(hash));
        assertNotNull(templateRef.get());

        // 4. Finalize (should succeed after re-initialize)
        try {
            client.finalizeTemplate(hash);
            log.infof("Discard and re-initialize/finalize test for %s successful.", hash);
        } catch (IntegresqlException e) {
            log.error("Failed to finalize template after re-initialization: " + e.getMessage());
            fail("Failed to finalize template after re-initialization: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during finalizeTemplate after re-initialization", e);
            fail("Unexpected error during finalizeTemplate after re-initialization: " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    void testFinalizeTemplate() {
        String hash = "java-finalize-hash3"; // Use different hash

        // 1. Initialize
        TemplateDatabase template = client.initializeTemplate(hash);
        assertNotNull(template, "TemplateDatabase should not be null after initialization.");
        assertNotNull(template.database, "Template's inner database object should not be null.");
        assertNotNull(template.database.config, "Template's DatabaseConfig should not be null.");
        assertNotNull(template.database.config.database, "Template database name should not be null or empty.");
        assertFalse(template.database.config.database.isEmpty(), "Template database name should not be empty.");
        log.infof("Initialized template for discard test: %s, DB name: %s", hash, template.database.config.database);

        // 2. Finalize
        try {
            client.finalizeTemplate(hash);
            log.infof("Discard and re-initialize/finalize test for %s successful.", hash);
        } catch (IntegresqlException e) {
            log.error("Failed to finalize template after re-initialization: " + e.getMessage());
            fail("Failed to finalize template after re-initialization: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during finalizeTemplate after re-initialization", e);
            fail("Unexpected error during finalizeTemplate after re-initialization: " + e.getMessage());
        }

        // 3. Try to finalize again (should probably throw TemplateNotFound or similar,
        // depends on server impl.)
        // The Go test doesn't check this, but it might be a useful check.
        // assertThrows(TemplateNotFoundException.class, () ->
        // client.finalizeTemplate(hash));
        log.infof("Finalize test for %s successful.", hash);
    }

    @Test
    @Order(4)
    void testGetTestDatabase() throws SQLException {
        String hash = "java-getdb-hash4";

        // 1. Initialize
        TemplateDatabase template = client.initializeTemplate(hash);
        assertNotNull(template, "TemplateDatabase should not be null after initialization.");
        assertNotNull(template.database, "Template's inner database object should not be null.");
        assertNotNull(template.database.config, "Template's DatabaseConfig should not be null.");
        assertNotNull(template.database.config.database, "Template database name should not be null or empty.");
        assertFalse(template.database.config.database.isEmpty(), "Template database name should not be empty.");
        log.infof("Initialized template for discard test: %s, DB name: %s", hash, template.database.config.database);

        // 2. Finalize
        try {
            client.finalizeTemplate(hash);
            log.infof("Discard and re-initialize/finalize test for %s successful.", hash);
        } catch (IntegresqlException e) {
            log.error("Failed to finalize template after re-initialization: " + e.getMessage());
            fail("Failed to finalize template after re-initialization: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during finalizeTemplate after re-initialization", e);
            fail("Unexpected error during finalizeTemplate after re-initialization: " + e.getMessage());
        }

        // 3. Try to finalize again (should probably throw TemplateNotFound or similar,
        // depends on server impl.)
        // The Go test doesn't check this, but it might be a useful check.
        // assertThrows(TemplateNotFoundException.class, () ->
        // client.finalizeTemplate(hash));
        log.infof("Finalize test for %s successful.", hash);

        // 2. Get first test database
        AtomicReference<TestDatabase> db1Ref = new AtomicReference<>();
        try {
            db1Ref.set(client.getTestDatabase(hash));
        } catch (IntegresqlException e) {
            log.error("Failed to get first test database: " + e.getMessage());
            fail("Failed to get first test database: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during getTestDatabase", e);
            fail("Unexpected error during getTestDatabase: " + e.getMessage());
        }

        TestDatabase db1 = db1Ref.get();
        assertNotNull(db1);
        assertNotNull(db1.database.config);
        assertNotEquals("", db1.database.templateHash);
        // Go test checked TemplateHash, but our DTO doesn't have it directly. We trust
        // the API call scope.

        log.infof("Got first test DB: %s", db1.database.templateHash);

        // 3. Ping first test database
        DatabaseConfig config1 = db1.database.config;
        config1.host = "localhost";

        int port = ConfigProvider.getConfig().getValue("quarkus.integresql.dev-services.db.port",
                int.class);

        config1.port = port;
        String url1 = config1.connectionString();

        try (Connection conn1 = DriverManager.getConnection(url1, config1.username, config1.password)) {
            assertTrue(conn1.isValid(5));
            log.infof("Successfully pinged DB1: %s", db1.database.templateHash);
        } catch (SQLException e) {
            fail("Failed to connect or ping DB1 (" + db1.database.templateHash + ")", e);
        }

        // 4. Get second test database (without returning first)
        AtomicReference<TestDatabase> db2Ref = new AtomicReference<>();
        try {
            db2Ref.set(client.getTestDatabase(hash));
        } catch (IntegresqlException e) {
            log.error("Failed to get second test database: " + e.getMessage());
            fail("Failed to get second test database: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during getTestDatabase for second DB", e);
            fail("Unexpected error during getTestDatabase for second DB: " + e.getMessage());
        }

        TestDatabase db2 = db2Ref.get();
        assertNotNull(db2);
        assertNotNull(db2.database.config);
        assertNotEquals("", db2.database.templateHash);
        assertEquals(db2.database.templateHash, db1.database.templateHash);
        assertNotEquals(db2.id, db1.id);
        log.infof("Got second test DB: %s", db2.database.templateHash);

        // 5. Ping second test database
        DatabaseConfig config2 = db2.database.config;
        config2.host = "localhost";
        config2.port = port;
        String url2 = config2.connectionString();
        try (Connection conn2 = DriverManager.getConnection(url2, config2.username, config2.password)) {
            assertTrue(conn2.isValid(5));
            log.infof("Successfully pinged DB2: %s", db2.database.templateHash);
        } catch (SQLException e) {
            fail("Failed to connect or ping DB2 (" + db2.database.templateHash + ")", e);
        }

        // Clean up (optional here as reset runs before next test)
        // client.returnTestDatabase(hash, db1.database.templateHash);
        // client.returnTestDatabase(hash, db2.database.templateHash);
    }
}
