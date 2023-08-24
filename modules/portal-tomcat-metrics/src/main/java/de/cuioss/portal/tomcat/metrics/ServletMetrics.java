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

import static de.cuioss.tools.string.MoreStrings.nullToEmpty;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import de.cuioss.tools.logging.CuiLogger;

/**
 * A servlet filter that provides the following metrics:
 * <ul>
 * <li>A Histogram with response time distribution per context</li>
 * <li>A Gauge with the number of concurrent request per context</li>
 * <li>A Gauge with a the number of responses per context and status code</li>
 * </ul>
 *
 * Example metrics being exported:
 *
 * <pre>
 * servlet_request_concurrent_total{"/foo",} 1.0
 * servlet_response_status_total{"/foo", "200",} 1.0
 * </pre>
 *
 * <p>
 * As the {@link WebFilter} annotation has no element for order, other filters
 * could ran before.
 * </p>
 *
 * @author Sven Haag
 * @see <a href=
 *      "https://github.com/nlighten/tomcat_exporter/blob/tomcat_exporter-0.0.13/client/src/main/java/nl/nlighten/prometheus/tomcat/TomcatServletMetricsFilter.java">tomcat-exporter</a>
 */
@WebFilter(filterName = "ServletMetricsFilter", urlPatterns = "/*")
public class ServletMetrics implements Filter {

    private static final CuiLogger LOGGER = new CuiLogger(ServletMetrics.class);

    private static final int UNDEFINED_HTTP_STATUS = 999;

    @Inject
    private MetricRegistry applicationRegistry;

    @Override
    public void init(final FilterConfig filterConfig) {
        LOGGER.trace("ServletMetricsFilter initialized");
        // NOOP
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        LOGGER.debug("Processing ServletMetricsFilter");

        final var request = (HttpServletRequest) servletRequest;
        final var servletPath = getName(request);

        if (servletPath.startsWith("faces/pages/") || servletPath.equals("javax.faces.resource")) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else if (!request.isAsyncStarted()) {
            final var context = new Tag("context", getContext(request));
            final var name = new Tag("name", servletPath);

            final var servletConcurrentRequest = applicationRegistry.concurrentGauge(
                    new MetadataBuilder().withName("servlet.request.concurrent.total")
                            .withDescription("Number of concurrent requests for given context.").build(),
                    context, name);
            servletConcurrentRequest.inc();

            final var timer = applicationRegistry.timer(
                    new MetadataBuilder().withName("servlet.request").withUnit(MetricUnits.SECONDS)
                            .withDescription("The time taken fulfilling servlet requests").build(),
                    context, name, new Tag("method", request.getMethod())).time(); // start timer

            try {
                // calls the next filter in the chain. returns when servlet has been processed.
                filterChain.doFilter(servletRequest, servletResponse);
            } finally {
                timer.stop();

                servletConcurrentRequest.dec();

                applicationRegistry.concurrentGauge(
                        new MetadataBuilder().withName("servlet.response.status.total")
                                .withDescription("Number of requests for given context and status code.").build(),
                        context, name, new Tag("status", getStatusString(servletResponse))).inc();
            }
        } else {
            LOGGER.info(
                    "Async request received for Metrics servlet filter. Metrics are not evaluated for this request.");
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private String getName(final HttpServletRequest request) {
        final var name = nullToEmpty(request.getServletPath());
        if (name.startsWith("/")) {
            return name.substring(1);
        }
        return name;
    }

    private String getStatusString(final ServletResponse response) {
        try {
            return Integer.toString(((HttpServletResponse) response).getStatus());
        } catch (final Exception ex) {
            return Integer.toString(UNDEFINED_HTTP_STATUS);
        }
    }

    private String getContext(final HttpServletRequest request) {
        if (null != request.getContextPath() && !request.getContextPath().isEmpty()) {
            return request.getContextPath();
        }
        return "/";
    }

    @Override
    public void destroy() {
        // NOOP
    }
}
