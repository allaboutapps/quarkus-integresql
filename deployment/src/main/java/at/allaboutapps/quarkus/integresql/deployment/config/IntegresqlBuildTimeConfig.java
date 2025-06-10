package at.allaboutapps.quarkus.integresql.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.integresql")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface IntegresqlBuildTimeConfig {

    /**
     * The configuration for the Dev Services.
     * This is set to true by default.
     *
     * @return the dev services configuration
     */
    DevServicesConfig devServices();
}
