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
package de.cuioss.portal.tomcat.health.checks;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import lombok.ToString;

@SuppressWarnings("javadoc")
@ToString
public class HealthCheckDynamic implements HealthCheck {

    private boolean state = false;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named(HealthCheckDynamic.class.getSimpleName())
                .withData("data_key_1", "data_value_1").withData("data_key_2", "data_value_2")
                .withData("data_long", 1234567890L).withData("data_bool", true).status(state).build();
    }

    /** change state to UP */
    public void up() {
        state = true;
    }

    /** change state to DOWN */
    public void down() {
        state = false;
    }

    private String getHealthStatus() {
        return state ? HealthCheckResponse.Status.UP.name() : HealthCheckResponse.Status.DOWN.name();
    }

    public String getJsonResponse() {
        return "{\n" + "  \"status\": \"" + getHealthStatus() + "\",\n" + "  \"checks\": [\n" + "    {\n"
                + "      \"name\": \"HealthCheckDynamic\",\n" + "      \"status\": \"" + getHealthStatus() + "\",\n"
                + "      \"data\": {\n" + "        \"data_bool\": true,\n" + "        \"data_long\": 1234567890,\n"
                + "        \"data_key_1\": \"data_value_1\",\n" + "        \"data_key_2\": \"data_value_2\"\n"
                + "      }\n" + "    }\n" + "  ]\n" + "}";
    }
}
