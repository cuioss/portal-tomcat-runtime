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

import org.eclipse.microprofile.metrics.Counter;

/**
 * Copy from io.quarkus.smallrye.metrics.runtime.GetCountOnlyCounter.
 * <p>
 * A helper abstract class for implementing counters which only need
 * {@link #getCount()}. Other methods throw an exception.
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
