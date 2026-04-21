package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.harness.core.event.HarnessExecutionListener;
import com.lightflare.server.harness.core.tool.ToolExecutionRouter;
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
        HarnessExecutionListener listener
) {
}
