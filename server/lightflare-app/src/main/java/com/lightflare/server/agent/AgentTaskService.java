package com.lightflare.server.agent;

import com.lightflare.server.agent.excecution.AgentExecutionListener;
import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.skill.SkillSelectionService;
import com.lightflare.server.agent.tool.ToolExecutionRouter;
import com.lightflare.server.agent.tool.ToolExecutionRouters;
import com.lightflare.server.agent.tool.ToolService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final ToolService toolService;
    private final SkillSelectionService skillSelectionService;
    private final AgentRunnerService agentRunnerService;

    public String executeStateless(String executionId, String userId, String task) {
        return executeStateless(executionId, userId, task, AgentExecutionListener.NOOP);
    }

    public String executeStateless(String executionId,
                                   String userId,
                                   String task,
                                   AgentExecutionListener listener) {
        String resolvedExecutionId = StringUtils.hasText(executionId) ? executionId : "task-" + UUID.randomUUID();
        ToolExecutionRouter toolExecutionRouter = ToolExecutionRouters.normal(toolService);
        AgentTaskRequest request = new AgentTaskRequest(
                resolvedExecutionId,
                AgentRunContext.EXECUTION_TYPE_TASK,
                AgentRunContext.REFERENCE_TYPE_TASK,
                resolvedExecutionId,
                userId,
                task,
                toolExecutionRouter.listTools(),
                toolExecutionRouter,
                new ConversationContext(null, List.of()),
                skillSelectionService.buildSkillContext(null),
                listener
        );
        return execute(request);
    }

    public String executeWorkflowStep(String executionId, String referenceId, String userId, String task) {
        String resolvedExecutionId = StringUtils.hasText(executionId) ? executionId : "workflow-" + UUID.randomUUID();
        ToolExecutionRouter toolExecutionRouter = ToolExecutionRouters.normal(toolService);
        AgentTaskRequest request = new AgentTaskRequest(
                resolvedExecutionId,
                AgentRunContext.EXECUTION_TYPE_WORKFLOW,
                AgentRunContext.REFERENCE_TYPE_WORKFLOW_STEP,
                StringUtils.hasText(referenceId) ? referenceId : resolvedExecutionId,
                userId,
                task,
                toolExecutionRouter.listTools(),
                toolExecutionRouter,
                new ConversationContext(null, List.of()),
                skillSelectionService.buildSkillContext(null),
                AgentExecutionListener.NOOP
        );
        return execute(request);
    }

    public String execute(AgentTaskRequest request) {
        return agentRunnerService.execute(request);
    }
}
