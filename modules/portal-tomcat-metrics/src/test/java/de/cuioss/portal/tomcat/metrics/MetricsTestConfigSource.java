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

import static de.cuioss.tools.collect.CollectionLiterals.immutableMap;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * @author Sven Haag
 */
public class MetricsTestConfigSource implements ConfigSource {

    private static final Map<String, String> PROPERTIES = immutableMap("application.context.name", "test-app");

    @Override
    public Map<String, String> getProperties() {
        return PROPERTIES;
    }

    @Override
    public Set<String> getPropertyNames() {
        return PROPERTIES.keySet();
    }

    @Override
    public String getValue(final String key) {
        return PROPERTIES.get(key);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
}
