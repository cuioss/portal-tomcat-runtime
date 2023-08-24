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
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_LOG_IN_REQUIRED;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_MICROMETER_COMPATIBILITY;
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_ROLES_REQUIRED;
import static de.cuioss.tools.collect.CollectionLiterals.mutableList;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.CharArrayWriter;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.myfaces.test.mock.MockHttpServletRequest;
import org.apache.myfaces.test.mock.MockHttpServletResponse;
import org.apache.myfaces.test.mock.MockPrintWriter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.configuration.PortalConfigurationSource;
import de.cuioss.portal.configuration.initializer.PortalInitializer;
import de.cuioss.portal.core.test.junit5.EnablePortalConfiguration;
import de.cuioss.portal.core.test.mocks.configuration.PortalTestConfiguration;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;

@EnableAutoWeld
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
@EnablePortalConfiguration
@AddBeanClasses({ MetricsRequestHandler.class, MetricRegistries.class, MetricsInitializer.class })
class PortalMetricsServletTest {

    @Inject
    private PortalMetricsServlet underTest;

    @Inject
    @PortalInitializer
    private MetricsInitializer metricsInitializer;

    @Inject
    @PortalConfigurationSource
    private PortalTestConfiguration configuration;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry baseRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    private MetricRegistry appRegistry;

    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;
    private MockPrintWriter writer;

    @BeforeEach
    void beforeEach() {
        servletRequest = new MockHttpServletRequest();
        servletRequest.setServletPath(PortalMetricsServlet.URL_PATTERN);
        servletRequest.setContextPath("test-context");
        servletRequest.setMethod("GET");
        servletRequest.addHeader("Accept", "application/json");

        writer = new MockPrintWriter(new CharArrayWriter());
        servletResponse = new MockHttpServletResponse();
        servletResponse.setWriter(writer);
    }

    @AfterEach
    void destroy() {
        for (MetricRegistry registry : mutableList(baseRegistry, vendorRegistry, appRegistry)) {
            registry.getMetricIDs().forEach(metricID -> registry.remove(metricID));
        }
        underTest.destroy();
    }

    @Test
    void shouldRespondInSpringFormat() throws IOException {
        configuration.fireEvent(PORTAL_METRICS_ENABLED, "true");
        servletRequest.setPathInfo("/");
        metricsInitializer.initialize();

        underTest.executeDoGet(servletRequest, servletResponse);
        final var result = new String(writer.content());
        assertFalse(result.contains("base_"));
        assertFalse(result.contains("vendor_"));
        // must run in Tomcat to work: assertTrue(result.contains("tomcat_"));
    }

    @Test
    void shouldRespondToBasicCall() throws IOException {
        configuration.fireEvent(PORTAL_METRICS_ENABLED, "true");
        servletRequest.setPathInfo("/base");
        metricsInitializer.initialize();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertEquals(SC_OK, servletResponse.getStatus());
    }

    @Test
    void shouldRespondToOptionCall() throws IOException {
        configuration.put(PORTAL_METRICS_ENABLED, "true");
        configuration.put(PORTAL_METRICS_MICROMETER_COMPATIBILITY, "mp");
        configuration.fireEvent();

        servletRequest.setPathInfo("/base");
        servletRequest.setMethod("OPTIONS");

        metricsInitializer.initialize();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertEquals(SC_OK, servletResponse.getStatus());
    }

    @Test
    void shouldBeDisabledByDefault() {
        configuration.put(PORTAL_METRICS_LOG_IN_REQUIRED, "true");
        configuration.put(PORTAL_METRICS_ROLES_REQUIRED, "Metrics-Collector");
        configuration.fireEvent();

        metricsInitializer.initialize();
        assertFalse(underTest.isEnabled());
    }
}
