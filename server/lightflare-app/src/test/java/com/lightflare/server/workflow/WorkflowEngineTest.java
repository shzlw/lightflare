package com.lightflare.server.workflow;

import com.lightflare.server.agent.AgentTaskService;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowEngineTest {

    private FakeWorkflowService workflowService;
    private FakeToolService toolService;
    private FakeAgentTaskService agentTaskService;
    private WorkflowEngine workflowEngine;

    @BeforeEach
    void setUp() {
        workflowService = new FakeWorkflowService();
        toolService = new FakeToolService();
        agentTaskService = new FakeAgentTaskService();
        workflowEngine = new WorkflowEngine(
                workflowService,
                repositoryProxy(WorkflowRunRepository.class),
                repositoryProxy(WorkflowStepRunRepository.class),
                toolService,
                agentTaskService
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolStepInputsResolveInputsAndPreviousStepOutputWithoutStringifyingExactTemplates() {
        Workflow workflow = workflow("""
                {
                  "version": 1,
                  "inputs": [
                    {"name": "url", "default": "https://example.com"}
                  ],
                  "steps": [
                    {
                      "id": "fetch_page",
                      "name": "Fetch page",
                      "type": "tool",
                      "toolName": "fetch-tool",
                      "input": {
                        "url": "{{inputs.url}}"
                      }
                    },
                    {
                      "id": "archive_page",
                      "name": "Archive page",
                      "type": "tool",
                      "toolName": "archive-tool",
                      "input": {
                        "payload": "{{steps.fetch_page.output}}",
                        "title": "{{steps.fetch_page.output.content.title}}",
                        "note": "Archive {{steps.fetch_page.output.content.title}} from {{inputs.url}}"
                      }
                    }
                  ]
                }
                """);
        workflowService.workflow = workflow;
        toolService.handler = (toolName, arguments) -> {
            if ("fetch-tool".equals(toolName)) {
                return ToolResult.success("""
                        {"content":{"title":"Example title","items":["one","two"]},"status":200}
                        """);
            }
            return ToolResult.success("{\"archived\":true}");
        };

        workflowEngine.execute("workflow-1", Map.of(), null, "user-1", "manual", null);

        Map<String, Object> values = argumentsByName(toolService.argumentsFor("archive-tool"));

        Object payload = values.get("payload");
        assertInstanceOf(Map.class, payload);
        assertEquals("Example title", ((Map<String, Object>) ((Map<String, Object>) payload).get("content")).get("title"));
        assertEquals("Example title", values.get("title"));
        assertEquals("Archive Example title from https://example.com", values.get("note"));
    }

    @Test
    void llmStepPromptEmbedsToolOutputAsJson() {
        Workflow workflow = workflow("""
                {
                  "version": 1,
                  "steps": [
                    {
                      "id": "fetch_page",
                      "name": "Fetch page",
                      "type": "tool",
                      "toolName": "fetch-tool",
                      "input": {
                        "url": "https://example.com"
                      }
                    },
                    {
                      "id": "summarize_page",
                      "name": "Summarize page",
                      "type": "llm",
                      "prompt": "Summarize this tool output: {{steps.fetch_page.output}}"
                    }
                  ]
                }
                """);
        workflowService.workflow = workflow;
        toolService.handler = (toolName, arguments) -> ToolResult.success("{\"content\":{\"title\":\"Example title\"}}");
        agentTaskService.response = "{\"summary\":\"done\"}";

        workflowEngine.execute("workflow-1");

        assertEquals("summarize_page", agentTaskService.referenceId);
        assertTrue(agentTaskService.task.contains("Summarize this tool output: {\"content\":{\"title\":\"Example title\"}}"));
    }

    private Workflow workflow(String definitionJson) {
        Workflow workflow = new Workflow();
        workflow.setId("workflow-1");
        workflow.setName("Workflow");
        workflow.setStatus("active");
        workflow.setSchemaDefinition(definitionJson);
        return workflow;
    }

    private Map<String, Object> argumentsByName(List<ToolArgument> arguments) {
        return arguments.stream().collect(java.util.stream.Collectors.toMap(
                ToolArgument::getName,
                ToolArgument::getValue
        ));
    }

    @SuppressWarnings("unchecked")
    private <T> T repositoryProxy(Class<T> repositoryType) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                (proxy, method, args) -> method.getReturnType().equals(int.class) ? 1 : null
        );
    }

    private static class FakeWorkflowService extends WorkflowService {

        private Workflow workflow;

        FakeWorkflowService() {
            super(null, null, null, null);
        }

        @Override
        public Workflow getWorkflow(String id) {
            return workflow;
        }
    }

    private static class FakeToolService extends ToolService {

        private final java.util.Map<String, List<ToolArgument>> calls = new java.util.HashMap<>();
        private BiFunction<String, List<ToolArgument>, ToolResult> handler = (toolName, arguments) -> ToolResult.success("{}");

        FakeToolService() {
            super(null, null);
        }

        @Override
        public ToolResult execute(String toolName, List<ToolArgument> arguments, String userId) {
            calls.put(toolName, arguments);
            return handler.apply(toolName, arguments);
        }

        private List<ToolArgument> argumentsFor(String toolName) {
            return calls.get(toolName);
        }
    }

    private static class FakeAgentTaskService extends AgentTaskService {

        private String referenceId;
        private String task;
        private String response = "{}";

        FakeAgentTaskService() {
            super(null, null, null);
        }

        @Override
        public String executeWorkflowStep(String executionId, String referenceId, String userId, String task) {
            this.referenceId = referenceId;
            this.task = task;
            return response;
        }
    }
}
