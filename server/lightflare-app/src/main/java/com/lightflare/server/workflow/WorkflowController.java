package com.lightflare.server.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    public record CreateWorkflowRequest(
            String name,
            String description,
            String status,
            @JsonAlias({"schema_definition", "definitionJson", "definition_json"}) String schemaDefinition
    ) {
    }

    public record UpdateWorkflowRequest(
            String name,
            String description,
            String status,
            @JsonAlias({"schema_definition", "definitionJson", "definition_json"}) String schemaDefinition
    ) {
    }

    public record CreateTriggerRequest(
            String triggerType,
            String type,
            String name,
            Boolean enabled,
            @JsonAlias("config_json") String configJson
    ) {
    }

    public record UpdateTriggerRequest(
            String triggerType,
            String type,
            String name,
            Boolean enabled,
            @JsonAlias("config_json") String configJson
    ) {
    }

    public record EnableWorkflowRequest(boolean enabled) {
    }

    public record StartExecutionRequest(
            Map<String, Object> inputData,
            Map<String, Object> initialData,
            String startStepId
    ) {
    }

    public record TestStepRequest(WorkflowStepDefinition step, Map<String, Object> mockContext) {
    }

    @PostMapping
    public Workflow createWorkflow(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(
                request.name(),
                request.description(),
                request.schemaDefinition(),
                request.status(),
                null
        );
    }

    @GetMapping
    public List<Workflow> getWorkflows() {
        return workflowService.getAllWorkflows();
    }

    @GetMapping("/{id}")
    public Workflow getWorkflow(@PathVariable("id") String id) {
        return workflowService.getWorkflow(id);
    }

    @PutMapping("/{id}")
    public Workflow updateWorkflow(@PathVariable("id") String id, @RequestBody UpdateWorkflowRequest request) {
        return workflowService.updateWorkflow(
                id,
                request.name(),
                request.description(),
                request.schemaDefinition(),
                request.status()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable("id") String id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enabled")
    public Workflow setWorkflowEnabled(@PathVariable("id") String id, @RequestBody EnableWorkflowRequest request) {
        return workflowService.setWorkflowEnabled(id, request.enabled());
    }

    @PostMapping("/{id}/triggers")
    public WorkflowTrigger createTrigger(@PathVariable("id") String id, @RequestBody CreateTriggerRequest request) {
        String triggerType = request.triggerType() != null ? request.triggerType() : request.type();
        return workflowService.createTrigger(
                id,
                triggerType,
                request.name(),
                request.enabled() == null || request.enabled(),
                request.configJson()
        );
    }

    @GetMapping("/{id}/triggers")
    public List<WorkflowTrigger> getTriggers(@PathVariable("id") String id) {
        return workflowService.getTriggers(id);
    }

    @PutMapping("/{id}/triggers/{triggerId}")
    public WorkflowTrigger updateTrigger(@PathVariable("id") String id,
                                         @PathVariable("triggerId") String triggerId,
                                         @RequestBody UpdateTriggerRequest request) {
        String triggerType = request.triggerType() != null ? request.triggerType() : request.type();
        return workflowService.updateTrigger(
                id,
                triggerId,
                triggerType,
                request.name(),
                request.enabled(),
                request.configJson()
        );
    }

    @DeleteMapping("/{id}/triggers/{triggerId}")
    public ResponseEntity<Void> deleteTrigger(@PathVariable("id") String id,
                                              @PathVariable("triggerId") String triggerId) {
        workflowService.deleteTrigger(id, triggerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/runs")
    public List<WorkflowRun> getRuns(@PathVariable("id") String id,
                                     @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return workflowService.getRecentRuns(id, limit);
    }

    @PostMapping("/{id}/executions")
    public ResponseEntity<Map<String, String>> startExecution(@PathVariable("id") String id,
                                                              @RequestBody(required = false) StartExecutionRequest request) {
        Map<String, Object> initialData = Collections.emptyMap();
        if (request != null && request.inputData() != null) {
            initialData = request.inputData();
        } else if (request != null && request.initialData() != null) {
            initialData = request.initialData();
        }
        String executionId = workflowEngine.execute(
                id,
                initialData,
                request != null ? request.startStepId() : null
        );
        return ResponseEntity.ok(Map.of("executionId", executionId));
    }

    @PostMapping("/{id}/triggers/{triggerId}/executions")
    public ResponseEntity<Map<String, String>> startTriggerExecution(@PathVariable("id") String id,
                                                                     @PathVariable("triggerId") String triggerId,
                                                                     @RequestBody(required = false) StartExecutionRequest request) {
        WorkflowTrigger trigger = workflowService.getTriggerForWorkflow(id, triggerId);
        Map<String, Object> initialData = Collections.emptyMap();
        if (request != null && request.inputData() != null) {
            initialData = request.inputData();
        } else if (request != null && request.initialData() != null) {
            initialData = request.initialData();
        }
        String executionId = workflowEngine.execute(
                id,
                initialData,
                request != null ? request.startStepId() : null,
                null,
                trigger.getTriggerType(),
                trigger.getId(),
                trigger.getId()
        );
        return ResponseEntity.ok(Map.of("executionId", executionId));
    }

    @PostMapping("/test-step")
    public Object testStep(@RequestBody TestStepRequest request) {
        return workflowEngine.testStep(request.step(), request.mockContext());
    }
}
