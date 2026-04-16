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
public class PostgresRunQueryTool implements Tool {

    private final PostgresService postgresService;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("postgres-run-query")
            .description("Execute a read-only SQL query (SELECT) on a PostgreSQL database.")
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
                            .name("sql")
                            .type("string")
                            .description("The SQL SELECT query to execute.")
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
        String sql = getStringArgument(arguments, "sql");

        if (sql == null || sql.isBlank()) {
            return ToolResult.failure("Missing required argument: sql");
        }

        String trimmedSql = sql.trim().toLowerCase();
        if (!trimmedSql.startsWith("select") && !trimmedSql.startsWith("with")) {
            return ToolResult.failure("Only SELECT or WITH (read-only) queries are allowed for safety reasons.");
        }

        try {
            List<Map<String, Object>> results = postgresService.query(connectionName, sql);
            if (results.isEmpty()) {
                return ToolResult.success("Query executed successfully, but returned no results.");
            }
            return ToolResult.success(PostgresUtils.OBJECT_MAPPER.writeValueAsString(results));
        } catch (Exception e) {
            return ToolResult.failure("Failed to execute query: " + e.getMessage());
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
