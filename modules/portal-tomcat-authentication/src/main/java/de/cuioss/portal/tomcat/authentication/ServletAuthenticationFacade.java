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
package de.cuioss.portal.tomcat.authentication;

import static de.cuioss.portal.authentication.facade.AuthenticationResults.invalidResultKey;
import static de.cuioss.portal.configuration.PortalConfigurationKeys.PORTAL_SERVLET_BASIC_AUTH_ALLOWED;
import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.cuioss.portal.authentication.AuthenticatedUserInfo;
import de.cuioss.portal.authentication.facade.AuthenticationFacade;
import de.cuioss.portal.authentication.facade.AuthenticationResults;
import de.cuioss.portal.authentication.facade.AuthenticationSource;
import de.cuioss.portal.authentication.facade.BaseAuthenticationFacade;
import de.cuioss.portal.authentication.facade.FormBasedAuthenticationFacade;
import de.cuioss.portal.authentication.facade.PortalAuthenticationFacade;
import de.cuioss.portal.authentication.model.BaseAuthenticatedUserInfo;
import de.cuioss.portal.authentication.model.UserStore;
import de.cuioss.tools.collect.CollectionLiterals;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.property.PropertyHolder;
import de.cuioss.uimodel.application.LoginCredentials;
import de.cuioss.uimodel.result.ResultObject;
import lombok.Getter;
import lombok.ToString;

/**
 * Implementation of the {@link AuthenticationFacade} interface that
 * authenticates against the {@link HttpServletRequest#login(String, String)}
 * and {@link HttpServletRequest#logout()} .
 *
 * @author Oliver Wolff
 */
