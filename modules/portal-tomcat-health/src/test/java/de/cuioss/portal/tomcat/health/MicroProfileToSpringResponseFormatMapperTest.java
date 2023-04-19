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
        final var response = Json.createReader(new InputStreamReader(getClass()
            .getResourceAsStream("/health_mp_down_wo_data.json")))
            .readObject();
        final var health = new SmallRyeHealth(response);
        assertDoesNotThrow(() -> MicroProfileToSpringResponseFormatMapper.apply(health));
    }
}
