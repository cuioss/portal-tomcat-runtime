package de.cuioss.portal.tomcat.metrics;

import static de.cuioss.portal.metrics.RegistryHelper.THREADS_SUFFIX;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Callable;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.metrics.ExtendedMetadataBuilder;

/**
 * TODO
 * tomcat_servlet_request_seconds summary
 * <p>
 *
 * @author Sven Haag
 * @see
 *      <a href=
 *      "https://github.com/nlighten/tomcat_exporter/blob/tomcat_exporter-0.0.13/client/src/main/java/nl/nlighten/prometheus/tomcat/TomcatGenericExports.java">tomcat-exporter</a>
 */
class TomcatMetrics {

    private static final CuiLogger LOGGER = new CuiLogger(TomcatMetrics.class);

    private static final String ERROR_MSG = "Portal-535: Error retrieving metric.";
    private static final String DEBUG_MSG_ADDING_MBEAN = "Adding metrics for mbean: {}";
    private static final String JMX_DOMAIN = "Catalina";

    private final MBeanServer server;

    TomcatMetrics() {
        server = ManagementFactory.getPlatformMBeanServer();
    }

    public void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        addSessionMetrics(registry, micrometerFormat);
        addServletMetrics(registry, micrometerFormat);
        addThreadPoolMetrics(registry, micrometerFormat);
        addRequestProcessorMetrics(registry, micrometerFormat);
    }

    private void addSessionMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        try {
            final var filterName = new ObjectName(JMX_DOMAIN + ":type=Manager,context=*,host=*");
            final var mBeans = server.queryMBeans(filterName, null);

            for (final ObjectInstance mBean : mBeans) {
                final var objectName = mBean.getObjectName();
                LOGGER.debug(DEBUG_MSG_ADDING_MBEAN, objectName);
                final var hostTag = new Tag("host", objectName.getKeyProperty("host"));
                final var contextTag = new Tag("context", objectName.getKeyProperty("context"));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.active.current.sessions")
                                .withType(MetricType.GAUGE)
                                .withDescription("Number of active sessions at this moment")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "activeSessions")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.rejected.sessions")
                                .withType(MetricType.COUNTER)
                                .withDescription("Number of sessions rejected due to maxActive being reached")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        new SafeGetCountOnlyCounter() {

                            @Override
                            public Callable<Number> getValueProvider() {
                                return () -> (Integer) server.getAttribute(objectName, "rejectedSessions");
                            }
                        },
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.created.sessions")
                                .withType(MetricType.COUNTER)
                                .withDescription("Total number of sessions created by this manager")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        new SafeGetCountOnlyCounter() {

                            @Override
                            public Callable<Number> getValueProvider() {
                                return () -> (Long) server.getAttribute(objectName, "sessionCounter");
                            }
                        },
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.expired.total")
                                .withType(MetricType.GAUGE)
                                .withDescription(
                                        "Number of sessions that expired (doesn't include explicit invalidations)")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "expiredSessions")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.alive.average")
                                .withType(MetricType.GAUGE)
                                .withUnit(MetricUnits.SECONDS)
                                .withDescription("Average time an expired session had been alive")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "sessionAverageAliveTime")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.alive.max")
                                .withType(MetricType.GAUGE)
                                .withUnit(MetricUnits.SECONDS)
                                .withDescription("Maximum time an expired session had been alive")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "sessionMaxAliveTime")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.expireRate")
                                .withType(MetricType.GAUGE)
                                .withDescription("Session expiration rate in sessions per minute")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "sessionExpireRate")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.sessions.active.max.sessions")
                                .withType(MetricType.GAUGE)
                                .withDescription("Maximum number of active sessions so far")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "maxActive")),
                        hostTag, contextTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.context.state.started")
                                .withType(MetricType.GAUGE)
                                .withDescription("Indication if the lifecycle state of this context is STARTED")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "stateName")
                                .equals("STARTED") ? 1 : 0),
                        hostTag, contextTag);
            }
        } catch (final Exception e) {
            LOGGER.error(ERROR_MSG, e);
        }
    }

    private void addServletMetrics(final MetricRegistry registry,
            final boolean micrometerFormat) {
        try {
            final var filterName = new ObjectName(
                JMX_DOMAIN + ":j2eeType=Servlet,WebModule=*,J2EEApplication=*,J2EEServer=*,name=*");
            final var mBeans = server.queryMBeans(filterName, null);

            for (final ObjectInstance mBean : mBeans) {
                final var objectName = mBean.getObjectName();
                LOGGER.debug(DEBUG_MSG_ADDING_MBEAN, objectName);
                final var servletName = objectName.getKeyProperty("name");

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.servlet.errorCount")
                                .withType(MetricType.GAUGE)
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "errorCount")),
                        new Tag("name", servletName));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.servlet.requestCount")
                                .withType(MetricType.GAUGE)
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "requestCount")),
                        new Tag("name", servletName));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.servlet.requestMaxTime")
                                .withDescription("Maximum processing time of a request")
                                .withType(MetricType.GAUGE)
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "maxTime")),
                        new Tag("name", servletName));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.servlet.processingTime")
                                .withDescription("Total execution time of the servlet's service method")
                                .withType(MetricType.GAUGE)
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "processingTime")),
                        new Tag("name", servletName));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.servlet.loadTime")
                                .withDescription("Time taken to load and initialise the Servlet")
                                .withType(MetricType.GAUGE)
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "loadTime")),
                        new Tag("name", servletName));
            }
        } catch (final Exception e) {
            LOGGER.error(ERROR_MSG, e);
        }
    }

    private void addRequestProcessorMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        try {
            final var filterName = new ObjectName(JMX_DOMAIN + ":type=GlobalRequestProcessor,name=*");
            final var mBeans = server.queryMBeans(filterName, null);

            for (final ObjectInstance mBean : mBeans) {
                final var objectName = mBean.getObjectName();
                LOGGER.debug(DEBUG_MSG_ADDING_MBEAN, objectName);
                final var nameTag = new Tag("name", objectName.getKeyProperty("name")
                        .replaceAll("[\"\\\\]", ""));

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.received.bytes")
                                .withType(MetricType.COUNTER)
                                .withUnit(MetricUnits.BYTES)
                                .withDescription("Number of bytes received by this request processor")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .withOpenMetricsKeyOverride(
                                        micrometerFormat ? "tomcat_global_received_bytes_total" : null)
                                .build(),
                        new SafeGetCountOnlyCounter() {

                            @Override
                            public Callable<Number> getValueProvider() {
                                return () -> (Long) server.getAttribute(objectName, "bytesReceived");
                            }
                        },
                        nameTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.sent.bytes")
                                .withType(MetricType.COUNTER)
                                .withUnit(MetricUnits.BYTES)
                                .withDescription("Number of bytes sent by this request processor")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .withOpenMetricsKeyOverride(micrometerFormat ? "tomcat_global_sent_bytes_total" : null)
                                .build(),
                        new SafeGetCountOnlyCounter() {

                            @Override
                            public Callable<Number> getValueProvider() {
                                return () -> (Long) server.getAttribute(objectName, "bytesSent");
                            }
                        },
                        nameTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.processingTime")
                                .withType(MetricType.GAUGE)
                                .withUnit(MetricUnits.SECONDS)
                                .withDescription("The total time spend by this request processor")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "processingTime")),
                        nameTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.errorCount")
                                .withType(MetricType.GAUGE)
                                .withDescription("The number of error request served by this request processor")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "errorCount")),
                        nameTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.requestCount")
                                .withType(MetricType.GAUGE)
                                .withDescription("The number of requests served by this request processor")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.intGauge(() -> server.getAttribute(objectName, "requestCount")),
                        nameTag);

                registry.register(
                        new ExtendedMetadataBuilder()
                                .withName("tomcat.global.max")
                                .withType(MetricType.GAUGE)
                                .withUnit(MetricUnits.MILLISECONDS)
                                // .multi(true)
                                .withDescription("Tomcat GlobalRequestProcessor Max Time")
                                .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                                .build(),
                        SafeGauge.longGauge(() -> server.getAttribute(objectName, "maxTime")),
                        nameTag);
            }
        } catch (final Exception e) {
            LOGGER.error(ERROR_MSG, e);
        }
    }

    private void addThreadPoolMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        try {
            final var filterName = new ObjectName(JMX_DOMAIN + ":type=ThreadPool,name=*");
            final var mBeans = server.queryMBeans(filterName, null);

            for (final ObjectInstance mBean : mBeans) {
                final var objectName = mBean.getObjectName();
                LOGGER.debug(DEBUG_MSG_ADDING_MBEAN, objectName);
                final var threadPoolName = objectName.getKeyProperty("name").replaceAll("[\"\\\\]", "");
                final var nameTag = new Tag(micrometerFormat ? "name" : "pool", threadPoolName);

                registerThreadCurrent(registry, micrometerFormat, objectName, nameTag);

                registerThreadBusy(registry, micrometerFormat, objectName, nameTag);

                registerThredsConfigMax(registry, micrometerFormat, objectName, nameTag);

                registerThreadsConnectionsCurrent(registry, micrometerFormat, objectName, threadPoolName, nameTag);

                registerThreadsConnectionsMax(registry, micrometerFormat, objectName, threadPoolName, nameTag);
            }
        } catch (final Exception e) {
            LOGGER.error(ERROR_MSG, e);
        }
    }

    private void registerThreadsConnectionsMax(final MetricRegistry registry, final boolean micrometerFormat,
            final ObjectName objectName, final String threadPoolName, final Tag nameTag) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerFormat ? "tomcat.threads.connections.max"
                                : "threadpool.maxConnections")
                        .withType(MetricType.GAUGE)
                        .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                        .withDescription("Maximum number of concurrent connections served by this pool.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                SafeGauge.intGauge(() -> server.getAttribute(objectName, "maxConnections")),
                nameTag, new Tag("pool", threadPoolName));
    }

    private void registerThreadsConnectionsCurrent(final MetricRegistry registry, final boolean micrometerFormat,
            final ObjectName objectName, final String threadPoolName, final Tag nameTag) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerFormat ? "tomcat.threads.connections.current"
                                : "threadpool.activeConnections")
                        .withType(MetricType.GAUGE)
                        .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                        .withDescription("Number of connections served by this pool.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                SafeGauge.longGauge(() -> server.getAttribute(objectName, "connectionCount")),
                nameTag, new Tag("pool", threadPoolName));
    }

    private void registerThredsConfigMax(final MetricRegistry registry, final boolean micrometerFormat,
            final ObjectName objectName, final Tag nameTag) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerFormat ? "tomcat.threads.config.max" : "threadpool.size")
                        .withType(MetricType.GAUGE)
                        .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                        .withDescription("Maximum number of threads allowed in this pool.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                SafeGauge.intGauge(() -> server.getAttribute(objectName, "maxThreads")),
                nameTag);
    }

    private void registerThreadBusy(final MetricRegistry registry, final boolean micrometerFormat,
            final ObjectName objectName, final Tag nameTag) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerFormat ? "tomcat.threads.busy" : "threadpool.busyThreads")
                        .withType(MetricType.GAUGE)
                        .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                        .withDescription("Number of busy threads in this pool.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                SafeGauge.intGauge(() -> server.getAttribute(objectName, "currentThreadsBusy")),
                nameTag);
    }

    private void registerThreadCurrent(final MetricRegistry registry, final boolean micrometerFormat,
            final ObjectName objectName, final Tag nameTag) {
        registry.register(
                new ExtendedMetadataBuilder()
                        .withName(micrometerFormat ? "tomcat.threads.current" : "threadpool.activeThreads")
                        .withType(MetricType.GAUGE)
                        .withUnit(micrometerFormat ? THREADS_SUFFIX : MetricUnits.NONE)
                        .withDescription("Number threads in this pool.")
                        .skipsScopeInOpenMetricsExportCompletely(micrometerFormat)
                        .build(),
                SafeGauge.intGauge(() -> server.getAttribute(objectName, "currentThreadCount")),
                nameTag);
    }
}
