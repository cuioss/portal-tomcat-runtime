package de.cuioss.portal.tomcat.authentication;

import java.io.Serializable;
import java.security.Principal;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("javadoc")
public class MockPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = -9158214411838373258L;
    @Setter
    @Getter
    private String name;

}
