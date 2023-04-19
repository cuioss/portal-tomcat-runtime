package de.cuioss.portal.tomcat.metrics;

import java.util.concurrent.Callable;

import de.cuioss.tools.logging.CuiLogger;

/**
 * @author Sven Haag
 */
abstract class SafeGetCountOnlyCounter extends GetCountOnlyCounter {

    private static final CuiLogger LOGGER = new CuiLogger(SafeGetCountOnlyCounter.class);

    private static final String ERROR_MSG = "Portal-535: Error retrieving metric.";

    /**
     * @return callable whose value can be parsed to {@link Long}.
     */
    public abstract Callable<Number> getValueProvider();

    @Override
    public long getCount() {
        try {
            return getValueProvider().call().longValue();
        } catch (final Exception e) {
            LOGGER.error(ERROR_MSG, e);
        }
        return 0L;
    }
}