@PortalAuthenticationFacade
@ApplicationScoped
@ToString
public class ServletAuthenticationFacade extends BaseAuthenticationFacade
        implements FormBasedAuthenticationFacade, Serializable {

    private static final CuiLogger log = new CuiLogger(ServletAuthenticationFacade.class);

    private static final String PORTAL_504_UNABLE_TO_DETERMINE_ROLE = "Portal-504: Unable to determine role for user {} found principal-class is {}";

    private static final String SESSION_KEY_USER_INFO = "sessionStoredAuthenticatedUserInfo";

    private static final long serialVersionUID = 1844545138388833549L;

    @Getter
    private final List<UserStore> availableUserStores = Collections.emptyList();

    @Inject
    @ConfigProperty(name = PORTAL_SERVLET_BASIC_AUTH_ALLOWED)
    private Provider<Boolean> allowBasicAuth;

    /**
     * Logs in against the {@link HttpServletRequest}
     */
    @Override
    public ResultObject<AuthenticatedUserInfo> login(final HttpServletRequest servletRequest,
            final LoginCredentials loginCredentials) {
        requireNonNull(loginCredentials);
        requireNonNull(servletRequest);
        if (loginCredentials.isComplete()) {
            try {
                servletRequest.login(loginCredentials.getUsername(), loginCredentials.getPassword());
                log.debug("login executed and returned without error {}", loginCredentials);
                invalidateSessionSafely(servletRequest);
                return determineAuthenticatedUserInfoAndAddToSession(loginCredentials.getUsername(), servletRequest);
            } catch (final RuntimeException | ServletException e) {
                log.debug("Unable to login due to ", e);
                return AuthenticationResults.invalidResultKey(AuthenticationResults.KEY_INVALID_CREDENTIALS,
                        loginCredentials.getUsername(), e);
            }
        }
        return AuthenticationResults.RESULT_INCOMPLETE_CREDENTIALS;
    }

    private static boolean invalidateSessionSafely(final HttpServletRequest servletRequest) {
        final var session = servletRequest.getSession(false);
        if (null != session) {
            try {
                // Paranoia mode
                session.removeAttribute(SESSION_KEY_USER_INFO);
                session.invalidate();
                return true;
            } catch (final IllegalStateException e) {
                log.debug("Unable to invalidate Session", e);
            }
        }
        return false;
    }

    private ResultObject<AuthenticatedUserInfo> determineAuthenticatedUserInfoAndAddToSession(final String username,
            final HttpServletRequest servletRequest) {

        var addUserToSession = true;
        var principal = servletRequest.getUserPrincipal();

        if (null == principal && allowBasicAuth.get()) {
            log.debug("Checking BASIC authentication");
            principal = getBasicAuthPrincipal(servletRequest).orElse(null);
            addUserToSession = false;
        }

        if (null == principal) {
            log.debug("determineAuthenticatedUserInfoAndAddToSession: no Principal found");
            return invalidResultKey(AuthenticationResults.KEY_INVALID_CREDENTIALS, username, null);
        }

        log.debug("retrieveCurrentAuthenticationContext: Principal found: {}", principal);
        final var userInfo = enrich(createAuthenticatedUser(principal));
        log.debug("Loaded user model:{}", userInfo);
        if (addUserToSession) {
            addUserToSession(servletRequest, userInfo);
        }
        return AuthenticationResults.validResult(userInfo);
    }

    private static void addUserToSession(final HttpServletRequest servletRequest,
            final AuthenticatedUserInfo userInfo) {
        final var session = servletRequest.getSession(true);
        if (null != session) {
            session.setAttribute(SESSION_KEY_USER_INFO, userInfo);
        }
    }

    private static AuthenticatedUserInfo createAuthenticatedUser(final Principal principal) {
        return BaseAuthenticatedUserInfo.builder().authenticated(true).identifier(principal.getName())
                .displayName(principal.getName()).roles(getRoles(principal)).system("TomcatRealm").build();
    }

    private static Optional<Principal> getBasicAuthPrincipal(final HttpServletRequest servletRequest) {
        try {
            final var credentials = getBasicAuthCredentials(servletRequest);
            if (credentials.isPresent()) {
                final var values = credentials.get().split(":", 2);
                String username = null;
                String password = null;

                if (2 == values.length) {
                    username = values[0];
                    password = values[1];
                } else if (1 == values.length) {
                    username = values[0];
                }

                servletRequest.login(username, password);
                log.debug("Basic auth login executed and returned without error for user={}", username);
                return Optional.ofNullable(servletRequest.getUserPrincipal());
            }
        } catch (final ServletException e) {
            log.debug("Unable to login due to ", e);
        } catch (final RuntimeException e) {
            log.warn("Portal-531: Error during Basic authentication", e);
        }

        return Optional.empty();
    }

    /**
     * @param servletRequest must not be null
     *
     * @return decoded base64 credentials
     */
    private static Optional<String> getBasicAuthCredentials(final HttpServletRequest servletRequest) {
        final var token = "basic ";
        final var authorization = servletRequest.getHeader("Authorization");

        if (null != authorization && authorization.toLowerCase().startsWith(token)) {
            log.debug("Basic Authorization Header found");
            final var base64Credentials = authorization.substring(token.length()).trim();
            if (base64Credentials.length() > 0) {
                return Optional.of(new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8));
            }
            log.debug("Basic Authorization Header with 0 length");
        }

        return Optional.empty();
    }

    /**
     * Extract role names from string array field "roles" in class
     * org.apache.catalina.realm.GenericPrincipal
     *
     * @param principal to extract from
     *
     * @return list with role names or empty list
     */
    private static List<String> getRoles(final Principal principal) {
        var roleProperty = PropertyHolder.from(principal.getClass(), "roles");
        if (roleProperty.isPresent()) {
            final var value = roleProperty.get().readFrom(principal);
            if (value instanceof String[] strings) {
                return CollectionLiterals.immutableList(strings);
            }
        } else {
            log.error(PORTAL_504_UNABLE_TO_DETERMINE_ROLE, principal.getName(), principal.getClass());
        }

        return Collections.emptyList();
    }

    @Override
    public boolean logout(final HttpServletRequest servletRequest) {
        try {
            servletRequest.logout();
            return invalidateSessionSafely(servletRequest);
        } catch (RuntimeException | ServletException e) {
            log.debug("Unable to logout due to ", e);
            return false;
        }
    }

    @Override
    public AuthenticatedUserInfo retrieveCurrentAuthenticationContext(final HttpServletRequest servletRequest) {
        log.debug("retrieveCurrentAuthenticationContext called with {}", servletRequest.getRequestURI());

        final var sessionUser = getUserInfoFromSession(servletRequest);
        if (sessionUser.isPresent()) {
            return sessionUser.get();
        }

        final var info = determineAuthenticatedUserInfoAndAddToSession(null, servletRequest);
        if (info.isValid()) {
            return info.getResult();
        }

        return AuthenticationResults.NOT_LOGGED_IN;
    }

    private Optional<AuthenticatedUserInfo> getUserInfoFromSession(final HttpServletRequest servletRequest) {
        final var session = servletRequest.getSession(false);
        if (null != session) {
            return Optional.ofNullable((AuthenticatedUserInfo) session.getAttribute(SESSION_KEY_USER_INFO));
        }
        return Optional.empty();
    }

    @Override
    public AuthenticationSource getAuthenticationSource() {
        return AuthenticationSource.TOMCAT_USER;
    }
}
