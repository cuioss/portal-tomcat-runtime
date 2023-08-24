/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.portal.tomcat.metrics;

import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * @author Sven Haag
 */
@UtilityClass
final class OperatingSystemMetrics {

    private static final CuiLogger LOGGER = new CuiLogger(OperatingSystemMetrics.class);

    /**
     * @param registry                  to be used for registering OS metrics
     * @param micrometerCompatibility
     * @param sunSpecificMetricsEnabled try to add
     *                                  {@code com.sun.management.OperatingSystemMXBean}
     *                                  metrics
     */
    @SuppressWarnings("squid:S1191") // using SUN classes is the actual use-case here
    public static void bindTo(final MetricRegistry registry, final boolean micrometerCompatibility,
            final boolean sunSpecificMetricsEnabled) {
        requireNonNull(registry);

        final var operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

        registerCPULoadAverage(registry, micrometerCompatibility, operatingSystemMXBean);

        registerCPUCount(registry, micrometerCompatibility, operatingSystemMXBean);

        // some metrics are only available in jdk internal class
        // 'com.sun.management.OperatingSystemMXBean': cast to it.
        // the cast will fail for some JVM not derived from HotSpot (J9 for example) so
        // we check if
        // it is assignable to it
        if (!sunSpecificMetricsEnabled) {
            LOGGER.debug("Portal-017: SUN specific operating system metrics are disabled.");
        } else if (com.sun.management.OperatingSystemMXBean.class.isAssignableFrom(operatingSystemMXBean.getClass())) {
            try {
                final var internalOperatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;

                registerCPUUsage(registry, micrometerCompatibility, internalOperatingSystemMXBean);

                registerSystemCPUUsage(registry, micrometerCompatibility, internalOperatingSystemMXBean);

                registerProcessCPUTime(registry, micrometerCompatibility, internalOperatingSystemMXBean);

                registerMemoryFreeSize(registry, micrometerCompatibility, internalOperatingSystemMXBean);

                registerMemoryFreeSwap(registry, micrometerCompatibility, internalOperatingSystemMXBean);
            } catch (final ClassCastException cce) {
                // this should never occurs
                LOGGER.debug("""
                        Unable to cast the OperatingSystemMXBean to com.sun.management.OperatingSystemMXBean, \
                        not registering extended operating system metrics\
                        """, cce);
            }
        } else {
            LOGGER.warn("""
                    Could not cast java.lang.management.OperatingSystemMXBean to \
                    com.sun.management.OperatingSystemMXBean. Some operating system metrics won't be available.\
                    """);
        }
    }

