package de.cuioss.portal.tomcat.metrics;

import java.lang.management.ManagementFactory;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

import io.smallrye.metrics.ExtendedMetadataBuilder;
import lombok.experimental.UtilityClass;

/**
 * <h1>JVM Metrics</h1>
 * <ol>
 * <li>The number of classes that are currently loaded in the Java Virtual Machine</li>
 * <li>The total number of classes that have been loaded since the JVM has started execution</li>
 * <li>The total number of classes unloaded since the JVM has started execution</li>
 * </ol>
 *
 * <h3>Micrometer Format</h3>
 * <ol>
 * <li>jvm_classes_loaded_classes</li>
 * <li>jvm_classes_loaded_classes_total</li>
 * <li>jvm_classes_unloaded_classes_total</li>
 * </ol>
 *
 * <h3>MicroProfile Format</h3>
 * <ol>
 * <li>classloader.loadedClasses.count</li>
 * <li>classloader.loadedClasses.total</li>
 * <li>classloader.unloadedClasses.total</li>
 * </ol>
 *
 * @author Sven Haag
 */
@UtilityClass
final class ClassLoaderMetrics {

    private static final String JAVA_VIRTUAL_MACHINE_HAS_STARTED_EXECUTION =
        "Java Virtual Machine has started execution.";
    private static final String UNIT_CLASSES = "classes";

    void bindTo(final MetricRegistry registry, final boolean micrometerFormat) {
        // The ClassLoadingMXBean can be used in native mode, but it only returns zeroes, so there's
        // no point in
        // including such metrics.
        final var classLoadingBean = ManagementFactory.getClassLoadingMXBean();

        if (micrometerFormat) {
            registry.register(
                    new ExtendedMetadataBuilder()
                            .withName("jvm.classes.loaded")
                            .withType(MetricType.GAUGE)
                            .withUnit(UNIT_CLASSES)
                            .withDescription(
                                    "The number of classes that are currently loaded in the Java Virtual Machine")
                            .withOpenMetricsKeyOverride("jvm_classes_loaded_classes")
                            .skipsScopeInOpenMetricsExportCompletely(true)
                            .build(),
                    (Gauge<?>) classLoadingBean::getLoadedClassCount);

            registry.register(
                    new ExtendedMetadataBuilder()
                            .withName("jvm.classes.loaded.total")
                            .withType(MetricType.GAUGE) // should be a counter?!
                            .withUnit(UNIT_CLASSES)
                            .withDescription("The total number of classes that have been loaded since the " +
                                    JAVA_VIRTUAL_MACHINE_HAS_STARTED_EXECUTION)
                            .withOpenMetricsKeyOverride("jvm_classes_loaded_classes_total")
                            .skipsScopeInOpenMetricsExportCompletely(true)
                            .build(),
                    (Gauge<?>) classLoadingBean::getTotalLoadedClassCount);

            registry.register(
                    new ExtendedMetadataBuilder()
                            .withName("jvm.classes.unloaded")
                            .withType(MetricType.COUNTER)
                            .withUnit(UNIT_CLASSES)
                            .withDescription(
                                    "The total number of classes unloaded since the Java Virtual Machine has started execution")
                            .withOpenMetricsKeyOverride("jvm_classes_unloaded_classes_total")
                            .skipsScopeInOpenMetricsExportCompletely(true)
                            .build(),
                    new GetCountOnlyCounter() {

                        @Override
                        public long getCount() {
                            return classLoadingBean.getUnloadedClassCount();
                        }
                    });
        } else {
            registry.register(
                    new MetadataBuilder()
                            // required by MP spec
                            .withName("classloader.loadedClasses.count")
                            .withDisplayName("Current Loaded Class Count")
                            .withType(MetricType.GAUGE)
                            .withDescription("Displays the number of classes that are currently loaded in the " +
                                    "Java Virtual Machine")
                            .build(),
                    (Gauge<?>) classLoadingBean::getLoadedClassCount);

            registry.register(
                    new MetadataBuilder()
                            // required by MP spec
                            .withName("classloader.loadedClasses.total")
                            .withType(MetricType.COUNTER)
                            .withDescription("Displays the total number of classes that have been loaded since the " +
                                    JAVA_VIRTUAL_MACHINE_HAS_STARTED_EXECUTION)
                            .build(),
                    new GetCountOnlyCounter() {

                        @Override
                        public long getCount() {
                            return classLoadingBean.getTotalLoadedClassCount();
                        }
                    });

            registry.register(
                    new MetadataBuilder()
                            // required by MP spec
                            .withName("classloader.unloadedClasses.total")
                            .withDisplayName("Total Unloaded Class Count")
                            .withType(MetricType.COUNTER)
                            .withDescription("Displays the total number of classes unloaded since the " +
                                    JAVA_VIRTUAL_MACHINE_HAS_STARTED_EXECUTION)
                            .build(),
                    new GetCountOnlyCounter() {

                        @Override
                        public long getCount() {
                            return classLoadingBean.getUnloadedClassCount();
                        }
                    });
        }
    }
}
