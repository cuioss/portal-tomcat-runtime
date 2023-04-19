package de.cuioss.portal.tomcat.metrics;

import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * @author Sven Haag
 */
@UtilityClass
final class RuntimeMetrics {

    static void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        requireNonNull(registry);

        final var runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        if (micrometerFormat) {
            registry.register(
                new ExtendedMetadataBuilder()
                    //.withName("process.runtime")
                    .withName("process.uptime")
                    .withDisplayName("JVM Uptime")
                    .withType(MetricType.GAUGE)
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withDescription("The uptime of the Java Virtual Machine.")
                    .skipsScopeInOpenMetricsExportCompletely(true)
                    .build(),
                (Gauge) runtimeMXBean::getUptime);

            registry.register(
                new ExtendedMetadataBuilder()
                    .withName("process.start.time")
                    .withDisplayName("JVM Starttime")
                    .withType(MetricType.GAUGE)
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withDescription("Start time of the process since unix epoch.")
                    .skipsScopeInOpenMetricsExportCompletely(true)
                    .build(),
                (Gauge) runtimeMXBean::getStartTime);
        } else {
            // required metric according to spec 2.3
            registry.register(Metadata.builder()
                    .withName("jvm.uptime")
                    .withDisplayName("JVM Uptime")
                    .withType(MetricType.GAUGE)
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withDescription("Displays the time elapsed since the start of the " +
                        "Java Virtual Machine in milliseconds.")
                    .build(),
                (Gauge) runtimeMXBean::getUptime);

            registry.register(
                Metadata.builder()
                    .withName("jvm.starttime")
                    .withDisplayName("JVM Starttime")
                    .withType(MetricType.GAUGE)
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withDescription("Displays the start time of the Java Virtual Machine " +
                        "since unix epoch in milliseconds.")
                    .build(),
                (Gauge) runtimeMXBean::getStartTime);
        }
    }
}
