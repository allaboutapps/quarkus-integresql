package at.allaboutapps.quarkus.integresql.deployment.config;

import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.OptionalInt;

public interface DevServicesConfig {

    /**
     * Whether the dev services should be enabled or not.
     * This is set to true by default.
     *
     * @return true if dev services are enabled, false otherwise
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The name of the image to use for the dev service.
     * This is set to "ghcr.io/allaboutapps/integresql:latest" by default.
     *
     * @return the name of the image
     */
    @WithDefault("ghcr.io/allaboutapps/integresql:latest")
    String imageName();


    /**
     * The port to use for the dev service.
     * This is set to 5000 by default.
     *
     * @return the port
     */
    OptionalInt port();

    /**
     * Whether to use a shared network for the dev service.
     * This is set to true by default.
     *
     * @return true if a shared network is used, false otherwise
     */
    @WithDefault("false")
    boolean shared();

    /**
     * The name of the service to use for the dev service.
     * This is set to "integresql" by default.
     *
     * @return the name of the service
     */
    @WithDefault("integresql")
    String serviceName();

    /**
     * Environment variables that are passed to the container.
     */
    Map<String, String> containerEnv();

    DatabaseConfig db();
}
