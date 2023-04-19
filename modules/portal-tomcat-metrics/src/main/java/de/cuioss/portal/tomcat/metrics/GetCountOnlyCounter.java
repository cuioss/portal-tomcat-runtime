package de.cuioss.portal.tomcat.metrics;

import org.eclipse.microprofile.metrics.Counter;

/**
 * Copy from io.quarkus.smallrye.metrics.runtime.GetCountOnlyCounter.
 * <p>
 * A helper abstract class for implementing counters which only need {@link #getCount()}.
 * Other methods throw an exception.
 *
 * @author Sven Haag
 */
public abstract class GetCountOnlyCounter implements Counter {

    private static final String MUST_NOT_BE_CALLED = "Must not be called";

    @Override
    public void inc() {
        throw new IllegalStateException(MUST_NOT_BE_CALLED);
    }

    @Override
    public void inc(final long n) {
        throw new IllegalStateException(MUST_NOT_BE_CALLED);
    }
}
