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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheckResponse;

import de.cuioss.portal.configuration.types.ConfigAsList;
import de.cuioss.portal.core.servlet.AbstractPortalServlet;
import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;

/**
 * Servlet for responding to GET requests for:
 * <ul>
 * <li><code>health</code></li>
 * <li><code>health/liveness</code></li>
 * <li><code>health/readiness</code></li>
 * </ul>
 *
 * The /health endpoint represents the consolidated response of liveness and
 * readiness.
 *
 * @author Sven Haag
 */
@WebServlet(name = "PortalHealthCheckServlet", urlPatterns = { PortalHealthServlet.REQUEST_PATH_HEALTH,
        PortalHealthServlet.REQUEST_PATH_LIVENESS, PortalHealthServlet.REQUEST_PATH_READINESS })
@ApplicationScoped
@SuppressWarnings("squid:S1075") // owolff: A hard coded url is the actual use-case
public class PortalHealthServlet extends AbstractPortalServlet {

    private static final String AT_LEAST_ONE_HEALTH_CHECK_STATUS_IS_DOWN = "At least one health check status is DOWN";

    private static final CuiLogger log = new CuiLogger(PortalHealthServlet.class);

    private static final long serialVersionUID = -6904322706994309318L;

    /**
     * Request path for health check resulting in a JSON response containing the
     * details of each liveness and readiness check together. Returns HTTP 200 (OK)
     * if all checks pass, or HTTP 500 (Internal Service Error) if one or more fail.
     * Authentication is required! If the user is not authenticated a simplified
     * status is returned, see {@link PortalHealthServlet#REQUEST_PATH_HEALTH}.
     */
    static final String REQUEST_PATH_HEALTH = "/health";

    /**
     * Request path for liveness check resulting in a JSON response containing the
     * details of all liveness checks. Returns HTTP 200 (OK) if all checks pass, or
     * HTTP 503 (Service Unavailable) if one or more fail. Authentication is
     * required! If the user is not authenticated a simplified status is returned,
     * see {@link PortalHealthServlet#REQUEST_PATH_HEALTH}.
     */
    static final String REQUEST_PATH_LIVENESS = REQUEST_PATH_HEALTH + "/live";

    /**
     * Request path for readiness check resulting in a JSON response containing the
     * details of all readiness checks. Returns HTTP 200 (OK) if all checks pass, or
     * HTTP 503 (Service Unavailable) if one or more fail. Authentication is
     * required! If the user is not authenticated a simplified status is returned,
     * see {@link PortalHealthServlet#REQUEST_PATH_HEALTH}.
     */
    static final String REQUEST_PATH_READINESS = REQUEST_PATH_HEALTH + "/ready";

    private static final String UNKNOWN_ENDPOINT_MSG = "Portal-152: Displaying no details, wrong endpoint, expected '"
            + REQUEST_PATH_HEALTH + "', '" + REQUEST_PATH_LIVENESS + "' or '" + REQUEST_PATH_READINESS + "', but was: ";

    @Inject
    @ConfigProperty(name = PORTAL_HEALTHCHECK_ENABLED)
    private Provider<Boolean> serviceEnabledProvider;

    @Inject
    @ConfigProperty(name = PORTAL_HEALTHCHECK_HTTPCODEDOWN)
    private Provider<Integer> httpCodeDown;

    @Inject
    @ConfigProperty(name = PORTAL_HEALTHCHECK_CUSTOMOUTPUT)
    private Provider<Boolean> customOutput;

    @Inject
    @ConfigProperty(name = PORTAL_HEALTHCHECK_LOG_IN_REQUIRED)
    private Provider<Boolean> loginForDetailsRequired;

    @Inject
    @ConfigAsList(name = PORTAL_HEALTHCHECK_ROLES_REQUIRED)
    private Provider<List<String>> requiredRolesForDetails;

    @Inject
    private Provider<SmallRyeHealthReporter> healthReporterProvider;

    /**
     * <h2>Example custom output</h2>
     *
     * <pre>
     * {
     *     "status": "DOWN",
     *     "healthCheck1": {
     *         "status": "UP"
     *     },
     *     "healthCheck2": {
     *         "status": "DOWN",
     *         "foo": "bar"
     *     },
     *     "diskSpace": {
     *         "status": "UP",
     *         "total": 500105760768,
     *         "free": 423078170624,
     *         "threshold": 10485760
     *     }
     * }
     * </pre>
     *
     * <h2>Example Health MicroProfile v2 output</h2> For detailed information see:
     * <a href=
     * "https://github.com/eclipse/microprofile-health/blob/2.0.1/spec/src/main/asciidoc/protocol-wireformat.adoc">Wireformat</a>
     *
     * <pre>
     * {
     *   "status": "DOWN",
     *   "checks": [
     *     {
     *       "name": "healthCheck1",
     *       "status": "UP"
     *     },
     *     {
     *       "name": "healthCheck2",
     *       "status": "DOWN",
     *       "data": {
     *         "key": "value",
     *         "foo": "bar"
     *       }
     *     }
     *   ]
     * }
     * </pre>
     */
    @Override
    public void executeDoGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        log.debug("Evaluating Health");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

