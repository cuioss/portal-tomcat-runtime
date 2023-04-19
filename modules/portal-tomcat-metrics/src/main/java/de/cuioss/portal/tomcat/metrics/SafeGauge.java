package de.cuioss.portal.tomcat.metrics;

import java.util.concurrent.Callable;

import org.eclipse.microprofile.metrics.Gauge;

import de.cuioss.tools.logging.CuiLogger;
import lombok.experimental.UtilityClass;

/**
 * Catches and logs exceptions occurred during {@link Callable#call()}.
 *
 * @author Sven Haag
 */
@UtilityClass
final class SafeGauge {

    private static final CuiLogger LOGGER = new CuiLogger(SafeGauge.class);

    private static final String ERROR_MSG = "Portal-535: Error retrieving metric.";

    /**
     * @param callable called in a try-catch statement. the value is directly cast to double.
     *
     * @return gauge with double value
     */
    static Gauge<Double> doubleGauge(final Callable<Object> callable) {
        return () -> {
            try {
                return (Double) callable.call();
            } catch (final Exception e) {
                LOGGER.error(ERROR_MSG, e);
                return Double.NaN;
            }
        };
    }

    /**
     * @param callable called in a try-catch statement. the value is directly cast to integer.
     *
     * @return gauge with integer value
     */
    static Gauge<Integer> intGauge(final Callable<Object> callable) {
        return () -> {
            try {
                return (Integer) callable.call();
            } catch (final Exception e) {
                LOGGER.error(ERROR_MSG, e);
                return 0;
            }
        };
    }

    /**
     * @param callable called in a try-catch statement. the value is directly cast to long.
     *
     * @return gauge with long value
     */
    static Gauge<Long> longGauge(final Callable<Object> callable) {
        return () -> {
            try {
                return (Long) callable.call();
            } catch (final Exception e) {
                LOGGER.error(ERROR_MSG, e);
                return 0L;
            }
        };
    }
}
