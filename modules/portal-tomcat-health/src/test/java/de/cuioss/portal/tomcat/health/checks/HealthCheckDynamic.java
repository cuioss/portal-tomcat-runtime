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
            .withData("data_key_1", "data_value_1")
            .withData("data_key_2", "data_value_2")
            .withData("data_long", 1234567890L)
            .withData("data_bool", true)
            .status(state)
            .build();
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
        return state
            ? HealthCheckResponse.Status.UP.name()
            : HealthCheckResponse.Status.DOWN.name();
    }

    public String getJsonResponse() {
        return "{\n" +
            "  \"status\": \"" + getHealthStatus() + "\",\n" +
            "  \"checks\": [\n" +
            "    {\n" +
            "      \"name\": \"HealthCheckDynamic\",\n" +
            "      \"status\": \"" + getHealthStatus() + "\",\n" +
            "      \"data\": {\n" +
            "        \"data_bool\": true,\n" +
            "        \"data_long\": 1234567890,\n" +
            "        \"data_key_1\": \"data_value_1\",\n" +
            "        \"data_key_2\": \"data_value_2\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }
}
