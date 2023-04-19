package de.cuioss.portal.tomcat.metrics;

import static de.cuioss.tools.collect.CollectionLiterals.immutableList;

import java.util.List;

import de.cuioss.portal.configuration.FileConfigurationSource;
import de.cuioss.portal.configuration.MetricsConfigKeys;
import de.cuioss.portal.core.test.tests.configuration.AbstractConfigurationKeyVerifierTest;
import de.cuioss.portal.core.test.tests.configuration.PropertiesDefaultConfigSource;
import lombok.Getter;

class DefaultConfigTest extends AbstractConfigurationKeyVerifierTest {

    @Getter
    private final FileConfigurationSource underTest = new PropertiesDefaultConfigSource();

    @Override
    public Class<?> getKeyHolder() {
        return MetricsConfigKeys.class;
    }

    @Override
    public List<String> getKeysIgnoreList() {
        return immutableList(
            MetricsConfigKeys.PORTAL_METRICS_ROLES_REQUIRED,
            MetricsConfigKeys.PORTAL_METRICS_APP_NAME);
    }

    @Override
    public List<String> getConfigurationKeysIgnoreList() {
        return immutableList("mp.metrics.tags");
    }
}
