package at.allaboutapps.quarkus.integresql.deployment.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

import java.util.Optional;
import java.util.OptionalInt;

@ConfigGroup
public interface DatabaseConfig {
    /**
     * Name of the postgresql image to use to start the database
     * 
     * @return The image name.
     */
    @WithDefault("postgres:17.4-alpine")
    String imageName();

    /**
     * The port to use for the database.
     * 
     * @return The port.
     */
    OptionalInt port();

    /**
     * The host to use for the database.
     * 
     * @return The host.
     */
    Optional<String> host();
}