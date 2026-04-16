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
public class GeocodingTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("geocoding")
            .description("Resolve a location name into a specific place with latitude and longitude coordinates. Use country_code to narrow ambiguous matches.")
            .category("weather")
            .integrationId("openmeteo")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("location")
                            .type("string")
                            .description("The name of the location to resolve (e.g., 'Berlin').")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("country_code")
                            .type("string")
                            .description("Optional ISO 3166-1 alpha-2 country code (e.g., 'DE').")
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
        String location = readStringArgument(arguments, "location");
        String countryCode = readStringArgument(arguments, "country_code");

        if (location == null) {
            return ToolResult.failure("location is required");
        }

        try {
            return ToolResult.success(openMeteoService.geocodeLocation(
                    new OpenMeteoLocationRequest(location, countryCode, null, null)
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
}
