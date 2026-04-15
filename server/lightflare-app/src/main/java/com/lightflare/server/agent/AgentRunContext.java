package com.lightflare.server.agent;

import com.lightflare.server.chat.CreateChatRequest;
import com.lightflare.server.tools.core.ToolDefinition;

import java.util.List;

public record AgentRunContext(CreateChatRequest request, List<ToolDefinition> tools) {

    public AgentRunContext {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public String task() {
        return request.getData();
    }

    public String sessionId() {
        return request.getSessionId();
    }

    public String userId() {
        return request.getUserId();
    }
}
