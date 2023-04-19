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
