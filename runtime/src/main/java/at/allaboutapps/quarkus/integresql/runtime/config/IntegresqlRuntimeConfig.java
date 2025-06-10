package at.allaboutapps.quarkus.integresql.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/**
 * Quarkus runtime configuration properties for the IntegreSQL client.
 */
@ConfigMapping(prefix = "quarkus.integresql")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface IntegresqlRuntimeConfig {
    /**
     * The base URL of the IntegreSQL server API.
     * If not set, the Dev Service (if enabled) will provide a default.
     * Example: <a href="http://localhost:5000/api">...</a>
     */
    @WithDefault("http://localhost:5000/api")
    String baseUrl();

    /**
     * The API version string to append to the base URL.
     */
    @WithDefault("v1")
    String apiVersion();

    /**
     * Default request timeout for client operations.
     */
    @WithDefault("30S")
    Duration requestTimeout();

    /**
     * Whether to enable debug logging for the IntegreSQL client.
     * This is set to false by default.
     */
    @WithDefault("false")
    boolean debug();

    /**
     * Override the port the IntegreSQL client uses to connect to the PostgreSQL
     * server.
     * If not set, the default port will be used (5432).
     */
    Optional<Integer> overridePort();
}