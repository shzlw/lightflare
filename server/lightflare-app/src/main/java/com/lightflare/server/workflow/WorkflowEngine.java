package com.lightflare.server.workflow;

import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowService workflowService;
    private final WorkflowRunRepository runRepository;

    public String execute(String workflowId) {
        return execute(workflowId, Collections.emptyMap(), null);
    }

    public String execute(String workflowId, Map<String, Object> initialData) {
        return execute(workflowId, initialData, null);
    }

    public String execute(String workflowId, Map<String, Object> initialData, String startStepId) {
        return execute(workflowId, initialData, startStepId, null, "manual", null);
    }

    public String execute(String workflowId,
                          Map<String, Object> initialData,
                          String startStepId,
                          String userId,
                          String triggerType,
                          String sourceId) {
        return execute(workflowId, initialData, startStepId, userId, triggerType, sourceId, null);
    }

    public String execute(String workflowId,
                          Map<String, Object> initialData,
                          String startStepId,
                          String userId,
                          String triggerType,
                          String sourceId,
                          String triggerId) {
        Workflow workflow = workflowService.getWorkflow(workflowId);
        String runId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> input = initialData != null ? initialData : Collections.emptyMap();
        Map<String, Object> output = new HashMap<>();
        output.put("message", "Workflow persistence is ready; step execution is not implemented yet.");
        output.put("startStepId", startStepId);
        runRepository.insertRun(
                runId,
                workflow.getId(),
                triggerId,
                triggerType != null ? triggerType : "manual",
                "COMPLETED",
                JsonUtils.toJson(input),
                JsonUtils.toJson(output),
                null,
                userId,
                sourceId,
                now,
                now,
                now
        );
        return runId;
    }

    public Object testStep(WorkflowStepDefinition step, Map<String, Object> mockContext) {
        return Map.of(
                "stepId", step != null ? step.resolvedId() : null,
                "input", mockContext != null ? mockContext : Collections.emptyMap(),
                "message", "Workflow step execution is not implemented yet."
        );
    }
}
