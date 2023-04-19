package de.cuioss.portal.tomcat.metrics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import de.cuioss.portal.core.test.mocks.microprofile.PortalTestMetricRegistry;

/**
 * @author Sven Haag
 */
class DiskSpaceMetricsTest {

    @Test
    void shouldRegisterMetrics() {
        final var registry = new PortalTestMetricRegistry();
        new DiskSpaceMetrics(new File(System.getProperty("user.dir")), true).bindTo(registry);

        assertTrue(registry.getMetric("disk.free").isPresent());
        assertTrue(registry.getTags("disk.free").get().size() > 0);

        assertTrue(registry.getMetric("disk.total").isPresent());
        assertTrue(registry.getTags("disk.total").get().size() > 0);

        assertNotNull(registry.getGauges().firstKey().getTags().get("path"));
    }
}
