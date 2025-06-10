package at.allaboutapps.quarkus.integresql.runtime;

import at.allaboutapps.integresql.client.IntegresqlJavaClient;
import at.allaboutapps.integresql.config.IntegresqlClientConfig;
import at.allaboutapps.quarkus.integresql.runtime.config.IntegresqlRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Recorder responsible for creating the IntegreSQL client bean at runtime
 * startup.
 */
@Recorder
public class IntegresqlRecorder {

    private static final Logger log = Logger.getLogger(IntegresqlRecorder.class);

    /**
     * Creates a Supplier for the IntegresqlJavaClient bean.
     * This method runs at runtime startup.
     *
     * @return A Supplier that creates the client instance.
     */
    public Supplier<IntegresqlJavaClient> configureIntegresqlClient(IntegresqlRuntimeConfig config) {
        return () -> {
            String baseUrl = config.baseUrl();
            String apiVersion = config.apiVersion();
            boolean debug = config.debug();
            Optional<Integer> overridePort = config.overridePort();

            if (overridePort.isPresent()) {
                log.infof("Overriding port to %d", overridePort.get());
            }

            IntegresqlClientConfig clientSpecificConfig = IntegresqlClientConfig.customConfig(
                    baseUrl,
                    apiVersion,
                    debug,
                    overridePort);

            return new IntegresqlJavaClient(clientSpecificConfig);
        };
    }
}