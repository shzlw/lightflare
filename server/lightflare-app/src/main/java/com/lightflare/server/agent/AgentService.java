package com.lightflare.server.agent;

import com.lightflare.server.agent.excecution.AgentExecutionListener;
import com.lightflare.server.agent.excecution.AgentExecutionService;
import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.memory.ConversationContextService;
import com.lightflare.server.agent.skill.SkillContext;
import com.lightflare.server.agent.skill.SkillSelectionService;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.chat.CreateChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String LOG_STAGE = "AGENT_REQ";

    private final ToolService toolService;
    private final ConversationContextService conversationContextService;
    private final SkillSelectionService skillSelectionService;
    private final AgentExecutionService agentExecutionService;

    public String process(CreateChatRequest request) {
        return process(request, AgentExecutionListener.NOOP);
    }

    public String process(CreateChatRequest request, AgentExecutionListener listener) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("[{}][START] sessionId={}, userId={}", LOG_STAGE, request.getSessionId(), request.getUserId());

        AgentRunContext runContext = new AgentRunContext(request, toolService.listTools());
        log.info("[{}][TOOLS_READY] sessionId={}, toolCount={}", LOG_STAGE, runContext.sessionId(), runContext.tools().size());
        if (agentExecutionService.hasResumableCheckpoint(runContext.sessionId())) {
            log.info("[{}][RESUME] sessionId={} has a running execution checkpoint", LOG_STAGE, runContext.sessionId());
            String response = agentExecutionService.resume(
                    runContext.sessionId(),
                    runContext.tools(),
                    listener != null ? listener : AgentExecutionListener.NOOP
            );
            conversationContextService.persistAssistantResponse(runContext, response);
            log.info("[{}][COMPLETE] sessionId={}, responseLength={}",
                    LOG_STAGE,
                    runContext.sessionId(), response != null ? response.length() : 0);
            return response;
        }

        ConversationContext conversationContext = conversationContextService.prepare(runContext);
        log.info("[{}][CONTEXT_READY] sessionId={}, promptMemoryCount={}",
                LOG_STAGE,
                conversationContext.promptMemories().size(), runContext.sessionId());
        SkillContext skillContext = skillSelectionService.buildSkillContext(conversationContext.currentMemory());
        log.info("[{}][SKILLS_READY] sessionId={}, availableSkillCount={}",
                LOG_STAGE,
                skillContext.availableSkills().size(), runContext.sessionId());

        String response = agentExecutionService.execute(
                runContext,
                conversationContext,
                skillContext,
                listener != null ? listener : AgentExecutionListener.NOOP
        );
        conversationContextService.persistAssistantResponse(runContext, response);
        log.info("[{}][COMPLETE] sessionId={}, responseLength={}",
                LOG_STAGE,
                runContext.sessionId(), response != null ? response.length() : 0);
        return response;
    }
}
