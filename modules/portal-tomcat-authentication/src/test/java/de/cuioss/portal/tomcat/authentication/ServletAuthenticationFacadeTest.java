package de.cuioss.portal.tomcat.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.myfaces.test.mock.MockHttpSession;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cuioss.portal.authentication.facade.PortalAuthenticationFacade;
import de.cuioss.portal.core.test.junit5.EnablePortalConfiguration;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.Joiner;
import de.cuioss.uimodel.application.LoginCredentials;
import lombok.Getter;

@EnableAutoWeld
@EnableTestLogger
@EnablePortalConfiguration
class ServletAuthenticationFacadeTest implements ShouldHandleObjectContracts<ServletAuthenticationFacade> {

    private static final CuiLogger log = new CuiLogger(ServletAuthenticationFacadeTest.class);

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String[] ROLES = new String[] { "role1", "role2" };

    private static final LoginCredentials VALID_CREDENTIALS =
        LoginCredentials.builder().username(USERNAME).password(PASSWORD).build();

    private static final LoginCredentials INCOMPLETE_CREDENTIALS =
        LoginCredentials.builder().username(USERNAME).build();

    @Inject
    @Getter
    @PortalAuthenticationFacade
    private ServletAuthenticationFacade underTest;

    private ControllableMockHttpServletRequest mockRequest;
    private RoleProvidingPrincipal roleProvidingPrincipal;

    @BeforeEach
    void beforeTest() {
        mockRequest = new ControllableMockHttpServletRequest();
        mockRequest.setHttpSession(new MockHttpSession());

        roleProvidingPrincipal = new RoleProvidingPrincipal();
        roleProvidingPrincipal.setName(USERNAME);
        roleProvidingPrincipal.setRoles(ROLES);
    }

    @Test
    void shouldLoginHappyCase() {
        mockRequest.setUserPrincipal(roleProvidingPrincipal);
        final var userInfo = underTest.login(mockRequest, VALID_CREDENTIALS).getResult();
        assertNotNull(userInfo);
        assertTrue(userInfo.isAuthenticated());
        assertEquals(USERNAME, userInfo.getDisplayName());
        assertEquals(2, userInfo.getRoles().size());

        assertEquals(userInfo, underTest.retrieveCurrentAuthenticationContext(mockRequest));
    }

    @Test
    void shouldHandleIncompleteCredentials() {
        final var userInfo = underTest.login(mockRequest, INCOMPLETE_CREDENTIALS);
        assertNotNull(userInfo);
        assertFalse(userInfo.isValid());
        assertNotNull(userInfo.getResultDetail().get().getDetail());
        assertFalse(userInfo.getResult().isAuthenticated());
    }

    @Test
    void shouldHandleLoginError() {
        mockRequest.setThrowExceptionOnLogin(true);
        final var userInfo = underTest.login(mockRequest, VALID_CREDENTIALS);
        assertNotNull(userInfo);
        assertFalse(userInfo.isValid());
        assertNotNull(userInfo.getResultDetail().get().getDetail());
        assertFalse(userInfo.getResult().isAuthenticated());
        assertNotNull(userInfo.getResult().getIdentifier());
    }

    @Test
    void shouldLoginWithInvalidPrincipal() {
        final var mockPrincipal = new MockPrincipal();
        mockPrincipal.setName(USERNAME);
        mockRequest.setUserPrincipal(mockPrincipal);

        final var userInfo = underTest.login(mockRequest, VALID_CREDENTIALS);
        assertNotNull(userInfo);
        assertTrue(userInfo.isValid());
        assertTrue(userInfo.getResult().isAuthenticated());
        assertEquals(USERNAME, userInfo.getResult().getDisplayName());
        assertTrue(userInfo.getResult().getRoles().isEmpty());

        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR, "Portal-504");

        assertEquals(userInfo.getResult(), underTest.retrieveCurrentAuthenticationContext(mockRequest));
    }

    @Test
    void shouldRetrieveExistingAuthorizationContext() {
        mockRequest.setUserPrincipal(roleProvidingPrincipal);
        assertTrue(underTest.retrieveCurrentAuthenticationContext(mockRequest).isAuthenticated());
    }

    @Test
    void shouldHandleRetrieveAuthorizationContextWithoutSession() {
        mockRequest.setHttpSession(null);
        assertFalse(underTest.retrieveCurrentAuthenticationContext(mockRequest).isAuthenticated());
    }

    @Test
    void shouldLogoutHappyCase() {

        assertTrue(underTest.logout(mockRequest));

        // Should gracefully handle multiple logout
        assertTrue(underTest.logout(mockRequest));

        mockRequest.setHttpSession(null);
        assertFalse(underTest.logout(mockRequest));
    }

    @Test
    void shouldLogoutGracefullyOnException() {
        mockRequest.setThrowExceptionOnLogout(true);
        assertFalse(underTest.logout(mockRequest));
    }

    @Test
    void shouldBasicAuth() {
        mockRequest.setUserPrincipal(roleProvidingPrincipal);
        mockRequest.setAuthenticateUser(true);
        mockRequest.addHeader("Authorization", toBasicAuthValue(USERNAME, PASSWORD));

        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);

        assertNotNull(userInfo);
        assertTrue(userInfo.isAuthenticated());
        assertEquals(USERNAME, userInfo.getDisplayName());
        assertEquals(2, userInfo.getRoles().size());
    }

    @Test
    void shouldHandleBasicAuthWithoutCredentials() {
        mockRequest.setUserPrincipal(roleProvidingPrincipal);
        mockRequest.setAuthenticateUser(true);
        mockRequest.addHeader("Authorization", "Basic");
        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);
        assertNotNull(userInfo);
        assertFalse(userInfo.isAuthenticated());
    }

    /** Test for potential overflow, but http header size is limited by server */
    @Test
    void basicAuthWithBigHeader() {
        final var generator = Generators.strings(10240, 10240);
        mockRequest.addHeader("Authorization", toBasicAuthValue(generator.next(), generator.next()));
        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);
        assertNotNull(userInfo);
        assertFalse(userInfo.isAuthenticated());
    }

    @Test
    void basicAuthWithWrongToken() {
        mockRequest.addHeader("Authorization", "Basic2");
        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);
        assertNotNull(userInfo);
        assertFalse(userInfo.isAuthenticated());
    }

    @Test
    void basicAuthWithEmptyContent() {
        mockRequest.addHeader("Authorization", "Basic ");
        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);
        assertNotNull(userInfo);
        assertFalse(userInfo.isAuthenticated());
    }

    @Test
    void basicAuthWithInvalidBase64Credentials() {
        mockRequest.addHeader("Authorization", "Basic .");
        final var userInfo = underTest.retrieveCurrentAuthenticationContext(mockRequest);
        assertNotNull(userInfo);
        assertFalse(userInfo.isAuthenticated());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Portal-531");
    }

    /**
     * @param username required
     * @param password can be null
     *
     * @return base64 encoded username:password
     */
    private String toBasicAuthValue(final String username, final String password) {
        Objects.requireNonNull(username);
        try {
            return "Basic " + Base64.getEncoder().encodeToString(
                    Joiner.on(':').skipNulls().join(username, password)
                            .getBytes(StandardCharsets.UTF_8.name()));
        } catch (final Exception e) {
            log.error("could not encode basic auth credentials", e);
        }
        return null;
    }
}
