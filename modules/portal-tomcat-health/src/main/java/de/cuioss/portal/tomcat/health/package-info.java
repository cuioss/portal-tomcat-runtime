/**
 * <h1>ICW conform health check service.</h1>
 *
 * See: <a href="https://wiki.icw.int/display/PXS/Application+Monitoring">Application+Monitoring</a>
 *
 * <h1>Endpoints</h1>
 * <h2>ping</h2>
 * <p>
 * Authentication necessary: no
 * </p>
 * <p>
 * Responds to GET requests with a 200 OK response of pong. This is useful for determining liveness
 * for load balancing, etc.
 * </p>
 * <h2>status</h2>
 * <p>
 * Authentication necessary: no
 * </p>
 * <p>
 * Special health check that aggregates the health checks into a single and simple health check.
 * Boils down to status UP or DOWN
 * </p>
 * <h2>health</h2>
 * <p>
 * Authentication necessary: yes
 * </p>
 * <p>
 * Responds to GET requests by running all the health checks, 200 OK if all pass, or 500 Internal
 * Service Error if one or more fail.
 * </p>
 * <p>
 * If the request is unauthenticated it falls back to a <code>status</code> request.
 * </p>
 * <p>
 * If
 * {@linkplain de.cuioss.portal.configuration.HealthCheckConfigKeys#PORTAL_HEALTHCHECK_ENABLED}
 * is set to disabled,
 * the configured
 * {@linkplain de.cuioss.portal.configuration.HealthCheckConfigKeys#PORTAL_HEALTHCHECK_HTTPCODEDOWN}
 * HTTP status code is returned.
 * </p>
 *
 * <h1>Response example</h1>
 * <p>
 * The overall status is merged from all health check responses. If one health check is DOWN the
 * overall status is DOWN.
 * </p>
 * <ul>
 * <li>If UP the HTTP status code is 200</li>
 * <li>If DOWN the HTTP status code is 500</li>
 * </ul>
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
 * @author Sven Haag
 */
package de.cuioss.portal.tomcat.health;
