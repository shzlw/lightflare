package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
            <purpose>
            Use this tool when the user wants to change an existing workflow's name, description, status, steps, inputs, or full definition.
            </purpose>

            <required_context>
            Requires workflow_id.
            If the current workflow definition is not already in context, first use inspect-workflow with action="get" to retrieve the workflow, triggers, and recent runs.
            Preserve existing step ids, input names, trigger intent, and unrelated fields unless the user asks to replace them.
            </required_context>

            <definition_updates>
            Send the full updated `definition_json`, not a partial patch, when changing steps or inputs.
            Preferred definition_json structure:
            {
              "version": 1,
              "inputs": [],
              "steps": [
                {
                  "id": "step_id",
                  "name": "Step name",
                  "type": "llm",
                  "prompt": "Runtime instruction.",
                  "onError": "stop"
                }
              ]
            }
            </definition_updates>

            <examples>
            User: "Add a final email step to this workflow."
            Tool call:
            {
              "workflow_id": "wf_123",
              "name": "Daily task summary",
              "description": "Summarize tasks and email the summary.",
              "status": "active",
              "definition_json": {
                "version": 1,
                "inputs": [
                  {"name": "email", "label": "Email", "type": "string", "required": true}
                ],
                "steps": [
                  {
                    "id": "summarize_tasks",
                    "name": "Summarize open tasks",
                    "type": "llm",
                    "prompt": "Find my open tasks and summarize what needs attention today.",
                    "onError": "stop"
                  },
                  {
                    "id": "send_summary",
                    "name": "Send summary email",
                    "type": "tool",
                    "toolName": "email.send",
                    "input": {
                      "to": "{{inputs.email}}",
                      "subject": "Daily task summary",
                      "body": "{{steps.summarize_tasks.output.text}}"
                    },
                    "onError": "stop"
                  }
                ]
              }
            }
            </examples>

            <rules>
            Use create-workflow for new workflows.
            Use manage-workflow-trigger for trigger-only changes.
            Use enable-workflow for simple enable/disable requests.
            </rules>
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("update-workflow")
            .description("Update an existing workflow definition or metadata.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id to update.", true),
                    WorkflowToolDefinitions.input("name", "string", "Updated workflow name.", false),
                    WorkflowToolDefinitions.input("description", "string", "Updated workflow description.", false),
                    WorkflowToolDefinitions.input("status", "string", "draft, active, or disabled.", false),
                    WorkflowToolDefinitions.input("definition_json", "object", "Full updated workflow definition JSON object or JSON string.", false),
                    WorkflowToolDefinitions.input("triggers", "array", "Optional trigger definitions to create while updating.", false)
            ))
            .build();

    private final ManageWorkflowTool workflowOperations;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        try {
            return workflowOperations.updateWorkflow(arguments, context);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
