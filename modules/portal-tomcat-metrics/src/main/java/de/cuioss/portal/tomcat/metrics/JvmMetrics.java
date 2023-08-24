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

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * @author Sven Haag
 */
@UtilityClass
final class JvmMetrics {

    static void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        memoryMetrics(registry, micrometerFormat);
        memoryPoolMetrics(registry, micrometerFormat);
        bufferPoolMetrics(registry, micrometerFormat);
    }

    private static void bufferPoolMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        for (final BufferPoolMXBean bufferPoolBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            final var tags = new Tag[] { new Tag("id", bufferPoolBean.getName()),
                    new Tag("name", bufferPoolBean.getName()) };

            registry.register(
                    new ExtendedMetadataBuilder().withName(micrometerFormat ? "jvm.buffer.count" : "buffer.count")
                            .withType(MetricType.GAUGE).withUnit("buffers").multi(true)
                            .withDescription("An estimate of the number of buffers in the pool")
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) bufferPoolBean::getCount, tags);

            registry.register(new ExtendedMetadataBuilder()
                    .withName(micrometerFormat ? "jvm.buffer.memory.used" : "buffer.usedMemory")
                    .withDisplayName("BufferPool Memory Used").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                    .multi(true)
                    .withDescription(
                            "An estimate of the memory that the Java Virtual Machine is using for this buffer pool")
                    .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) bufferPoolBean::getMemoryUsed, tags);

            registry.register(
                    new ExtendedMetadataBuilder()
                            .withName(micrometerFormat ? "jvm.buffer.total.capacity" : "buffer.totalMemory")
                            .withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES).multi(true)
                            .withDescription("An estimate of the total capacity of the buffers in this pool")
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) bufferPoolBean::getTotalCapacity, tags);
        }
    }

    private static void memoryPoolMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        for (final MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            final var area = MemoryType.HEAP.equals(memoryPoolMXBean.getType()) ? "heap" : "nonheap";

            final Tag[] tags;
            if (micrometerFormat) {
                tags = new Tag[] { new Tag("id", memoryPoolMXBean.getName()), new Tag("area", area) };
            } else {
                tags = new Tag[] { new Tag("name", memoryPoolMXBean.getName()) };
            }

            registry.register(
                    new ExtendedMetadataBuilder().withName(micrometerFormat ? "jvm.memory.used" : "memory.usage")
                            .withDisplayName("MemoryPool Usage Used").withType(MetricType.GAUGE)
                            .withUnit(MetricUnits.BYTES).withDescription("The amount of used memory")
                            .multi(micrometerFormat).skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) () -> memoryPoolMXBean.getUsage().getUsed(), tags);

            registry.register(new ExtendedMetadataBuilder()
                    .withName(micrometerFormat ? "jvm.memory.committed" : "memory.committed")
                    .withDisplayName("MemoryPool Usage Committed").withType(MetricType.GAUGE)
                    .withUnit(MetricUnits.BYTES)
                    .withDescription(
                            "The amount of memory in bytes that is committed for the Java Virtual Machine to use")
                    .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) () -> memoryPoolMXBean.getUsage().getCommitted(), tags);

            registry.register(
                    new ExtendedMetadataBuilder().withName(micrometerFormat ? "jvm.memory.max" : "memory.max")
                            .withDisplayName("MemoryPool Usage Max").withType(MetricType.GAUGE)
                            .withUnit(MetricUnits.BYTES)
                            .withDescription(
                                    "The maximum amount of memory in bytes that can be used for memory management")
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) () -> memoryPoolMXBean.getUsage().getMax(), tags);

            registry.register(
                    new ExtendedMetadataBuilder().withName(micrometerFormat ? "jvm.memory.maxUsage" : "memory.maxUsage")
                            .withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                            .withDisplayName("MemoryPool PeakUsage Used")
                            .withDescription("Peak usage of the memory pool.")
                            .skipsScopeInOpenMetricsExportCompletely(micrometerFormat).build(),
                    (Gauge) () -> memoryPoolMXBean.getPeakUsage().getUsed(), tags);
        }
    }

    private static void memoryMetrics(final MetricRegistry registry, final boolean micrometerFormat) {
        if (micrometerFormat) {
            // these metrics are not present in Micrometer
            return;
        }

        final var memoryMXBean = ManagementFactory.getMemoryMXBean();

        registry.register(Metadata.builder()
                // required by MP spec
                .withName("memory.committedHeap").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                .withDisplayName("Committed Heap Memory").withDescription("""
                        Displays the amount of memory in bytes that is committed for the Java Virtual Machine to use. \
                        This amount of memory is guaranteed for the Java virtual machine to use.\
                        """).build(), (Gauge) () -> memoryMXBean.getHeapMemoryUsage().getCommitted());

        registry.register(Metadata.builder()
                // required by MP spec
                .withName("memory.maxHeap").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                .withDisplayName("Max Heap Memory")
                .withDescription(
                        """
                                Displays the maximum amount of heap memory in bytes that can be used for memory\s\
                                management. This attribute displays -1 if the maximum heap memory size is undefined.\s\
                                This amount of memory is not guaranteed to be available for memory management if it is greater\s\
                                than the amount of committed memory. The Java virtual machine may fail to allocate memory even\s\
                                if the amount of used memory does not exceed this maximum size.""")
                .build(), (Gauge) () -> memoryMXBean.getHeapMemoryUsage().getMax());

        registry.register(Metadata.builder()
                // required by MP spec
                .withName("memory.usedHeap").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                .withDisplayName("Used Heap Memory")
                .withDescription("Displays the amount of used heap memory in bytes.").build(),
                (Gauge) () -> memoryMXBean.getHeapMemoryUsage().getUsed());

        registry.register(Metadata.builder().withName("memory.committedNonHeap").withType(MetricType.GAUGE)
                .withUnit(MetricUnits.BYTES).withDisplayName("Committed Non Heap Memory").withDescription("""
                        Displays the amount of non heap memory in bytes that is committed for the \
                        Java virtual machine to use.\
                        """).build(), (Gauge) () -> memoryMXBean.getNonHeapMemoryUsage().getCommitted());

        registry.register(
                Metadata.builder().withName("memory.maxNonHeap").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                        .withDisplayName("Max Non Heap Memory")
                        .withDescription("Displays the maximum amount of used non-heap memory in bytes.").build(),
                (Gauge) () -> memoryMXBean.getNonHeapMemoryUsage().getMax());

        registry.register(
                Metadata.builder().withName("memory.usedNonHeap").withType(MetricType.GAUGE).withUnit(MetricUnits.BYTES)
                        .withDisplayName("Used Non Heap Memory")
                        .withDescription("Displays the amount of used non-heap memory in bytes.").build(),
                (Gauge) () -> memoryMXBean.getNonHeapMemoryUsage().getUsed());
    }
}
