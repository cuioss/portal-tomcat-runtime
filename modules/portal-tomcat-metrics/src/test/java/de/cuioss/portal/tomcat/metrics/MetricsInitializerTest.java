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
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_OS_SUN_ENABLED;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_TOMCAT_ENABLED;
import static de.cuioss.tools.collect.CollectionLiterals.mutableList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.configuration.PortalConfigurationSource;
import de.cuioss.portal.configuration.initializer.PortalInitializer;
import de.cuioss.portal.core.test.junit5.EnablePortalConfiguration;
import de.cuioss.portal.core.test.mocks.configuration.PortalTestConfiguration;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;

@EnableAutoWeld
@EnablePortalConfiguration
@AddBeanClasses({ MetricsRequestHandler.class, MetricRegistries.class })
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
class MetricsInitializerTest {

    @Inject
    @PortalInitializer
    private MetricsInitializer underTest;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry baseRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    private MetricRegistry appRegistry;

    @Inject
    @PortalConfigurationSource
    private PortalTestConfiguration configuration;

    @AfterEach
    void destroy() {
        for (MetricRegistry registry : mutableList(baseRegistry, vendorRegistry, appRegistry)) {
            registry.getMetricIDs().forEach(metricID -> registry.remove(metricID));
        }
        underTest.destroy();
    }

    @Test
    void disabledRegistration() {
        configuration.fireEvent(PORTAL_METRICS_ENABLED, "false");
        underTest.initialize();
        assertTrue(baseRegistry.getMetricIDs().isEmpty());
    }

    @Test
    void noSunOSMetrics() {
        configuration.put(PORTAL_METRICS_ENABLED, "true");
        configuration.put(PORTAL_METRICS_OS_SUN_ENABLED, "false");
        configuration.fireEvent();
        underTest.initialize();
        assertFalse(baseRegistry.getMetricIDs().stream().anyMatch(id -> id.getName().equals("process.cpu.usage")));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG, "Portal-017");
    }

    @Test
    void noTomcatMetrics() {
        configuration.put(PORTAL_METRICS_ENABLED, "true");
        configuration.put(PORTAL_METRICS_TOMCAT_ENABLED, "false");
        configuration.fireEvent();
        underTest.initialize();
        assertFalse(baseRegistry.getMetricIDs().stream().anyMatch(id -> id.getName().startsWith("tomcat")));
    }
}