        if (Endpoint.UNKNOWN == Endpoint.evaluate(request)) {
            final var msg = UNKNOWN_ENDPOINT_MSG + request.getServletPath();
            log.warn(msg);
            response.setStatus(500);
            response.getOutputStream().print(msg);
        } else if (Boolean.TRUE.equals(customOutput.get())) {
            log.debug("Report health using custom style");
            reportIcwConform(request, response);
        } else {
            log.debug("Report health using MicroProfile style");
            reportMicroProfileConform(request, response);
        }
    }

    /**
     * <pre>
     * {
     *     "status": "DOWN",
     *     "healthCheck1": {
     *         "status": "UP"
     *     },
     *     "healthCheck2": {
     *         "status": "DOWN",
     *         "foo": "bar"
     *     },
     *     "diskSpace": {
     *         "status": "UP",
     *         "total": 500105760768,
     *         "free": 423078170624,
     *         "threshold": 10485760
     *     }
     * }
     * </pre>
     *
     * @param req
     * @param response
     *
     * @throws IOException
     */
    private void reportIcwConform(final HttpServletRequest req, final HttpServletResponse response) throws IOException {
        final var responseJson = Json.createObjectBuilder();

        final var health = evaluateChecks(Endpoint.evaluate(req));

        // evaluate global status
        if (health.isDown()) {
            log.debug(AT_LEAST_ONE_HEALTH_CHECK_STATUS_IS_DOWN);
            response.setStatus(httpCodeDown.get());
            addStatus(true, responseJson);
        } else {
            addStatus(false, responseJson);
        }

        if (shouldDisplayDetails()) {
            log.debug("Evaluating health check details");
            responseJson.addAll(MicroProfileToSpringResponseFormatMapper.apply(health));
        } // else only global status is returned

        final var json = responseJson.build().toString();
        log.debug("Health check response: {}", json);
        response.getOutputStream().print(json);
    }

    private boolean shouldDisplayDetails() {
        final var userInfo = getUserInfo();
        if (Boolean.TRUE.equals(loginForDetailsRequired.get()) && !userInfo.isAuthenticated()) {
            log.debug("Displaying no details, user should be authenticated, but is not '{}'", userInfo);
            return false;
        }
        final Collection<String> roles = requiredRolesForDetails.get();
        if (roles.isEmpty()) {
            log.debug("Displaying details, no role restriction");
            return true;
        }
        if (!userInfo.getRoles().containsAll(roles)) {
            log.debug("Displaying no details, required roles '{}', but user does not provide them '{}'", roles,
                    userInfo);
            return false;
        }
        log.debug("All checks are ok, displaying detailed data");
        return true;
    }

    private void reportMicroProfileConform(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        final var healthReporter = healthReporterProvider.get();
        final var health = evaluateChecks(Endpoint.evaluate(request));
        if (health.isDown()) {
            log.debug(AT_LEAST_ONE_HEALTH_CHECK_STATUS_IS_DOWN);
            response.setStatus(httpCodeDown.get());
        }
        if (shouldDisplayDetails()) {
            healthReporter.reportHealth(response.getOutputStream(), health);
        } else {
            final var responseJson = Json.createObjectBuilder();
            addStatus(health.isDown(), responseJson);
            response.getOutputStream().print(responseJson.build().toString());
        }
    }

    private static void addStatus(final boolean isDown, final JsonObjectBuilder builder) {
        builder.add("status",
                isDown ? HealthCheckResponse.Status.DOWN.toString() : HealthCheckResponse.Status.UP.toString());
    }

    private SmallRyeHealth evaluateChecks(final Endpoint endpoint) {
        if (Endpoint.LIVENESS == endpoint) {
            return healthReporterProvider.get().getLiveness();
        }
        if (Endpoint.READINESS == endpoint) {
            return healthReporterProvider.get().getReadiness();
        }
        return healthReporterProvider.get().getHealth();
    }

    // Consumed by de.cuioss.portal.core.api.servlet.AbstractPortalServlet
    @Override
    public boolean isEnabled() {
        return serviceEnabledProvider.get();
    }

    @Override
    public Collection<String> getRequiredRoles() {
        // The test against the configured roles is applied to the detailed information
        return Collections.emptyList();
    }

    @Override
    public boolean isLoggedInUserRequired() {
        // The test against a logged in user is applied to the detailed information
        return false;
    }

    /**
     * Representation of the requested endpoint
     */
    private enum Endpoint {
        LIVENESS, READINESS, HEALTH, UNKNOWN;

        static Endpoint evaluate(final HttpServletRequest req) {
            final var path = req.getServletPath();
            if (REQUEST_PATH_LIVENESS.equals(path)) {
                return LIVENESS;
            }
            if (REQUEST_PATH_READINESS.equals(path)) {
                return READINESS;
            }
            if (REQUEST_PATH_HEALTH.equals(path)) {
                return HEALTH;
            }
            return UNKNOWN;
        }
    }
}
