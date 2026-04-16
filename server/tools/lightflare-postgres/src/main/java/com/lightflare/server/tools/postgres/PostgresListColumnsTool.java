package com.lightflare.server.tools.postgres;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PostgresListColumnsTool implements Tool {

    private final PostgresService postgresService;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("postgres-list-columns")
            .description("Get detailed column information (types, nullability, etc.) for a specific table in the PostgreSQL database.")
            .category("database")
            .integrationId("postgres")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("connectionName")
                            .type("string")
                            .description("The name of the configured connection to use.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("tableName")
                            .type("string")
                            .description("The name of the table to inspect.")
                            .required(true)
                            .build()
            ))
            .build();

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String connectionName = getOptionalStringArgument(arguments, "connectionName");
        String tableName = getStringArgument(arguments, "tableName");

        if (tableName == null || tableName.isBlank()) {
            return ToolResult.failure("Missing required argument: tableName");
        }

        try {
            List<Map<String, Object>> columns = postgresService.getTableColumns(connectionName, tableName);
            if (columns.isEmpty()) {
                return ToolResult.failure("Table not found or has no columns: " + tableName);
            }
            return ToolResult.success(PostgresUtils.OBJECT_MAPPER.writeValueAsString(columns));
        } catch (Exception e) {
            return ToolResult.failure("Failed to list columns: " + e.getMessage());
        }
    }

    private String getStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
