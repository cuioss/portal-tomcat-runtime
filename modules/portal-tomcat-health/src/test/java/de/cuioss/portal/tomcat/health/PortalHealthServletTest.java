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
package de.cuioss.portal.tomcat.health;

import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_CUSTOMOUTPUT;
import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_ENABLED;
import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_HTTPCODEDOWN;
import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_LOG_IN_REQUIRED;
import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_ROLES_REQUIRED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.myfaces.test.mock.MockHttpServletRequest;
import org.apache.myfaces.test.mock.MockHttpServletResponse;
import org.apache.myfaces.test.mock.MockServletOutputStream;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.configuration.PortalConfigurationSource;
import de.cuioss.portal.core.test.junit5.EnablePortalConfiguration;
import de.cuioss.portal.core.test.mocks.authentication.PortalTestUserProducer;
import de.cuioss.portal.core.test.mocks.configuration.PortalTestConfiguration;
import de.cuioss.portal.tomcat.health.checks.HealthCheckDynamic;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.collect.CollectionLiterals;
import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.health.SmallRyeHealthReporter;

@EnableAutoWeld
@EnableTestLogger
@EnablePortalConfiguration
@AddBeanClasses({ SmallRyeHealthReporter.class, PortalTestUserProducer.class })
class PortalHealthServletTest {

    private static final CuiLogger log = new CuiLogger(PortalHealthServletTest.class);

    private static final String HEALTH_CUSTOM_JSON_DOWN = "/health_custom_down.json";
    private static final String HEALTH_CUSTOM_JSON_UP = "/health_custom_up.json";
    static final String STATUS_CUSTOM_JSON_UP = "/status_custom_up.json";
    private static final String STATUS_CUSTOM_JSON_DOWN = "/status_custom_down.json";

    private static final String STATUS_MP_JSON_UP = "/status_mp_up.json";
    private static final String STATUS_MP_JSON_DOWN = "/status_mp_down.json";
    private static final String HEALTH_MP_JSON_UP = "/health_mp_up.json";
    private static final String HEALTH_MP_JSON_DOWN = "/health_mp_down.json";

    private static final String ROLE = "Metrics-Collector";

    @Inject
    private PortalHealthServlet underTest;

    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;

    @Inject
    @PortalConfigurationSource
    private PortalTestConfiguration configuration;

    @Inject
    private PortalTestUserProducer testUserProducer;

    @Produces
    @Liveness
    private final HealthCheckDynamic livenessCheckDynamic = new HealthCheckDynamic();

    @Produces
    @Readiness
    private HealthCheckDynamic readinessCheckDynamic = new HealthCheckDynamic();

    @BeforeEach
    void beforeEach() {
        servletRequest = new MockHttpServletRequest();
        servletRequest.setServletPath(PortalHealthServlet.REQUEST_PATH_HEALTH);

        servletResponse = new MockHttpServletResponse();

        livenessCheckDynamic.down();
        readinessCheckDynamic.down();
    }

