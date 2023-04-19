package de.cuioss.portal.tomcat.metrics;

import static de.cuioss.portal.metrics.RegistryHelper.THREADS_SUFFIX;
import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Objects;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * Registers a metric named <code>thread.state.count</code> for each currently present Thread state. It counts all
 * threads according to their state.
 *
 * @author Sven Haag
 * @see
 * <a href="https://github.com/micrometer-metrics/micrometer/blob/v1.3.5/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/jvm/JvmThreadMetrics.java">micrometer-metrics</a>
 */
@UtilityClass
final class ThreadMetrics {

    /**
     * Registers a {@link Gauge} of type {@link Long}, that counts all threads according to their {@link Thread.State}.
     *
     * @param registry
     *
     * @throws NullPointerException if registry is null
     */
    static void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        requireNonNull(registry);

        final var threadBean = ManagementFactory.getThreadMXBean();

        registry.register(
            new ExtendedMetadataBuilder()
                // required according to MP spec
                .withName(micrometerFormat ? "jvm.threads.peak" : "thread.max.count")
                .withDisplayName("Peak Thread Count")
                .withType(MetricType.GAUGE)
                .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                .withDescription("Displays the peak live thread count since the Java Virtual Machine started or peak" +
                    " was reset. This includes daemon and non-daemon threads.")
                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                .build(),
            (Gauge) threadBean::getPeakThreadCount);

        registry.register(
            new ExtendedMetadataBuilder()
                // required according to MP spec
                .withName(micrometerFormat ? "jvm.threads.daemon" : "thread.daemon.count")
                .withType(MetricType.GAUGE)
                .withDisplayName("Daemon Thread Count")
                .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                .withDescription("Displays the current number of live daemon threads.")
                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                .build(),
            (Gauge) threadBean::getDaemonThreadCount);

        registry.register(
            new ExtendedMetadataBuilder()
                // required according to MP spec
                .withName(micrometerFormat ? "jvm.threads.live" : "thread.count")
                .withDisplayName("Thread count")
                .withType(MetricType.GAUGE)
                .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                .withDescription("The current number of live threads including both daemon and non-daemon threads")
                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                .build(),
            (Gauge) threadBean::getThreadCount);

        final var threadStatesMetadata = new ExtendedMetadataBuilder()
            .withName(micrometerFormat ? "jvm.threads.states" : "thread.state.count")
            .withType(MetricType.GAUGE)
            .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
            .withDescription("The current number of threads having a particular state")
            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
            .build();
        for (final Thread.State state : Thread.State.values()) {
            registry.register(threadStatesMetadata,
                (Gauge) () -> getThreadStateCount(threadBean, state),
                new Tag("state", getStateTagValue(state)));
        }
    }

    private static long getThreadStateCount(final ThreadMXBean threadBean, final Thread.State state) {
        return Arrays.stream(threadBean.getThreadInfo(threadBean.getAllThreadIds()))
            .filter(Objects::nonNull)
            .filter(threadInfo -> threadInfo.getThreadState() == state)
            .count();
    }

    private static String getStateTagValue(final Thread.State state) {
        return state.name().toLowerCase().replace("_", "-");
    }
}
