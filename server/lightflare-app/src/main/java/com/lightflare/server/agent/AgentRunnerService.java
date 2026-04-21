package com.lightflare.server.agent;

import com.lightflare.server.agent.excecution.AgentExecutionService;
import com.lightflare.server.agent.excecution.ResumeExecutionRequest;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunnerService {

    private static final String LOG_STAGE = "AGENT_RUNNER";

    private final AgentExecutionService agentExecutionService;

    public boolean hasResumableCheckpoint(AgentTaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return agentExecutionService.hasResumableCheckpoint(
                request.executionType(),
                request.referenceType(),
                request.referenceId()
        );
    }

    public boolean hasWaitingForUserCheckpoint(AgentTaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return agentExecutionService.hasWaitingForUserCheckpoint(
                request.executionType(),
                request.referenceType(),
                request.referenceId()
        );
    }

    public String resume(AgentTaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("[{}][RESUME] executionId={}, executionType={}, referenceType={}, referenceId={}",
                LOG_STAGE,
                request.executionId(),
                request.executionType(),
                request.referenceType(),
                request.referenceId());
        return agentExecutionService.resume(new ResumeExecutionRequest(
                request.executionType(),
                request.referenceType(),
                request.referenceId(),
                request.task(),
                request.conversationContext(),
                request.tools(),
                request.toolExecutionRouter(),
                request.listener()
        ));
    }

    public String execute(AgentTaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("[{}][START] executionId={}, executionType={}, referenceType={}, referenceId={}, userId={}, toolCount={}",
                LOG_STAGE,
                request.executionId(),
                request.executionType(),
                request.referenceType(),
                request.referenceId(),
                request.userId(),
                request.tools().size());

        String response = agentExecutionService.execute(
                new HarnessRunContext(
                        request.executionId(),
                        request.executionType(),
                        request.referenceType(),
                        request.referenceId(),
                        request.userId(),
                        request.task(),
                        request.tools(),
                        request.toolExecutionRouter()
                ),
                request.conversationContext(),
                request.skillContext(),
                request.listener()
        );
        log.info("[{}][COMPLETE] executionId={}, responseLength={}",
                LOG_STAGE,
                request.executionId(),
                response != null ? response.length() : 0);
        return response;
    }
}
