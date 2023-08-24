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
import static org.eclipse.microprofile.metrics.MetricType.GAUGE;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;

import de.cuioss.portal.metrics.RegistryHelper;
import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * Registers metrics for file descriptors under Unix operating systems.
 *
 * @author Sven Haag
 * @see <a href=
 *      "https://github.com/micrometer-metrics/micrometer/blob/v1.3.5/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/system/FileDescriptorMetrics.java">micrometer-metrics</a>
 */
@UtilityClass
final class FileDescriptorMetrics {

    private static final CuiLogger LOGGER = new CuiLogger(FileDescriptorMetrics.class);

    /**
     * List of public, exported interface class names from supported JVM
     * implementations.
     */
    private static final List<String> UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
            "com.sun.management.UnixOperatingSystemMXBean", // HotSpot
            "com.ibm.lang.management.UnixOperatingSystemMXBean" // J9
    );

    /**
     * Adds the metrics to the given registry.
     *
     * @param registry         to be bound to
     * @param micrometerFormat whether to use the micrometer format
     *
     * @throws NullPointerException if registry is null
     */
    static void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        requireNonNull(registry);

        final var osBean = ManagementFactory.getOperatingSystemMXBean();
        final Class<?> osBeanClass = getFirstClassFound();
        final var openFilesMethod = detectMethod("getOpenFileDescriptorCount", osBean, osBeanClass);
        final var maxFilesMethod = detectMethod("getMaxFileDescriptorCount", osBean, osBeanClass);
        final var registryHelper = new RegistryHelper(LOGGER, registry);

        if (openFilesMethod != null) {
            registryHelper.bindIfNotPresent(
                    new ExtendedMetadataBuilder().withName("process.files.open")
                            .withDescription("The open file descriptor count").withType(GAUGE)
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge<Double>) () -> invoke(openFilesMethod, osBean));
        }

        if (maxFilesMethod != null) {
            registryHelper.bindIfNotPresent(
                    new ExtendedMetadataBuilder().withName("process.files.max")
                            .withDescription("The maximum file descriptor count").withType(GAUGE)
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge<Double>) () -> invoke(maxFilesMethod, osBean));
        }
    }

    private static double invoke(final Method method, final OperatingSystemMXBean osBean) {
        try {
            return method != null ? (double) (long) method.invoke(osBean) : Double.NaN;
        } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    private static Method detectMethod(final String name, final OperatingSystemMXBean osBean,
            final Class<?> osBeanClass) {
        if (osBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            osBeanClass.cast(osBean);
            return osBeanClass.getDeclaredMethod(name);
        } catch (final ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    private static Class<?> getFirstClassFound() {
        for (final String className : UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES) {
            try {
                return Class.forName(className);
            } catch (final ClassNotFoundException ignore) {
                // ignore
            }
        }
        return null;
    }
}
