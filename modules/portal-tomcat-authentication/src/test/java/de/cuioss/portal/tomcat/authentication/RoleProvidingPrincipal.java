package de.cuioss.portal.tomcat.authentication;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("javadoc")
public class RoleProvidingPrincipal extends MockPrincipal {

    private static final long serialVersionUID = -1508259532652784656L;

    @Getter
    @Setter
    private String[] roles;
}
