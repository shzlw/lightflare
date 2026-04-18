package com.lightflare.server.agent;

import com.lightflare.server.agent.excecution.AgentExecutionService;
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

    public String resume(AgentTaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("[{}][RESUME] executionId={}, executionType={}, referenceType={}, referenceId={}",
                LOG_STAGE,
                request.executionId(),
                request.executionType(),
                request.referenceType(),
                request.referenceId());
        return agentExecutionService.resume(
                request.executionType(),
                request.referenceType(),
                request.referenceId(),
                request.tools(),
                request.listener()
        );
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
                new AgentRunContext(
                        request.executionId(),
                        request.executionType(),
                        request.referenceType(),
                        request.referenceId(),
                        request.userId(),
                        request.task(),
                        request.tools()
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
