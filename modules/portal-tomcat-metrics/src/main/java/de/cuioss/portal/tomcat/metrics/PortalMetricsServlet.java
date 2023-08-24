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
import static de.cuioss.portal.configuration.MetricsConfigKeys.PORTAL_METRICS_ROLES_REQUIRED;
import static de.cuioss.tools.string.MoreStrings.nullToEmpty;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.cuioss.portal.configuration.types.ConfigAsList;
import de.cuioss.portal.core.servlet.AbstractPortalServlet;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import io.smallrye.metrics.MetricsRequestHandler;

/**
 * Entry point for displaying Metrics.
 *
 * @author Oliver Wolff
 */
@ApplicationScoped
@WebServlet(name = "PortalMetricsServlet", urlPatterns = { PortalMetricsServlet.URL_PATTERN })
public class PortalMetricsServlet extends AbstractPortalServlet {

    private static final long serialVersionUID = -5941700623068797584L;

    static final String URL_PATTERN = "/metrics/*";

    private static final CuiLogger LOGGER = new CuiLogger(PortalMetricsServlet.class);

    @Inject
    private MetricsRequestHandler metricsHandler;

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_ENABLED)
    private Provider<Boolean> serviceEnabled;

    @Inject
    @ConfigProperty(name = PORTAL_METRICS_LOG_IN_REQUIRED)
    private Provider<Boolean> loginRequired;

    @Inject
    @ConfigAsList(name = PORTAL_METRICS_ROLES_REQUIRED)
    private Provider<List<String>> requiredRoles;

    @Override
    public void executeDoGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final var requestPath = "/metrics" + nullToEmpty(request.getPathInfo());
        final var method = request.getMethod();
        final List<String> acceptHeaderList = Collections.list(request.getHeaders("Accept"));

        final Map<String, String> writtenHeader = new HashMap<>(0);
        LOGGER.debug("Extracted call with requestPath='{}', method='{}', acceptHeader='{}'", requestPath, method,
                acceptHeaderList);

        metricsHandler.handleRequest(requestPath, method, acceptHeaderList.stream(), (status, message, headers) -> {
            writtenHeader.putAll(headers);
            response.setStatus(status);
            response.getWriter().write(message);
        });
        // Fix cors header: '*' does not work on modern browser
        final var origin = request.getHeader("Origin");
        if (!MoreStrings.isEmpty(origin)) {
            // Mirror calling view
            writtenHeader.put("Access-Control-Allow-Origin", origin);
        }
        writtenHeader.forEach(response::addHeader);
    }

    @Override
    protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) {
        if (!checkAccess(resp)) {
            return;
        }
        try {
            executeDoGet(req, resp);
        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e, PORTAL_523, "Runtime Exception");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isEnabled() {
        return serviceEnabled.get();
    }

    @Override
    public boolean isLoggedInUserRequired() {
        return loginRequired.get();
    }

    @Override
    public Collection<String> getRequiredRoles() {
        return requiredRoles.get();
    }
}
