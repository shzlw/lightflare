package com.lightflare.server.agent;

import com.lightflare.server.agent.excecution.AgentExecutionListener;
import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.memory.ConversationContextService;
import com.lightflare.server.agent.skill.SkillContext;
import com.lightflare.server.agent.skill.SkillSelectionService;
import com.lightflare.server.agent.tool.InternalToolService;
import com.lightflare.server.agent.tool.ToolExecutionRouter;
import com.lightflare.server.agent.tool.ToolExecutionRouters;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.chat.CreateChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String LOG_STAGE = "AGENT_REQ";

    private final ToolService toolService;
    private final InternalToolService internalToolService;
    private final ConversationContextService conversationContextService;
    private final SkillSelectionService skillSelectionService;
    private final AgentRunnerService agentRunnerService;

    public String process(CreateChatRequest request) {
        return process(request, AgentExecutionListener.NOOP);
    }

    public String process(CreateChatRequest request, AgentExecutionListener listener) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("[{}][START] sessionId={}, userId={}", LOG_STAGE, request.getSessionId(), request.getUserId());
        ToolExecutionRouter toolExecutionRouter = ToolExecutionRouters.combined(toolService, internalToolService);

        AgentRunContext runContext = new AgentRunContext(
                request.getSessionId(),
                AgentRunContext.EXECUTION_TYPE_CHAT,
                AgentRunContext.REFERENCE_TYPE_CHAT_SESSION,
                request.getSessionId(),
                request.getUserId(),
                request.getData(),
                toolExecutionRouter.listTools(),
                toolExecutionRouter
        );
        log.info("[{}][TOOLS_READY] sessionId={}, toolCount={}", LOG_STAGE, runContext.executionId(), runContext.tools().size());
        AgentTaskRequest taskRequest = new AgentTaskRequest(
                runContext.executionId(),
                runContext.executionType(),
                runContext.referenceType(),
                runContext.referenceId(),
                runContext.userId(),
                runContext.task(),
                runContext.tools(),
                runContext.toolExecutionRouter(),
                new ConversationContext(null, List.of()),
                skillSelectionService.buildSkillContext(null),
                listener
        );
        if (agentRunnerService.hasWaitingForUserCheckpoint(taskRequest)) {
            log.info("[{}][RESUME_WAITING] sessionId={} has a waiting execution checkpoint", LOG_STAGE, runContext.executionId());
            ConversationContext conversationContext = conversationContextService.prepare(runContext);
            SkillContext skillContext = skillSelectionService.buildSkillContext(conversationContext.currentMemory());
            String response = agentRunnerService.resume(new AgentTaskRequest(
                    runContext.executionId(),
                    runContext.executionType(),
                    runContext.referenceType(),
                    runContext.referenceId(),
                    runContext.userId(),
                    runContext.task(),
                    runContext.tools(),
                    runContext.toolExecutionRouter(),
                    conversationContext,
                    skillContext,
                    listener
            ));
            conversationContextService.persistAssistantResponse(runContext, response);
            log.info("[{}][COMPLETE] sessionId={}, responseLength={}",
                    LOG_STAGE,
                    runContext.executionId(), response != null ? response.length() : 0);
            return response;
        }

        if (agentRunnerService.hasResumableCheckpoint(taskRequest)) {
            log.info("[{}][RESUME] sessionId={} has a running execution checkpoint", LOG_STAGE, runContext.executionId());
            String response = agentRunnerService.resume(taskRequest);
            conversationContextService.persistAssistantResponse(runContext, response);
            log.info("[{}][COMPLETE] sessionId={}, responseLength={}",
                    LOG_STAGE,
                    runContext.executionId(), response != null ? response.length() : 0);
            return response;
        }

        ConversationContext conversationContext = conversationContextService.prepare(runContext);
        log.info("[{}][CONTEXT_READY] sessionId={}, promptMemoryCount={}",
                LOG_STAGE,
                conversationContext.promptMemories().size(), runContext.executionId());
        SkillContext skillContext = skillSelectionService.buildSkillContext(conversationContext.currentMemory());
        log.info("[{}][SKILLS_READY] sessionId={}, availableSkillCount={}",
                LOG_STAGE,
                skillContext.availableSkills().size(), runContext.executionId());

        String response = agentRunnerService.execute(new AgentTaskRequest(
                runContext.executionId(),
                runContext.executionType(),
                runContext.referenceType(),
                runContext.referenceId(),
                runContext.userId(),
                runContext.task(),
                runContext.tools(),
                runContext.toolExecutionRouter(),
                conversationContext,
                skillContext,
                listener
        ));
        conversationContextService.persistAssistantResponse(runContext, response);
        log.info("[{}][COMPLETE] sessionId={}, responseLength={}",
                LOG_STAGE,
                runContext.executionId(), response != null ? response.length() : 0);
        return response;
    }

}
