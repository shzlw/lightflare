package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.tool.ToolExecutionRouter;
import com.lightflare.server.tools.core.ToolDefinition;
import java.util.List;

public record ResumeExecutionRequest(
        String executionType,
        String referenceType,
        String referenceId,
        String userInput,
        ConversationContext conversationContext,
        List<ToolDefinition> tools,
        ToolExecutionRouter toolExecutionRouter,
        AgentExecutionListener listener
) {
}
