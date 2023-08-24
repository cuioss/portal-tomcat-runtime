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

import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_ENABLED;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_MICROMETER_COMPATIBILITY;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_OS_SUN_ENABLED;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_TOMCAT_ENABLED;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import de.cuioss.portal.configuration.initializer.ApplicationInitializer;
import de.cuioss.portal.configuration.initializer.PortalInitializer;
import de.cuioss.tools.logging.CuiLogger;

@PortalInitializer
@ApplicationScoped
class MetricsInitializer implements ApplicationInitializer {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsInitializer.class);

    /** Keeps track of base and vendor-endpoints being already initialized. */
    private boolean initialized;

    /**
     * <h1>Attention!</h1>
     * <p>
     * By contract, a servlet container creates one instance of each servlet. A
     * dedicated thread is attached to each new incoming HTTP request to process the
     * request. So all threads share the servlet instances and by extension their
     * instance fields.<br>
     * To prevent any unexpected behavior at runtime, all servlet fields should then
     * be either static and/or final, <strong>or simply removed</strong>!
     * </p>
     */
    private static final List<Closeable> CLOSEABLE_LIST = new ArrayList<>();

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_ENABLED)
    private boolean metricsEnabled;

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_MICROMETER_COMPATIBILITY)
    private boolean micrometerCompatibility;

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_OS_SUN_ENABLED)
    private boolean sunSpecificOSMetricsEnabled;

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_TOMCAT_ENABLED)
    private boolean tomcatMetricsEnabled;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry baseRegistry;

    @Override
    @SuppressWarnings("squid:S2696") // setting the static initialized variable is the use-case
    public void initialize() {
        if (!metricsEnabled) {
            LOGGER.info("Metrics registration is disabled by configuration: " + PORTAL_METRICS_ENABLED);
        } else if (!initialized) {
            registerMetrics();

            // recognize amongst all servlet instances that the metrics registries has been
            // filled
            initialized = true;
        }
    }

    @Override
    public void destroy() {
        for (final Closeable closeable : CLOSEABLE_LIST) {
            try {
                closeable.close();
            } catch (final IOException e) {
                LOGGER.warn(e, "Could not close: {}", closeable.getClass());
            }
        }
        CLOSEABLE_LIST.clear();
        initialized = false;
    }

    @Override
    public Integer getOrder() {
        return ApplicationInitializer.ORDER_INTERMEDIATE;
    }

    private void registerMetrics() {
        if (micrometerCompatibility) {
            LOGGER.info("Registering Micrometer metrics");
        } else {
            LOGGER.info("Registering MicroProfile metrics");
        }

        final var jvmGcMetrics = new JvmGcMetrics(micrometerCompatibility);
        CLOSEABLE_LIST.add(jvmGcMetrics);
        jvmGcMetrics.bindTo(baseRegistry);

        JvmMetrics.bindTo(baseRegistry, micrometerCompatibility);
        ThreadMetrics.bindTo(baseRegistry, micrometerCompatibility);
        ClassLoaderMetrics.bindTo(baseRegistry, micrometerCompatibility);
        RuntimeMetrics.bindTo(baseRegistry, micrometerCompatibility);
        FileDescriptorMetrics.bindTo(baseRegistry, micrometerCompatibility);
        OperatingSystemMetrics.bindTo(baseRegistry, micrometerCompatibility, sunSpecificOSMetricsEnabled);
        new DiskSpaceMetrics(new File(System.getProperty("user.dir")), micrometerCompatibility).bindTo(baseRegistry);

        LOGGER.debug("Tomcat metrics enabled: {}", tomcatMetricsEnabled);
        if (tomcatMetricsEnabled) {
            new TomcatMetrics().bindTo(baseRegistry, micrometerCompatibility);
        }
    }
}
