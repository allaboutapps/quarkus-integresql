package at.allaboutapps.quarkus.integresql.deployment;

/**
 * Constants used across the IntegreSQL deployment module.
 */
public final class IntegresqlConstants {
    private IntegresqlConstants() {
        // Prevent instantiation
    }

    // Feature name
    public static final String FEATURE = "integresql";

    // Configuration keys
    public static final String CONFIG_BASE_URL = "quarkus.integresql.base-url";
    public static final String CONFIG_API_VERSION = "quarkus.integresql.api-version";
    public static final String CONFIG_PORT = "quarkus.integresql.dev-services.db.port";
}