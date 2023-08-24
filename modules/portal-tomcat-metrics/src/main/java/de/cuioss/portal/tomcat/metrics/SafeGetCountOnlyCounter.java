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
