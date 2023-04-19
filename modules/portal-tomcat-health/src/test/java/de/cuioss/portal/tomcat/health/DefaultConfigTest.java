package de.cuioss.portal.tomcat.health;

import static de.cuioss.portal.configuration.HealthCheckConfigKeys.PORTAL_HEALTHCHECK_ROLES_REQUIRED;
import static de.cuioss.tools.collect.CollectionLiterals.immutableList;

import java.util.List;

import de.cuioss.portal.configuration.FileConfigurationSource;
import de.cuioss.portal.configuration.HealthCheckConfigKeys;
import de.cuioss.portal.core.test.tests.configuration.AbstractConfigurationKeyVerifierTest;
import de.cuioss.portal.core.test.tests.configuration.PropertiesDefaultConfigSource;
import lombok.Getter;

class DefaultConfigTest extends AbstractConfigurationKeyVerifierTest {

    @Getter
    private final FileConfigurationSource underTest = new PropertiesDefaultConfigSource();

    @Override
    public Class<?> getKeyHolder() {
        return HealthCheckConfigKeys.class;
    }

    @Override
    public List<String> getKeysIgnoreList() {
        return immutableList(PORTAL_HEALTHCHECK_ROLES_REQUIRED);
    }
}
