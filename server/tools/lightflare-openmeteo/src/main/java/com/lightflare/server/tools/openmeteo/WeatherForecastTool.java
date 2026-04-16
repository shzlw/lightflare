package com.lightflare.server.tools.openmeteo;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class WeatherForecastTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("weather-forecast")
            .description("Get the current weather and multi-day forecast for latitude and longitude coordinates.")
            .category("weather")
            .integrationId("openmeteo")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("latitude")
                            .type("number")
                            .description("The latitude coordinate (e.g., 52.52).")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("longitude")
                            .type("number")
                            .description("The longitude coordinate (e.g., 13.41).")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("forecast_days")
                            .type("integer")
                            .description("The number of forecast days to return (1-16).")
                            .required(false)
                            .build()
            ))
            .build();

    private final OpenMeteoService openMeteoService;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        Double latitude = readDoubleArgument(arguments, "latitude");
        Double longitude = readDoubleArgument(arguments, "longitude");

        if (latitude == null || longitude == null) {
            return ToolResult.failure("latitude and longitude are required");
        }

        String forecastDaysValue = readStringArgument(arguments, "forecast_days");
        Integer forecastDays = null;
        if (forecastDaysValue != null) {
            try {
                forecastDays = Integer.parseInt(forecastDaysValue);
            } catch (NumberFormatException e) {
                return ToolResult.failure("forecast_days must be an integer");
            }
        }

        try {
            return ToolResult.success(openMeteoService.getForecastByLocation(
                    new OpenMeteoLocationRequest(null, null, latitude, longitude),
                    forecastDays
            ));
        } catch (RuntimeException e) {
            return ToolResult.failure(e.getMessage());
        }
    }

    private String readStringArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(parameter -> name.equals(parameter.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Double readDoubleArgument(List<ToolArgument> arguments, String name) {
        String value = readStringArgument(arguments, name);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }
}
