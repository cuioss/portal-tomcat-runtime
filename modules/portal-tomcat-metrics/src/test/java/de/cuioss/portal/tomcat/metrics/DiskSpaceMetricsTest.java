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
