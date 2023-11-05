package org.cyclonedx.gradle.utils;

import org.cyclonedx.CycloneDxSchema;

public class CycloneDxUtils {

    public static final CycloneDxSchema.Version DEFAULT_SCHEMA_VERSION = CycloneDxSchema.Version.VERSION_15;

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     * @return the CycloneDX schema to use
     */
    public static CycloneDxSchema.Version schemaVersion(String version) {
        switch (version) {
            case "1.0": return CycloneDxSchema.Version.VERSION_10;
            case "1.1": return CycloneDxSchema.Version.VERSION_11;
            case "1.2": return CycloneDxSchema.Version.VERSION_12;
            case "1.3": return CycloneDxSchema.Version.VERSION_13;
            case "1.4": return CycloneDxSchema.Version.VERSION_14;
            case "1.5": return CycloneDxSchema.Version.VERSION_15;
            default: return DEFAULT_SCHEMA_VERSION;
        }
    }


}