    private static void registerMemoryFreeSwap(final MetricRegistry registry, final boolean micrometerCompatibility,
            com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerCompatibility ? "system.memory.free.swap" : "memory.freeSwapSize")
                        .withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES).withDisplayName("Free swap size")
                        .withDescription("Displays the amount of free swap space in bytes.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) internalOperatingSystemMXBean::getFreeSwapSpaceSize);
    }

    private static void registerMemoryFreeSize(final MetricRegistry registry, final boolean micrometerCompatibility,
            com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                .withName(micrometerCompatibility ? "system.memory.free.size" : "memory.freePhysicalSize")
                .withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES).withDisplayName("Free physical memory size")
                .withDescription("Displays the amount of free physical memory in bytes.")
                .skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) internalOperatingSystemMXBean::getFreePhysicalMemorySize);
    }

    private static void registerProcessCPUTime(final MetricRegistry registry, final boolean micrometerCompatibility,
            com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                // optional metrics, according to spec
                .withName(micrometerCompatibility ? "process.cpu.time" : "cpu.processCpuTime")
                .withType(MetricType.GAUGE).withUnit(MetricUnits.NANOSECONDS).withDisplayName("Process CPU time")
                .withDescription("""
                        Displays the CPU time used by the process on which the Java Virtual Machine is running\s\
                        in nanoseconds. The returned value is of nanoseconds precision but not necessarily\s\
                        nanoseconds accuracy. This method returns -1 if the the platform does not support\s\
                        this operation.""").skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) internalOperatingSystemMXBean::getProcessCpuTime);
    }

    private static void registerSystemCPUUsage(final MetricRegistry registry, final boolean micrometerCompatibility,
            com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                .withName(micrometerCompatibility ? "system.cpu.usage" : "cpu.systemLoad").withType(MetricType.GAUGE)
                .withDisplayName("Process CPU Time").withDescription("""
                        Displays the "current system CPU load" for the whole system.\s\
                        This value is a double in the [0.0,1.0] interval. A value of 0.0 means that all CPUs\s\
                        were idle during the recent period of time observed, while a value of 1.0 means that\s\
                        all CPUs were actively running 100% of the time during the recent period being\s\
                        observed. All values between 0.0 and 1.0 are possible depending of the activities going\s\
                        on in the system. If the metric is not available, the method returns a negative value.""")
                .skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) internalOperatingSystemMXBean::getSystemCpuLoad);
    }

    private static void registerCPUUsage(final MetricRegistry registry, final boolean micrometerCompatibility,
            com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                // optional metrics, according to spec
                .withName(micrometerCompatibility ? "process.cpu.usage" : "cpu.processCpuLoad")
                .withType(MetricType.GAUGE).withUnit(micrometerCompatibility ? MetricUnits.NONE : MetricUnits.PERCENT)
                .withDisplayName("Process CPU load").withDescription("""
                        Displays  the "recent cpu usage" for the Java Virtual Machine process.\s\
                        This value is a double in the [0.0,1.0] interval. A value of 0.0 means that none of\s\
                        the CPUs were running threads from the JVM process during the recent period of time\s\
                        observed, while a value of 1.0 means that all CPUs were actively running threads from\s\
                        the JVM 100% of the time during the recent period being observed. Threads from the JVM\s\
                        include the application threads as well as the JVM internal threads.\s\
                        All values between 0.0 and 1.0 are possible depending of the activities going on in\s\
                        the JVM process and the whole system. If the Java Virtual Machine recent CPU usage is\s\
                        not available, the method returns a negative value.""")
                .skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) internalOperatingSystemMXBean::getProcessCpuLoad);
    }

    private static void registerCPUCount(final MetricRegistry registry, final boolean micrometerCompatibility,
            OperatingSystemMXBean operatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                // required by MP spec
                .withName(micrometerCompatibility ? "system.cpu.count" : "cpu.availableProcessors")
                .withType(MetricType.GAUGE).withDisplayName("Available Processors").withDescription("""
                        Displays the number of processors available to the Java Virtual Machine. \
                        This value may change during a particular invocation of the virtual machine.\
                        """).skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) operatingSystemMXBean::getAvailableProcessors);
    }

    private static void registerCPULoadAverage(final MetricRegistry registry, final boolean micrometerCompatibility,
            OperatingSystemMXBean operatingSystemMXBean) {
        registry.register(new ExtendedMetadataBuilder()
                // optional metrics, according to MP spec
                .withName(micrometerCompatibility ? "system.cpu.load.average" : "cpu.systemLoadAverage")
                .withType(MetricType.GAUGE).withDisplayName("System Load Average")
                .withDescription(
                        """
                                Displays the system load average for the last minute. The system load average\s\
                                is the sum of the number of runnable entities queued to the available processors and the\s\
                                number of runnable entities running on the available processors averaged over a period of time.\s\
                                The way in which the load average is calculated is operating system specific but is typically a\s\
                                damped time-dependent average. If the load average is not available, a negative value is\s\
                                displayed. This attribute is designed to provide a hint about the system load and may be\s\
                                queried frequently. The load average may be unavailable on some platforms where it is expensive\s\
                                to implement this method.""")
                .skipsScopeInOpenMetricsExportCompletely(micrometerCompatibility).build(),
                (Gauge<?>) operatingSystemMXBean::getSystemLoadAverage);
    }
}
