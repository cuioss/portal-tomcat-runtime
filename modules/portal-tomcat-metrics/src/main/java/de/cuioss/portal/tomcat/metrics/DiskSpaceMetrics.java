package de.cuioss.portal.tomcat.metrics;

import static java.util.Objects.requireNonNull;
import static org.eclipse.microprofile.metrics.MetricType.GAUGE;
import static org.eclipse.microprofile.metrics.MetricUnits.BYTES;

import java.io.File;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

import de.cuioss.portal.metrics.RegistryHelper;
import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Registers metrics for free- and total disk space for a given path.
 *
 * @author Sven Haag
 * @see
 *      <a href=
 *      "https://github.com/micrometer-metrics/micrometer/blob/v1.3.5/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/jvm/DiskSpaceMetrics.java">micrometer-metrics</a>
 */
@ToString
@EqualsAndHashCode
final class DiskSpaceMetrics {

    private static final CuiLogger LOGGER = new CuiLogger(DiskSpaceMetrics.class);

    private final Tag[] tags;
    private final File path;
    private final boolean micrometerFormat;

    /**
     * Register a file path for metering its free and total disk space.
     *
     * @param path to be metered
     *
     * @throws NullPointerException if path is null
     */
    DiskSpaceMetrics(final File path, final boolean micrometerFormat) {
        requireNonNull(path);
        this.path = path;
        this.tags = new Tag[] { new Tag("path", path.getAbsolutePath()) };
        this.micrometerFormat = micrometerFormat;
    }

    /**
     * @param registry to be bound to
     *
     * @throws NullPointerException if registry or exportType are {@code null}
     */
    public void bindTo(final MetricRegistry registry) {
        requireNonNull(registry);

        final var registryHelper = new RegistryHelper(LOGGER, registry);
        registryHelper.bindIfNotPresent(
                new ExtendedMetadataBuilder()
                        .withName("disk.free")
                        .withDescription("Usable space for path")
                        .withUnit(BYTES)
                        .withType(GAUGE)
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                (Gauge<Long>) path::getUsableSpace,
                tags);

        registryHelper.bindIfNotPresent(
                new ExtendedMetadataBuilder()
                        .withName("disk.total")
                        .withDescription("Total space for path")
                        .withUnit(BYTES)
                        .withType(GAUGE)
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                (Gauge<Long>) path::getTotalSpace,
                tags);
    }
}
