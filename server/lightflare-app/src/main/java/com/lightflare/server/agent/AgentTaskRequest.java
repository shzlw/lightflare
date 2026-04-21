package com.lightflare.server.agent;

import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.skill.SkillContext;
import com.lightflare.server.harness.core.event.HarnessExecutionListener;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import com.lightflare.server.harness.core.tool.ToolExecutionRouter;
import com.lightflare.server.tools.core.ToolDefinition;
import java.util.List;

public record AgentTaskRequest(
        String executionId,
        String executionType,
        String referenceType,
        String referenceId,
        String userId,
        String task,
        List<ToolDefinition> tools,
        ToolExecutionRouter toolExecutionRouter,
        ConversationContext conversationContext,
        SkillContext skillContext,
        HarnessExecutionListener listener
) {

    public AgentTaskRequest {
        executionType = executionType != null ? executionType : HarnessRunContext.EXECUTION_TYPE_TASK;
        referenceType = referenceType != null ? referenceType : HarnessRunContext.REFERENCE_TYPE_TASK;
        referenceId = referenceId != null ? referenceId : executionId;
        tools = tools == null ? List.of() : List.copyOf(tools);
        listener = listener != null ? listener : HarnessExecutionListener.NOOP;
    }
}
