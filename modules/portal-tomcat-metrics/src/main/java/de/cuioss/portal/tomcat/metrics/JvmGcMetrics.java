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

import java.io.Closeable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import com.sun.management.GarbageCollectionNotificationInfo;

import de.cuioss.tools.collect.MapBuilder;
import de.cuioss.tools.logging.CuiLogger;
import io.smallrye.metrics.ExtendedMetadataBuilder;

/**
 * @author Sven Haag
 * @see <a href=
 *      "https://github.com/micrometer-metrics/micrometer/blob/v1.3.5/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/jvm/JvmGcMetrics.java">micrometer-metrics</a>
 * @see <a href=
 *      "https://github.com/quarkusio/quarkus/blob/master/extensions/smallrye-metrics/runtime/src/main/java/io/quarkus/smallrye/metrics/runtime/MicrometerGCMetrics.java">Quarkus
 *      IO MicrometerGCMetrics</a>
 */
@SuppressWarnings("squid:S1191")
class JvmGcMetrics implements Closeable {

    private static final String TIME_SPENT_IN_GC_PAUSE = "Time spent in GC pause";

    private static final CuiLogger LOGGER = new CuiLogger(JvmGcMetrics.class);

    private String youngGenPoolName;
    private String oldGenPoolName;

    // jvm.gc.live.data.size metric
    private final AtomicLong liveDataSize = new AtomicLong(0);
    // jvm.gc.max.data.size metric
    private final AtomicLong maxDataSize = new AtomicLong(0);
    // jvm.gc.memory.promoted metric
    private final AtomicLong promotedBytes = new AtomicLong(0);
    // jvm.gc.memory.allocated metric
    private final AtomicLong allocatedBytes = new AtomicLong(0);

    // Mimicking the jvm.gc.pause timer. We don't have an exact equivalent of
    // Micrometer's timer, so
    // emulate
    // it with one gauge and two counters.
    // We use a wrapper class to wrap the 'cause' and 'action' fields of GC event
    // descriptors into
    // one class
    // We defer registering these metrics to runtime, because we don't assume we
    // know in advance the
    // full set of
    // causes and actions

    static class CauseAndActionWrapper {

        private final String cause;
        private final String action;

