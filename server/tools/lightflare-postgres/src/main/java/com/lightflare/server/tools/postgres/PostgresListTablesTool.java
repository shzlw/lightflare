package com.lightflare.server.tools.postgres;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class PostgresListTablesTool implements Tool {

    private final PostgresService postgresService;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("postgres-list-tables")
            .description("List all available tables in the primary public schema of the PostgreSQL database.")
            .category("database")
            .integrationId("postgres")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("connectionName")
                            .type("string")
                            .description("The name of the configured connection to use. Optional if only one is configured.")
                            .required(false)
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

        try {
            List<String> tables = postgresService.listTables(connectionName);
            if (tables.isEmpty()) {
                return ToolResult.success("No tables found in the current schema.");
            }
            return ToolResult.success("Tables: " + String.join(", ", tables));
        } catch (Exception e) {
            return ToolResult.failure("Failed to list tables: " + e.getMessage());
        }
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
