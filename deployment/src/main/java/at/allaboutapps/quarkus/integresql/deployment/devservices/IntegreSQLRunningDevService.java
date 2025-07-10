package at.allaboutapps.quarkus.integresql.deployment.devservices;

import at.allaboutapps.quarkus.integresql.deployment.IntegresqlConstants;
import at.allaboutapps.quarkus.integresql.deployment.config.IntegresqlBuildTimeConfig;
import at.allaboutapps.quarkus.integresql.deployment.container.IntegreSQLContainer;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class IntegreSQLRunningDevService {
    private final String NETWORK_ALIAS = "integresql";
    private final DevServicesResultBuildItem.RunningDevService runningDevServiceSupplier;
    private static final Logger log = Logger.getLogger(IntegreSQLRunningDevService.class);
    /** Static holder for Dev Service configuration/closeable handle. */
    private static volatile IntegresqlDevServiceCfg cfg;

    public IntegreSQLRunningDevService(String feature, boolean useSharedNetwork, Duration timeout,
            IntegresqlBuildTimeConfig integresqlConfig, String serviceName) {
        runningDevServiceSupplier = this.run(feature, useSharedNetwork, timeout, integresqlConfig, serviceName);
    }

    public IntegreSQLRunningDevService(DevServicesResultBuildItem.RunningDevService runningDevServiceSupplier) {
        this.runningDevServiceSupplier = runningDevServiceSupplier;
    }

    public DevServicesResultBuildItem.RunningDevService run(String feature, boolean useSharedNetwork, Duration timeout,
            IntegresqlBuildTimeConfig integresqlConfig, String serviceName) {
        PostgreSQLContainer<?> postgresqlContainer = null; // Declare outside try for cleanup
        GenericContainer<?> container = null;
        Network network = null; // Declare outside try for cleanup

        int pgPort = integresqlConfig.devServices().db().port().orElse(PostgreSQLContainer.POSTGRESQL_PORT);

        log.infof("Using PostgreSQL port: %d", pgPort);

        try {
            postgresqlContainer = new PostgreSQLContainer<>(
                    DockerImageName.parse(integresqlConfig.devServices().db().imageName()))
                    .withDatabaseName("integresql-db")
                    .withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT)
                    .withUsername("dbuser")
                    .withPassword("dbpass")
                    .withStartupTimeout(Duration.ofSeconds(120))
                    .withNetworkAliases(NETWORK_ALIAS)
                    .withCommand("postgres", "-c", "shared_buffers=128MB", "-c", "fsync=off", "-c",
                            "synchronous_commit=off", "-c", "full_page_writes=off", "-c", "max_connections=100", "-c",
                            "client_min_messages=warning")
                    .waitingFor(Wait.forListeningPort());

            if (integresqlConfig.devServices().port().isPresent()) {
                container = new IntegreSQLContainer(integresqlConfig.devServices().port().getAsInt(), useSharedNetwork,
                        serviceName);
            } else {
                container = new IntegreSQLContainer(useSharedNetwork, serviceName);
            }

            // configure network
            if (useSharedNetwork) {
                postgresqlContainer.withNetwork(Network.SHARED);
                container.withNetwork(Network.SHARED);
            } else {
                network = Network.newNetwork();

                postgresqlContainer.withNetwork(network);
                container.withNetwork(network);
            }

            container
                    .withEnv("PGHOST", NETWORK_ALIAS)
                    // .withEnv("PGPORT", String.valueOf(pgPort))
                    .withEnv("PGUSER", postgresqlContainer.getUsername())
                    .withEnv("PGPASSWORD", postgresqlContainer.getPassword())
                    .withStartupTimeout(Duration.ofSeconds(120))
                    .dependsOn(postgresqlContainer) // Ensure Postgres starts first
                    .waitingFor(Wait.forListeningPort());

            Optional.ofNullable(timeout).ifPresent(container::withStartupTimeout);
            container.withEnv(integresqlConfig.devServices().containerEnv());

            if (integresqlConfig.devServices().db().port().isPresent()) {
                log.infof("Setting port bindings for PostgreSQL container: %d:%d", pgPort,
                        PostgreSQLContainer.POSTGRESQL_PORT);
                postgresqlContainer.setPortBindings(
                        List.of(String.format("%d:%d", pgPort, PostgreSQLContainer.POSTGRESQL_PORT)));
            }

            postgresqlContainer.start();
            container.start();

            // Get the actual mapped port that PostgreSQL is accessible on
            int postgresPort = postgresqlContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);

            String postgresHost = postgresqlContainer.getHost();

            if (integresqlConfig.devServices().db().host().isPresent()) {
                postgresHost = integresqlConfig.devServices().db().host().get();
            }

            String host = container.getHost();
            int port = container.getMappedPort(IntegreSQLContainer.INTEGRESQL_PORT);
            String baseUrl = String.format("http://%s:%d/api", host, port);

            log.infof("IntegreSQL Dev Service started: %s", baseUrl);
            log.infof("PostgreSQL is accessible on port: %d", postgresPort);

            cfg = new IntegresqlDevServiceCfg(container, postgresqlContainer,
                    useSharedNetwork ? null : container.getNetwork());
            Map<String, String> config = Map.of(
                    IntegresqlConstants.CONFIG_BASE_URL, baseUrl,
                    IntegresqlConstants.CONFIG_PORT, String.valueOf(postgresPort),
                    IntegresqlConstants.CONFIG_HOST, postgresHost,
                    IntegresqlConstants.CONFIG_API_VERSION, "v1");

            return new DevServicesResultBuildItem.RunningDevService(feature, container.getContainerId(), cfg::close,
                    config);
        } catch (Exception e) {
            log.error("Error starting dedicated IntegreSQL/PostgreSQL containers", e);
            // Attempt cleanup of potentially partially started resources
            if (cfg != null) { // If cfg was created
                cfg.close();
            } else { // If cfg wasn't created, try stopping individually
                if (container != null && container.isRunning())
                    container.stop();
                if (postgresqlContainer != null && postgresqlContainer.isRunning())
                    postgresqlContainer.stop();
                if (!useSharedNetwork && network != null)
                    network.close(); // Close network only if we created it
            }
            cfg = null; // Ensure cfg is null on failure
            return null; // Indicate failure
        }
    }

    public DevServicesResultBuildItem.RunningDevService getRunningDevService() {
        return runningDevServiceSupplier;
    }

    private static class IntegresqlDevServiceCfg implements Closeable {
        private final GenericContainer<?> integresqlContainer;
        private final PostgreSQLContainer<?> postgresContainer; // Null if reusing PG Dev Svc (now unused path)
        private final Network network; // Null if reusing shared network

        IntegresqlDevServiceCfg(GenericContainer<?> integresqlContainer, PostgreSQLContainer<?> postgresContainer,
                Network network) {
            this.integresqlContainer = Objects.requireNonNull(integresqlContainer);
            this.postgresContainer = postgresContainer; // Can be null
            this.network = network; // Can be null
        }

        @Override
        public void close() {
            log.info("Stopping IntegreSQL Dev Service resources...");
            // Stop IntegreSQL first
            try {
                if (integresqlContainer.isRunning()) {
                    integresqlContainer.stop();
                }
            } catch (Exception e) {
                log.error(String.format("Failed to stop IntegreSQL container: %s", e.getMessage()));
            }
            // Stop Postgres ONLY if we started it (postgresContainer is not null)
            try {
                if (postgresContainer != null && postgresContainer.isRunning()) {
                    postgresContainer.stop();
                }
            } catch (Exception e) {
                log.error(String.format("Failed to stop PostgreSQL container for IntegreSQL: %s", e.getMessage()));
            }
            // Close network ONLY if we created it (network is not null)
            try {
                if (network != null) {
                    network.close();
                }
            } catch (Exception e) {
                log.error(String.format("Failed to close network for IntegreSQL Dev Service: %s", e.getMessage()));
            }
            log.info("IntegreSQL Dev Service resources stopped.");
        }
    }
}
