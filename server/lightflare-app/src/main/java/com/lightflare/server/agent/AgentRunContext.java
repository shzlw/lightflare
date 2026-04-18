package com.lightflare.server.agent;

import com.lightflare.server.tools.core.ToolDefinition;

import java.util.List;

public record AgentRunContext(
        String executionId,
        String executionType,
        String referenceType,
        String referenceId,
        String userId,
        String task,
        List<ToolDefinition> tools
) {

    public static final String EXECUTION_TYPE_CHAT = "agent_chat";
    public static final String EXECUTION_TYPE_WORKFLOW = "agent_workflow";
    public static final String EXECUTION_TYPE_TASK = "agent_task";
    public static final String REFERENCE_TYPE_CHAT_SESSION = "chat_session";
    public static final String REFERENCE_TYPE_WORKFLOW_STEP = "workflow_step";
    public static final String REFERENCE_TYPE_TASK = "task";

    public AgentRunContext {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
