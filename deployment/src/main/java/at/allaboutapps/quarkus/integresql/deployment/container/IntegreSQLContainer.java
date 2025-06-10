package at.allaboutapps.quarkus.integresql.deployment.container;

import io.quarkus.devservices.common.ConfigureUtil;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration; // Import Duration

public class IntegreSQLContainer extends GenericContainer<IntegreSQLContainer> {

    private static final DockerImageName IMAGE = DockerImageName.parse("ghcr.io/allaboutapps/integresql:latest");
    private static final String DEV_SERVICE_LABEL = "quarkus-integresql"; // Matches feature or service name
    public static final int INTEGRESQL_PORT = 5000; // Internal port of the IntegreSQL service

    private final boolean useSharedNetwork;
    private final String serviceName; // Used for labeling
    private final Integer fixedExposedPortOnHost; // The port on the host machine
    private String resolvedHostname; // Hostname within the shared network

    // --- UPDATED REGEX based on provided logs ---
    // This regex looks for the specific JSON message indicating the server has started.
    // It accounts for quotes and escapes special characters for Java and regex.
    private static final String INTEGRESQL_READY_LOG_REGEX = ".*\"message\":\"⇨ http server started on \\[::\\]:5000\\\\n\".*";

    public IntegreSQLContainer(Integer fixedExposedPortOnHost, boolean useSharedNetwork, String serviceName) {
        super(IMAGE);
        this.fixedExposedPortOnHost = fixedExposedPortOnHost;
        this.useSharedNetwork = useSharedNetwork;
        this.serviceName = serviceName; // Typically matches the feature name
        // Add a default label for easier identification
        withLabel(DEV_SERVICE_LABEL, this.serviceName);
    }

    public IntegreSQLContainer(boolean useSharedNetwork, String serviceName) {
        this(null, useSharedNetwork, serviceName);
    }

    @Override
    protected void configure() {
        super.configure();

        // Always expose the internal port so Testcontainers knows about it for mapping
        // and for getMappedPort() to work.
        if (this.fixedExposedPortOnHost != null) {
            // If a fixed host port is requested, bind the internal port to it.
            addFixedExposedPort(this.fixedExposedPortOnHost, INTEGRESQL_PORT);
        } else {
            // If no fixed host port, just expose the internal port for random mapping.
            addExposedPort(INTEGRESQL_PORT);
        }

        // Configure network aspects
        if (this.useSharedNetwork) {
            // ConfigureUtil.configureSharedNetwork sets up the container to join
            // Quarkus's shared Docker network and assigns it a predictable hostname.
            // The 'serviceName' (e.g., "integresql") is often used as part of this hostname.
            this.resolvedHostname = ConfigureUtil.configureSharedNetwork(this, this.serviceName);
        }
        // If not using shared network, Testcontainers will use its default network behavior.

        // Set the wait strategy after ports are configured
        super.setWaitStrategy(
                Wait.forLogMessage(INTEGRESQL_READY_LOG_REGEX, 1)
                        .withStartupTimeout(Duration.ofSeconds(120)) // Adjust timeout as needed
        );
    }

    /**
     * Gets the host for connecting to this container.
     * If using a shared network, it returns the resolved hostname within that network.
     * Otherwise, it returns the host as determined by Testcontainers (usually localhost or Docker host IP).
     */
    @Override
    public String getHost() {
        if (this.useSharedNetwork && this.resolvedHostname != null) {
            return this.resolvedHostname;
        }
        return super.getHost();
    }

    /**
     * Gets the mapped port on the host machine.
     * Relies on the port being exposed in configure().
     * @return The mapped port for the internal INTEGRESQL_PORT.
     */
    public int getPort() {
        // This will get the host port corresponding to the internal INTEGRESQL_PORT
        return getMappedPort(INTEGRESQL_PORT);
    }
}
