package de.cuioss.portal.tomcat.authentication;

import java.security.Principal;

import de.cuioss.test.jsf.mocks.CuiMockHttpServletRequest;
import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("javadoc")
public class ControllableMockHttpServletRequest extends CuiMockHttpServletRequest {

    @Getter
    @Setter
    private boolean throwExceptionOnLogin = false;

    @Getter
    @Setter
    private boolean throwExceptionOnLogout = false;

    /**
     * if true compares the username used on the last login with the principal name.
     */
    @Getter
    @Setter
    private boolean authenticateUser = false;

    private String username;

    @Override
    public void login(final String username, final String password) {
        if (throwExceptionOnLogin) {
            throw new IllegalStateException();
        }
        this.username = username;
    }

    @Override
    public void logout() {
        if (throwExceptionOnLogout) {
            throw new IllegalStateException();
        }
    }

    @Override
    public String getRequestURI() {
        return "test";
    }

    @Override
    public Principal getUserPrincipal() {
        if (authenticateUser) {
            if (null != username && username.equals(super.getUserPrincipal().getName())) {
                return super.getUserPrincipal();
            }
            return null;
        }
        return super.getUserPrincipal();
    }
}
