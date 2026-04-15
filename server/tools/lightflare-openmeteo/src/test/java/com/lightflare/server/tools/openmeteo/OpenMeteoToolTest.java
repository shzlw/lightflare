package com.lightflare.server.tools.openmeteo;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenMeteoToolTest {

    @Test
    void geocodingToolRequiresLocation() {
        GeocodingTool tool = new GeocodingTool(stubService());

        ToolResult result = tool.execute(List.of(), null);

        assertFalse(result.success());
        assertEquals("location is required", result.content());
    }

    @Test
    void weatherForecastToolRequiresCoordinates() {
        WeatherForecastTool tool = new WeatherForecastTool(stubService());

        ToolResult result = tool.execute(List.of(
                ToolArgument.builder().name("latitude").value("32.7767").build()
        ), null);

        assertFalse(result.success());
        assertEquals("latitude and longitude are required", result.content());
    }

    @Test
    void weatherForecastToolDelegatesToForecastService() {
        WeatherForecastTool tool = new WeatherForecastTool(stubService());

        ToolResult result = tool.execute(List.of(
                ToolArgument.builder().name("latitude").value("32.7767").build(),
                ToolArgument.builder().name("longitude").value("-96.7970").build(),
                ToolArgument.builder().name("forecast_days").value("5").build()
        ), null);

        assertTrue(result.success());
        assertEquals("{\"type\":\"forecast\"}", result.content());
    }

    private OpenMeteoService stubService() {
        return new OpenMeteoService(
                RestClient.builder().baseUrl("https://example.com").build(),
                RestClient.builder().baseUrl("https://example.com").build(),
                new OpenMeteoProperties(true, java.util.List.of("weather-forecast", "geocoding"), null, null, 3)
        ) {
            @Override
            public String geocodeLocation(OpenMeteoLocationRequest locationRequest) {
                return "{\"type\":\"geocode\"}";
            }

            @Override
            public String getForecastByLocation(OpenMeteoLocationRequest locationRequest, Integer requestedForecastDays) {
                return "{\"type\":\"forecast\"}";
            }
        };
    }
}