        public CauseAndActionWrapper(final String cause, final String action) {
            this.cause = cause;
            this.action = action;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (CauseAndActionWrapper) o;
            return Objects.equals(cause, that.cause) && Objects.equals(action, that.action);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cause, action);
        }
    }

    // keeps track of maximum gc pause lengths for a given GC cause and action
    private final Map<CauseAndActionWrapper, AtomicLong> gcPauseMax = new HashMap<>();

    // and the same for concurrent GC phases
    private final Map<CauseAndActionWrapper, AtomicLong> gcPauseMaxConcurrent = new HashMap<>();

    // To keep track of notification listeners that we register so we can clean them
    // up later
    private final Map<NotificationEmitter, NotificationListener> notificationEmitters = new HashMap<>();

    private final boolean micrometerCompatibility;

    JvmGcMetrics(final boolean micrometerCompatibility) {
        this.micrometerCompatibility = micrometerCompatibility;
        for (final MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            final var name = mbean.getName();
            if (isYoungGenPool(name)) {
                youngGenPoolName = name;
            } else if (isOldGenPool(name)) {
                oldGenPoolName = name;
            }
        }
    }

    void bindTo(final MetricRegistry registry) {
        if (!isManagementExtensionsPresent()) {
            return;
        }

        registry.register(new ExtendedMetadataBuilder().withName("jvm.gc.max.data.size").withType(MetricType.GAUGE)
                .withUnit(MetricUnits.BYTES).withDescription("Max size of old generation memory pool")
                .skipsScopeInOpenMetricsExportCompletely(true).build(), (Gauge<?>) this::getMaxDataSize);

        registry.register(new ExtendedMetadataBuilder().withName("jvm.gc.live.data.size").withType(MetricType.GAUGE)
                .withUnit(MetricUnits.BYTES).withDescription("Size of old generation memory pool after a full GC")
                .skipsScopeInOpenMetricsExportCompletely(true).build(), (Gauge<?>) this::getLiveDataSize);

        registry.register(new ExtendedMetadataBuilder().withName("jvm.gc.memory.promoted").withType(MetricType.COUNTER)
                .withUnit(MetricUnits.BYTES)
                .withDescription(
                        "Count of positive increases in the size of the old generation memory pool before GC to after GC")
                .skipsScopeInOpenMetricsExportCompletely(true)
                .withOpenMetricsKeyOverride("jvm_gc_memory_promoted_bytes_total").build(), new GetCountOnlyCounter() {

                    @Override
                    public long getCount() {
                        return getPromotedBytes();
                    }
                });

        registry.register(
                new ExtendedMetadataBuilder().withName("jvm.gc.memory.allocated").withType(MetricType.COUNTER)
                        .withUnit(MetricUnits.BYTES)
                        .withDescription(
                                """
                                        Incremented for an increase in the size of the young generation memory pool after one GC to \
                                        before the next\
                                        """)
                        .skipsScopeInOpenMetricsExportCompletely(true)
                        .withOpenMetricsKeyOverride("jvm_gc_memory_allocated_bytes_total").build(),
                new GetCountOnlyCounter() {

                    @Override
                    public long getCount() {
                        return getAllocatedBytes();
                    }
                });

        // start updating the metric values in a listener for GC events
        // Metrics that mimic the jvm.gc.pause timer will be registered lazily as GC
        // events occur
        startWatchingNotifications(registry);
    }

    public Long getLiveDataSize() {
        return liveDataSize.get();
    }

    public Long getMaxDataSize() {
        return maxDataSize.get();
    }

    public Long getPromotedBytes() {
        return promotedBytes.get();
    }

    public Long getAllocatedBytes() {
        return allocatedBytes.get();
    }

    private void startWatchingNotifications(final MetricRegistry registry) {
        final var youngGenSizeAfter = new AtomicLong(0L);

        for (final GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {

            if (!micrometerCompatibility) {
                // required MP metrics
                registerGCTime(registry, mbean);

                registerGCTotal(registry, mbean);
            }

            if (!(mbean instanceof NotificationEmitter)) {
                continue;
            }

            final NotificationListener notificationListener = (notification,
                    ref) -> handleNotificationListener(registry, youngGenSizeAfter, notification);
            final var notificationEmitter = (NotificationEmitter) mbean;
            notificationEmitter.addNotificationListener(notificationListener, null, null);
            notificationEmitters.put(notificationEmitter, notificationListener);
        }
    }

    private void handleNotificationListener(final MetricRegistry registry, final AtomicLong youngGenSizeAfter,
            Notification notification) {
        if (!notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            return;
        }
        final var cd = (CompositeData) notification.getUserData();
        final var notificationInfo = GarbageCollectionNotificationInfo.from(cd);

        final var gcCause = notificationInfo.getGcCause();
        final var gcAction = notificationInfo.getGcAction();
        final var gcInfo = notificationInfo.getGcInfo();
        final var duration = gcInfo.getDuration();

        final var metricName = isConcurrentPhase(gcCause) ? "jvm.gc.concurrent.phase.time" : "jvm.gc.pause";
        final var mapForStoringMax = isConcurrentPhase(gcCause) ? gcPauseMax : gcPauseMaxConcurrent;

        final var tags = new Tag[] { new Tag("action", gcAction), new Tag("cause", gcCause) };
        final var causeAndAction = new CauseAndActionWrapper(gcCause, gcAction);

        final var pauseSecondsMaxMetricID = new MetricID(metricName + ".seconds.max", tags);
        final var gcPauseMaxValue = mapForStoringMax.computeIfAbsent(causeAndAction, k -> new AtomicLong(0));
        if (duration > gcPauseMaxValue.get()) {
            gcPauseMaxValue.set(duration); // update the maximum GC length if needed
        }
        if (!registry.getGauges().containsKey(pauseSecondsMaxMetricID)) {
            registry.register(
                    new ExtendedMetadataBuilder().withName(metricName + ".seconds.max").withType(MetricType.GAUGE)
                            .withUnit(MetricUnits.NONE).withDescription(TIME_SPENT_IN_GC_PAUSE)
                            .skipsScopeInOpenMetricsExportCompletely(true).build(),
                    (Gauge<?>) () -> mapForStoringMax.get(causeAndAction).doubleValue() / 1000.0, tags);
        }

        registry.counter(new ExtendedMetadataBuilder().withName(metricName + ".seconds.count")
                .withType(MetricType.COUNTER).withUnit(MetricUnits.NONE).withDescription(TIME_SPENT_IN_GC_PAUSE)
                .skipsScopeInOpenMetricsExportCompletely(true)
                .withOpenMetricsKeyOverride(metricName.replace(".", "_") + "_seconds_count").build(), tags).inc();

        registry.counter(new ExtendedMetadataBuilder().withName(metricName + ".seconds.sum")
                .withType(MetricType.COUNTER).withUnit(MetricUnits.MILLISECONDS).withDescription(TIME_SPENT_IN_GC_PAUSE)
                .skipsScopeInOpenMetricsExportCompletely(true)
                .withOpenMetricsKeyOverride(metricName.replace(".", "_") + "_seconds_sum").build(), tags).inc(duration);

        // Update promotion and allocation counters
        final var before = gcInfo.getMemoryUsageBeforeGc();
        final var after = gcInfo.getMemoryUsageAfterGc();

        if (oldGenPoolName != null) {
            final var oldBefore = before.get(oldGenPoolName).getUsed();
            final var oldAfter = after.get(oldGenPoolName).getUsed();
            final var delta = oldAfter - oldBefore;
            if (delta > 0L) {
                promotedBytes.addAndGet(delta);
            }

            // Some GC implementations such as G1 can reduce the old gen size as part of a
            // minor GC. To track
            // the
            // live data size we record the value if we see a reduction in the old gen heap
            // size or
            // after a major GC.
            if (oldAfter < oldBefore || GcGenerationAge.fromName(notificationInfo.getGcName()) == GcGenerationAge.OLD) {
                liveDataSize.set(oldAfter);
                final var oldMaxAfter = after.get(oldGenPoolName).getMax();
                maxDataSize.set(oldMaxAfter);
            }
        }

        if (youngGenPoolName != null) {
            final var youngBefore = before.get(youngGenPoolName).getUsed();
            final var youngAfter = after.get(youngGenPoolName).getUsed();
            final var delta = youngBefore - youngGenSizeAfter.get();
            youngGenSizeAfter.set(youngAfter);
            if (delta > 0L) {
                allocatedBytes.addAndGet(delta);
            }
        }
    }

    private void registerGCTotal(final MetricRegistry registry, final GarbageCollectorMXBean mbean) {
        registry.register(new ExtendedMetadataBuilder().withName("gc.total").withDisplayName("Garbage Collection Count")
                .withType(MetricType.COUNTER)
                // multi!
                .withDescription("""
                        Displays the total number of collections that have occurred. \
                        This attribute lists -1 if the collection count is undefined for this collector.\
                        """).build(), new GetCountOnlyCounter() {

                    @Override
                    public long getCount() {
                        return mbean.getCollectionCount();
                    }
                }, new Tag("name", mbean.getName()));
    }

    private void registerGCTime(final MetricRegistry registry, final GarbageCollectorMXBean mbean) {
        registry.register(new ExtendedMetadataBuilder().withName("gc.time").withDisplayName("Garbage Collection Time")
                .withUnit(MetricUnits.MILLISECONDS).withType(MetricType.GAUGE)
                // multi!
                .withDescription("""
                        Displays the approximate accumulated collection elapsed time in\s\
                        milliseconds. This attribute displays -1 if the collection elapsed time is undefined for\
                         this collector. The Java virtual machine implementation may use a high resolution timer\
                         to measure the elapsed time. This attribute may display the same value even if the\s\
                        collection count has been incremented if the collection elapsed time is very short.""").build(),
                (Gauge<?>) mbean::getCollectionTime, new Tag("name", mbean.getName()));
    }

    @Override
    public void close() {
        notificationEmitters.forEach((emitter, listener) -> {
            try {
                emitter.removeNotificationListener(listener);
            } catch (final ListenerNotFoundException e) {
                // noop
                LOGGER.trace("Could not close GC metrics listener", e);
            }
        });
    }

    private boolean isYoungGenPool(final String name) {
        return name.endsWith("Eden Space");
    }

    private boolean isOldGenPool(final String name) {
        return name.endsWith("Old Gen") || name.endsWith("Tenured Gen");
    }

    private boolean isConcurrentPhase(final String cause) {
        return "No GC".equals(cause);
    }

    enum GcGenerationAge {

        OLD, YOUNG, UNKNOWN;

        private static final Map<String, GcGenerationAge> knownCollectors = new MapBuilder<String, GcGenerationAge>()
                .put("ConcurrentMarkSweep", OLD).put("Copy", YOUNG).put("G1 Old Generation", OLD)
                .put("G1 Young Generation", YOUNG).put("MarkSweepCompact", OLD).put("PS MarkSweep", OLD)
                .put("PS Scavenge", YOUNG).put("ParNew", YOUNG).toImmutableMap();

        static GcGenerationAge fromName(final String name) {
            return knownCollectors.getOrDefault(name, UNKNOWN);
        }
    }

    private static boolean isManagementExtensionsPresent() {
        try {
            Class.forName("com.sun.management.GarbageCollectionNotificationInfo", false,
                    JvmGcMetrics.class.getClassLoader());
            return true;
        } catch (final Exception e) {
            // We are operating in a JVM without access to this level of detail
            LOGGER.debug("""
                    GC notifications will not be available because \
                    com.sun.management.GarbageCollectionNotificationInfo is not present\
                    """);
            return false;
        }
    }
}
