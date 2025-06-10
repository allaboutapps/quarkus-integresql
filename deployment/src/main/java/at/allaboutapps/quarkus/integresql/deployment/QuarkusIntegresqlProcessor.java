package at.allaboutapps.quarkus.integresql.deployment;

import at.allaboutapps.quarkus.integresql.deployment.config.IntegresqlBuildTimeConfig;
import at.allaboutapps.quarkus.integresql.deployment.devservices.IntegreSQLRunningDevService;
import at.allaboutapps.quarkus.integresql.runtime.config.IntegresqlRuntimeConfig;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

// --- Imports from Runtime Module ---
import at.allaboutapps.quarkus.integresql.runtime.IntegresqlRecorder;

// --- Standard Quarkus Deployment Imports ---
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

// --- IMPORT FROM YOUR STANDALONE CLIENT LIBRARY ---
// Import the client class itself to specify the bean type
import at.allaboutapps.integresql.client.IntegresqlJavaClient;
// --- END IMPORT FROM LIBRARY ---

/**
 * Quarkus build-time processor for the IntegreSQL Client extension.
 * Handles CDI bean registration and Dev Services setup.
 * This version ALWAYS starts its own PostgreSQL and IntegreSQL containers.
 */
public class QuarkusIntegresqlProcessor {

    private static final Logger log = Logger.getLogger(QuarkusIntegresqlProcessor.class);

    /** Static holder for the running Dev Service reference. */
    private static volatile DevServicesResultBuildItem.RunningDevService integresqlDevService;
    /** Static flag to log info message only on first start. */
    private static volatile boolean first = true;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(IntegresqlConstants.FEATURE);
    }

    /**
     * Build step to set up and start the IntegreSQL Dev Service if applicable.
     * This version always starts its own dedicated PostgreSQL and IntegreSQL
     * containers.
     */
    @BuildStep(onlyIf = DevServicesConfig.Enabled.class)
    public void startIntegresqlDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            IntegresqlBuildTimeConfig buildTimeConfig,
            BuildProducer<DevServicesResultBuildItem> devServicesProducer,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem) {
        if (integresqlDevService != null || !buildTimeConfig.devServices().enabled()) {
            log.debug("IntegreSQL Dev Service not starting: disabled or already running.");
            return;
        }
        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Container runtime is not available, cannot start IntegreSQL Dev Service.");
            return;
        }

        // Use Quarkus's log compressor for cleaner startup logs
        try (StartupLogCompressor compressor = new StartupLogCompressor(
                (LaunchMode.current().isDevOrTest() ? "(dev)" : "") + " IntegreSQL Dev Service starting:",
                consoleInstalledBuildItem,
                loggingSetupBuildItem)) {

            // Check idempotency using volatile flag
            if (integresqlDevService != null) {
                return;
            }

            try {
                // --- Always start both containers ---
                log.info("Starting dedicated PostgreSQL and IntegreSQL containers for IntegreSQL Dev Service.");
                IntegreSQLRunningDevService integreSQLdevservice = new IntegreSQLRunningDevService(
                        IntegresqlConstants.FEATURE,
                        buildTimeConfig.devServices().shared(),
                        Duration.of(0, ChronoUnit.SECONDS),
                        buildTimeConfig,
                        buildTimeConfig.devServices().serviceName());

                DevServicesResultBuildItem.RunningDevService newDevService = integreSQLdevservice
                        .getRunningDevService();

                if (newDevService == null) {
                    throw new RuntimeException("Failed to start IntegreSQL Dev Service containers.");
                }

                devServicesProducer.produce(newDevService.toBuildItem());
                integresqlDevService = newDevService;

                Map<String, String> generatedConfig = integresqlDevService.getConfig();
                if (generatedConfig != null && generatedConfig.containsKey(IntegresqlConstants.CONFIG_BASE_URL)) {
                    if (first) {
                        first = false;
                        log.infof("IntegreSQL Dev Service started successfully. API URL: %s/%s",
                                generatedConfig.get(IntegresqlConstants.CONFIG_BASE_URL),
                                generatedConfig.get(IntegresqlConstants.CONFIG_API_VERSION));
                    }
                } else {
                    log.error("IntegreSQL Dev Service started but failed to produce configuration.");
                    compressor.closeAndDumpCaptured(); // Show logs on failure
                }

            } catch (Throwable t) {
                compressor.closeAndDumpCaptured();
                log.error("Failed to start IntegreSQL Dev Service", t);
                integresqlDevService = null;
                first = true;
            }
        }
    }

    /**
     * Build step to produce the IntegreSQLClient CDI bean.
     * Runs after the Dev Service has potentially started and configured the base
     * URL.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void configureClientBean(
            IntegresqlRecorder recorder,
            IntegresqlRuntimeConfig runtimeConfig,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(IntegresqlJavaClient.class) // Use class from library
                .scope(ApplicationScoped.class)
                .supplier(recorder.configureIntegresqlClient(runtimeConfig)) // Pass RuntimeValue
                .setRuntimeInit() // Bean instantiated at runtime
                .done());
    }
}