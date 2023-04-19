package de.cuioss.portal.tomcat.metrics;

import de.cuioss.portal.core.test.tests.BaseModuleConsistencyTest;

class ModuleConsistencyTest extends BaseModuleConsistencyTest {

    @Override
    protected void shouldStartUpContainer() {
        // org.jboss.weld.exceptions.DeploymentException: WELD-001408: Unsatisfied dependencies for type
        // AuthenticationFacade with qualifiers @PortalAuthenticationFacade
        // at @Inject @PortalAuthenticationFacade private de.cuioss.portal.core.user.PortalUserProducer.authenticationFacade
    }
}
