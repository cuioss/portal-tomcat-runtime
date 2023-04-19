package de.cuioss.portal.tomcat.metrics;

import static de.cuioss.tools.collect.CollectionLiterals.immutableSet;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.easymock.EasyMock;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.core.test.mocks.microprofile.PortalTestMetricRegistry;
import de.cuioss.portal.tomcat.metrics.TomcatMetrics;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.tools.collect.MapBuilder;
import de.cuioss.tools.reflect.FieldWrapper;
import de.cuioss.tools.reflect.MoreReflection;

class TomcatMetricsTest {

    private static final TypedGenerator<Integer> INTEGERS = Generators.integers(0, Integer.MAX_VALUE);
    private static final TypedGenerator<Long> LONGS = Generators.longs(0, Long.MAX_VALUE);

    private MBeanServer mBeanServerMock;
    private ObjectName objectName;

    @BeforeEach
    void initMBeanServer() {
        mBeanServerMock = EasyMock.createMock(MBeanServer.class);
        objectName = null;
    }

    @Test
    void shouldRegisterMicroProfileMetrics() throws MalformedObjectNameException, ReflectionException,
        AttributeNotFoundException, InstanceNotFoundException, MBeanException {

        final MetricRegistry registry = new PortalTestMetricRegistry();
        var tomcatMetrics = new TomcatMetrics();

        MBeanServer mBeanServerMock = EasyMock.createMock(MBeanServer.class);
        objectName = new ObjectName("objName1",
                new Hashtable<>(MapBuilder.from(
                        "host", "hostname",
                        "context", "contextname",
                        "name", "servletName")
                        .toImmutableMap()));

        EasyMock
                .expect(mBeanServerMock.queryMBeans(EasyMock.isA(ObjectName.class), EasyMock.isNull()))
                .andReturn(immutableSet(new ObjectInstance(objectName, "ClazzName1")))
                .anyTimes();
        registerTestMetrics();
        EasyMock.replay(mBeanServerMock);

        new FieldWrapper(MoreReflection
                .accessField(TomcatMetrics.class, "server")
                .orElseThrow(() -> new IllegalStateException("cannot access TomcatMetrics field 'server'")))
                        .writeValue(tomcatMetrics, mBeanServerMock);

        tomcatMetrics.bindTo(registry, false);

        EasyMock.verify(mBeanServerMock);

        Set<String> metricNames = registry.getNames();

        assertTrue(metricNames.contains("tomcat.sessions.active.current.sessions"));
        assertTrue(metricNames.contains("tomcat.sessions.rejected.sessions"));
        assertTrue(metricNames.contains("tomcat.sessions.created.sessions"));
        assertTrue(metricNames.contains("tomcat.sessions.expired.total"));
        assertTrue(metricNames.contains("tomcat.sessions.alive.average"));
        assertTrue(metricNames.contains("tomcat.sessions.alive.max"));
        assertTrue(metricNames.contains("tomcat.sessions.expireRate"));
        assertTrue(metricNames.contains("tomcat.sessions.active.max.sessions"));
        assertTrue(metricNames.contains("tomcat.context.state.started"));

        assertTrue(metricNames.contains("tomcat.servlet.errorCount"));
        assertTrue(metricNames.contains("tomcat.servlet.requestCount"));
        assertTrue(metricNames.contains("tomcat.servlet.requestMaxTime"));
        assertTrue(metricNames.contains("tomcat.servlet.processingTime"));
        assertTrue(metricNames.contains("tomcat.servlet.loadTime"));

        assertTrue(metricNames.contains("tomcat.global.received.bytes"));
        assertTrue(metricNames.contains("tomcat.global.sent.bytes"));
        assertTrue(metricNames.contains("tomcat.global.processingTime"));
        assertTrue(metricNames.contains("tomcat.global.errorCount"));
        assertTrue(metricNames.contains("tomcat.global.requestCount"));
        assertTrue(metricNames.contains("tomcat.global.max"));

        assertTrue(metricNames.contains("threadpool.activeThreads"));
        assertTrue(metricNames.contains("threadpool.busyThreads"));
        assertTrue(metricNames.contains("threadpool.size"));
        assertTrue(metricNames.contains("threadpool.activeConnections"));
        assertTrue(metricNames.contains("threadpool.maxConnections"));
    }

    private void registerTestMetrics()
        throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {

        // SessionMetrics
        expectServerAttribute("activeSessions", INTEGERS.next());
        expectServerAttribute("rejectedSessions", INTEGERS.next());
        expectServerAttribute("sessionCounter", LONGS.next());
        expectServerAttribute("expiredSessions", LONGS.next());
        expectServerAttribute("sessionAverageAliveTime", INTEGERS.next());
        expectServerAttribute("sessionMaxAliveTime", INTEGERS.next());
        expectServerAttribute("sessionExpireRate", INTEGERS.next());
        expectServerAttribute("maxActive", INTEGERS.next());
        expectServerAttribute("stateName", Generators.booleans().next()
                ? "STARTED"
                : Generators.strings().next());

        // ServletMetrics
        expectServerAttribute("errorCount", INTEGERS.next());
        expectServerAttribute("requestCount", INTEGERS.next());
        expectServerAttribute("maxTime", LONGS.next());
        expectServerAttribute("processingTime", LONGS.next());
        expectServerAttribute("loadTime", LONGS.next());

        // RequestProcessorMetrics
        expectServerAttribute("bytesReceived", LONGS.next());
        expectServerAttribute("bytesSent", LONGS.next());
        expectServerAttribute("processingTime", LONGS.next());
        expectServerAttribute("errorCount", INTEGERS.next());
        expectServerAttribute("requestCount", INTEGERS.next());
        expectServerAttribute("maxTime", LONGS.next());

        // ThreadPoolMetrics
        expectServerAttribute("currentThreadCount", INTEGERS.next());
        expectServerAttribute("currentThreadsBusy", INTEGERS.next());
        expectServerAttribute("maxThreads", INTEGERS.next());
        expectServerAttribute("connectionCount", LONGS.next());
        expectServerAttribute("maxConnections", INTEGERS.next());
    }

    private void expectServerAttribute(final String attribute, final Object returnValue)
        throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        EasyMock
                .expect(mBeanServerMock.getAttribute(EasyMock.eq(objectName), EasyMock.eq(attribute)))
                .andReturn(returnValue);
    }
}
