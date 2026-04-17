package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import com.lightflare.server.workflow.Workflow;
import com.lightflare.server.workflow.WorkflowService;
import com.lightflare.server.workflow.WorkflowStepExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowDefinitionTool implements InternalTool {

    private final WorkflowService workflowService;
    private final ToolService toolService;

    private static final String USAGE_GUIDANCE = """
            Use this tool to manage workflows. The `schema_definition` is a JSONB document.
            
            <STRUCTURE>
            {
              "version": 1,
              "steps": [
                {
                  "stepId": "unique_id",
                  "type": "TRIGGER | TOOL | CONDITION", 
                  "actionIdentifier": "...",
                  "inputMapping": { 
                    "param": "{{ previousId.output.key }}" 
                  },
                  "transitions": [
                    { 
                       "conditionExpression": "#steps['otherId'].output.val > 0", 
                       "targetStepId": "next_id" 
                    }
                  ]
                }
              ]
            }
            </STRUCTURE>
            
            <INPUT_MAPPING>
            - Use `{{ stepId.output.property }}` to inject data into step arguments.
            - This is replaced BEFORE the step runs.
            - Supports dot notation for object maps, for example `{{ fetch.output.body }}`.
            </INPUT_MAPPING>

            <SUPPORTED_STEP_TYPES>
            - TRIGGER: emits initial execution data.
            - TOOL: calls a normal tool from the tool catalog, such as `http-get`, `web-search`, or `web-page-content-extractor`.
            - CONDITION: passes resolved inputs through so transitions can branch.
            - LLM steps are not implemented yet. Do not use type `LLM` in workflow schemas.
            </SUPPORTED_STEP_TYPES>
            
            <TRANSITIONS>
            - Transitions use Spring Expression Language (SpEL).
            - Use `#output` to refer to the current step's result.
            - Use `#steps['stepId'].output` to refer to any previous step's result in the graph.
            - Use 'default' (case-insensitive) for the fallback transition.
            </TRANSITIONS>
            
            <Common_Expressions>
            - Null Checks: `#output != null`, `#steps['fetch'].output == null`
            - String Checks: `#output.contains('error')`, `#output.startsWith('https://')`
            - Numeric Logic: `#output.size() > 0`, `#steps['count'].output == 5`
            - Boolean Logic: `#output.success && #steps['auth'].output.valid`
            - Complex Map Access: `#output['status'] == 'FAILED'`, `#output.data?.user?.email != null`
            - Collection Selection (Advanced): 
               - Filter a list: `#output.?[value == 123]`
               - Find first match: `#output.^[category == 'news']`
               - Last match: `#output.$[status == 'active']`
               - Projecting fields: `#output.![name]` (returns a list of names)
            </Common_Expressions>
            
            <EXECUTION_FLOW>
            - The workflow starts at the first TRIGGER step. If no TRIGGER exists, it starts at the first step.
            - Workflow graphs must be acyclic. Loops are rejected during validation.
            - Transitions are evaluated in the order they appear in the JSON array.
            - Execution follows the FIRST transition whose condition evaluates to 'true'.
            - Put the 'default' transition last.
            - If no condition is true and no 'default' exists, the workflow ends.
            - To end explicitly, set 'targetStepId' to 'end' or leave transitions empty.
            </EXECUTION_FLOW>

            <MINIMAL_VALID_SCHEMA>
            {
              "version": 1,
              "steps": [
                {
                  "stepId": "start",
                  "type": "TRIGGER",
                  "inputMapping": {},
                  "transitions": [
                    { "conditionExpression": "default", "targetStepId": "fetch" }
                  ]
                },
                {
                  "stepId": "fetch",
                  "type": "TOOL",
                  "actionIdentifier": "http-get",
                  "inputMapping": {
                    "url": "https://example.com",
                    "headers": {}
                  },
                  "transitions": []
                }
              ],
              "metadata": {
                "purpose": "Describe what this workflow does"
              }
            }
            </MINIMAL_VALID_SCHEMA>
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-workflow-definition")
            .description("Get or update the structural definition (schema) of a workflow. This allows you to view existing steps or modify the graph logic.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("action")
                            .type("string")
                            .description("The action to perform: 'schema', 'get', 'upsert', or 'execution-steps'. Use 'schema' when unsure about the workflow schema.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("workflow_id")
                            .type("string")
                            .description("The ID of the workflow. Required for 'get', optional for 'upsert' (if creating a new one).")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("name")
                            .type("string")
                            .description("The display name of the workflow. (Used for 'upsert')")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("description")
                            .type("string")
                            .description("The description of what the workflow does. (Used for 'upsert')")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("schema_definition")
                            .type("string")
                            .description("The full workflow JSON schema as a valid JSON string. Also accepts an object when invoked programmatically. Used for 'upsert'.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("execution_id")
                            .type("string")
                            .description("The execution ID. Required for 'execution-steps'.")
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
        String action = getRequiredStringArgument(arguments, "action");
        String workflowId = getOptionalStringArgument(arguments, "workflow_id");

        return switch (action.toLowerCase()) {
            case "schema", "help" -> handleSchema();
            case "get" -> handleGet(workflowId);
            case "upsert" -> handleUpsert(arguments, workflowId);
            case "execution-steps" -> handleExecutionSteps(getOptionalStringArgument(arguments, "execution_id"));
            default -> ToolResult.failure("Unknown action: " + action + ". Use 'get' or 'upsert'.");
        };
    }

    private ToolResult handleSchema() {
        return ToolResult.success(JsonUtils.toJson(Map.of(
                "schema", exampleSchema(),
                "notes", List.of(
                        "schema_definition may be sent as a JSON string.",
                        "Use only TRIGGER, TOOL, and CONDITION step types.",
                        "TOOL actionIdentifier must be a normal tool name, not an internal workflow tool.",
                        "Workflow graphs must be acyclic.",
                        "Put default transitions last."
                ),
                "availableToolNames", toolService.listTools().stream()
                        .map(definition -> definition.getName())
                        .sorted()
                        .toList()
        )));
    }

    private ToolResult handleGet(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ToolResult.failure("workflow_id is required for 'get' action.");
        }
        try {
            Workflow workflow = workflowService.getWorkflow(workflowId);
            return ToolResult.success(JsonUtils.toJson(workflow));
        } catch (Exception e) {
            return ToolResult.failure("Workflow not found: " + workflowId);
        }
    }

    private ToolResult handleUpsert(List<ToolArgument> arguments, String workflowId) {
        String name = getOptionalStringArgument(arguments, "name");
        String description = getOptionalStringArgument(arguments, "description");
        String schemaDefinition = getOptionalJsonArgument(arguments, "schema_definition", "schemaDefinition");

        try {
            Workflow workflow;
            if (StringUtils.hasText(workflowId)) {
                // Update
                workflow = workflowService.getWorkflow(workflowId);
                String finalName = name != null ? name : workflow.getName();
                String finalDesc = description != null ? description : workflow.getDescription();
                String finalSchema = schemaDefinition != null ? schemaDefinition : workflow.getSchemaDefinition();
                workflow = workflowService.updateWorkflow(workflowId, finalName, finalDesc, finalSchema);
                return ToolResult.success("Successfully updated workflow. " + JsonUtils.toJson(workflow));
            } else {
                // Create
                if (!StringUtils.hasText(name)) {
                    return ToolResult.failure("name is required when creating a new workflow.");
                }
                String finalSchema = schemaDefinition != null ? schemaDefinition : "{\"version\": 1, \"steps\": []}";
                workflow = workflowService.createWorkflow(name, description, finalSchema);
                return ToolResult.success("Successfully created new workflow. " + JsonUtils.toJson(workflow));
            }
        } catch (Exception e) {
            return ToolResult.failure("Failed to upsert workflow: " + e.getMessage()
                    + "\nExpected schema example: " + JsonUtils.toJson(exampleSchema()));
        }
    }

    private ToolResult handleExecutionSteps(String executionId) {
        if (!StringUtils.hasText(executionId)) {
            return ToolResult.failure("execution_id is required for 'execution-steps' action.");
        }
        try {
            List<WorkflowStepExecution> steps = workflowService.getExecutionSteps(executionId);
            return ToolResult.success(JsonUtils.toJson(steps));
        } catch (Exception e) {
            return ToolResult.failure("Failed to fetch execution steps: " + e.getMessage());
        }
    }

    private String getRequiredStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(arg -> name.equals(arg.getName()))
                .map(ToolArgument::asString)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing required argument: " + name));
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(arg -> name.equals(arg.getName()))
                .map(ToolArgument::asString)
                .findFirst()
                .orElse(null);
    }

    private String getOptionalJsonArgument(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = arguments.stream()
                    .filter(arg -> name.equals(arg.getName()))
                    .findFirst()
                    .orElse(null);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof String stringValue) {
                return stringValue.isBlank() ? null : stringValue;
            }
            return JsonUtils.toJson(argument.getValue());
        }
        return null;
    }

    private Map<String, Object> exampleSchema() {
        return Map.of(
                "version", 1,
                "steps", List.of(
                        Map.of(
                                "stepId", "start",
                                "type", "TRIGGER",
                                "inputMapping", Map.of(),
                                "transitions", List.of(Map.of(
                                        "conditionExpression", "default",
                                        "targetStepId", "fetch"
                                ))
                        ),
                        Map.of(
                                "stepId", "fetch",
                                "type", "TOOL",
                                "actionIdentifier", "http-get",
                                "inputMapping", Map.of(
                                        "url", "https://example.com",
                                        "headers", Map.of()
                                ),
                                "transitions", List.of()
                        )
                ),
                "metadata", Map.of("purpose", "Fetch a page with http-get")
        );
    }
}
