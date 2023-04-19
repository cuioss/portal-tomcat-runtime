package de.cuioss.portal.tomcat.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.core.test.mocks.microprofile.PortalTestMetricRegistry;

/**
 * @author Sven Haag
 */
class ThreadMetricsTest {

    @Test
    void shouldRegisterMetrics() {
        final MetricRegistry registry = new PortalTestMetricRegistry();
        ThreadMetrics.bindTo(registry, false);

        assertFalse(registry.getGauges().isEmpty());
        assertTrue(registry.getGauges().keySet().stream()
            .anyMatch(g -> g.getTags().containsKey("state")));
    }
}