    @Test
    void shouldProvideStatusOnlyForNotLoggedInUserICW() throws IOException {
        configUserWithRoleRequired();
        configuration.fireEvent();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_CUSTOM_JSON_DOWN), SC_SERVICE_UNAVAILABLE);

        servletResponse = new MockHttpServletResponse();
        livenessCheckDynamic.up();
        readinessCheckDynamic.up();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_CUSTOM_JSON_UP), SC_OK);
    }

    @Test
    void shouldProvideStatusOnlyForNotLoggedInUserMP() throws IOException {
        configUserWithRoleRequired();
        configuration.fireEvent();

        TestLogLevel.DEBUG.addLogger(PortalHealthServlet.class);
        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_MP_JSON_DOWN), SC_SERVICE_UNAVAILABLE);

        servletResponse = new MockHttpServletResponse();
        livenessCheckDynamic.up();
        readinessCheckDynamic.up();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_MP_JSON_UP), SC_OK);
    }

    @Test
    void shouldProvideStatusOnlyForUserWithoutRoles() throws IOException {
        configUserWithRoleRequired();
        configuration.fireEvent();

        testUserProducer.authenticated(true).roles(Collections.emptyList());

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_CUSTOM_JSON_DOWN), SC_SERVICE_UNAVAILABLE);

        servletResponse = new MockHttpServletResponse();
        livenessCheckDynamic.up();
        readinessCheckDynamic.up();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_CUSTOM_JSON_UP), SC_OK);
    }

    @Test
    void shouldProvideDetailsForUserWithRolesICW() throws IOException {

        testUserProducer.authenticated(true).roles(CollectionLiterals.immutableList(ROLE));

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(HEALTH_CUSTOM_JSON_DOWN), SC_SERVICE_UNAVAILABLE);

        servletResponse = new MockHttpServletResponse();
        livenessCheckDynamic.up();
        readinessCheckDynamic.up();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(HEALTH_CUSTOM_JSON_UP), SC_OK);
    }

    @Test
    void shouldProvideDetailsForUserMP() throws IOException {

        testUserProducer.authenticated(true);
        configuration.fireEvent(PORTAL_HEALTHCHECK_CUSTOMOUTPUT, "false", PORTAL_HEALTHCHECK_ROLES_REQUIRED, "");

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(HEALTH_MP_JSON_DOWN), SC_SERVICE_UNAVAILABLE);

        servletResponse = new MockHttpServletResponse();
        livenessCheckDynamic.up();
        readinessCheckDynamic.up();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(HEALTH_MP_JSON_UP), SC_OK);
    }

    @Test
    void shouldProvideDetailsForUserMPLiveness() throws IOException {
        testUserProducer.authenticated(true);
        configuration.fireEvent(PORTAL_HEALTHCHECK_CUSTOMOUTPUT, "false", PORTAL_HEALTHCHECK_ROLES_REQUIRED, "");

        servletRequest.setServletPath(PortalHealthServlet.REQUEST_PATH_LIVENESS);
        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromString(livenessCheckDynamic.getJsonResponse()), SC_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldProvideDetailsForUserMPReadiness() throws IOException {
        testUserProducer.authenticated(true);
        readinessCheckDynamic.up();
        configuration.fireEvent(PORTAL_HEALTHCHECK_CUSTOMOUTPUT, "false", PORTAL_HEALTHCHECK_ROLES_REQUIRED, "");

        servletRequest.setServletPath(PortalHealthServlet.REQUEST_PATH_READINESS);
        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromString(readinessCheckDynamic.getJsonResponse()), 200);
    }

    @Test
    void upStatusDespiteNoReadinessCheck() throws IOException {
        testUserProducer.authenticated(true);
        readinessCheckDynamic = null;
        servletRequest.setServletPath(PortalHealthServlet.REQUEST_PATH_READINESS);

        underTest.executeDoGet(servletRequest, servletResponse);

        assertEquals(200, servletResponse.getStatus());
    }

    @Test
    void shouldHandleConfiguration() {
        assertTrue(underTest.isEnabled());
        configuration.fireEvent(PORTAL_HEALTHCHECK_ENABLED, "false");
        assertFalse(underTest.isEnabled());

        assertFalse(underTest.isLoggedInUserRequired());
        assertTrue(underTest.getRequiredRoles().isEmpty());
    }

    @Test
    void shouldHandleCustomHttpCode() throws IOException {
        final var custom = 999;
        configuration.put(PORTAL_HEALTHCHECK_HTTPCODEDOWN, String.valueOf(custom));
        configUserWithRoleRequired();
        configuration.fireEvent();

        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_CUSTOM_JSON_DOWN), custom);

        servletResponse = new MockHttpServletResponse();
        // MP must pick it up as well
        configuration.fireEvent(PORTAL_HEALTHCHECK_CUSTOMOUTPUT, "false");
        underTest.executeDoGet(servletRequest, servletResponse);
        assertJsonResponse(jsonFromClasspath(STATUS_MP_JSON_DOWN), custom);
    }

    @Test
    @SuppressWarnings("resource")
    // owolff: test only
    void shouldHandleWrongEndpoint() throws IOException {
        servletRequest.setServletPath(PortalHealthServlet.REQUEST_PATH_HEALTH + "/foo");
        underTest.executeDoGet(servletRequest, servletResponse);
        assertEquals(500, servletResponse.getStatus());
        assertEquals(StandardCharsets.UTF_8.name(), servletResponse.getCharacterEncoding());
        final var resultString = new String(((MockServletOutputStream) servletResponse.getOutputStream()).content());
        assertTrue(resultString.startsWith("Portal-152"));
    }

    private JsonObject jsonFromClasspath(final String classpath) {
        final var jsonReader = Json.createReader(new InputStreamReader(getClass().getResourceAsStream(classpath)));
        return jsonReader.readObject();
    }

    private JsonObject jsonFromString(final String responseJson) {
        final var jsonReader = Json.createReader(new StringReader(responseJson));
        return jsonReader.readObject();
    }

    private void assertJsonResponse(final JsonObject expectedResponse, final int expectedStatusCode)
            throws IOException {
        assertEquals(expectedStatusCode, servletResponse.getStatus());
        assertEquals("application/json", servletResponse.getContentType());
        assertEquals(StandardCharsets.UTF_8.name(), servletResponse.getCharacterEncoding());

        final var resultString = new String(((MockServletOutputStream) servletResponse.getOutputStream()).content());
        final var actual = Json.createReader(new StringReader(resultString)).readObject();

        log.info("Expected: {}", expectedResponse);
        log.info("Actual: {}", actual);

        assertEquals(expectedResponse, actual);
    }

    private void configUserWithRoleRequired() {
        configuration.put(PORTAL_HEALTHCHECK_LOG_IN_REQUIRED, "true");
        configuration.put(PORTAL_HEALTHCHECK_ROLES_REQUIRED, ROLE);
    }
}
