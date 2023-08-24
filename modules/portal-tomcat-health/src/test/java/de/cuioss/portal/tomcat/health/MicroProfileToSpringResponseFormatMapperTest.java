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
package de.cuioss.portal.tomcat.health;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.InputStreamReader;

import javax.json.Json;

import org.junit.jupiter.api.Test;

import io.smallrye.health.SmallRyeHealth;

class MicroProfileToSpringResponseFormatMapperTest {

    /**
     * Read a MP response, containing a health check without data.
     */
    @Test
    void handleMissingDataElement() {
        final var response = Json
                .createReader(new InputStreamReader(getClass().getResourceAsStream("/health_mp_down_wo_data.json")))
                .readObject();
        final var health = new SmallRyeHealth(response);
        assertDoesNotThrow(() -> MicroProfileToSpringResponseFormatMapper.apply(health));
    }
}
