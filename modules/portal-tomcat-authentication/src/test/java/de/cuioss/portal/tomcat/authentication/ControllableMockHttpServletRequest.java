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
