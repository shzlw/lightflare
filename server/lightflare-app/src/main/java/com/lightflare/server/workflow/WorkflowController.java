package com.lightflare.server.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowService workflowService, WorkflowEngine workflowEngine) {
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
    }

    public record CreateWorkflowRequest(String name, String description,
            @JsonAlias("schema_definition") String schemaDefinition) {}
    public record UpdateWorkflowRequest(String name, String description,
            @JsonAlias("schema_definition") String schemaDefinition) {}
    public record StartExecutionRequest(Map<String, Object> initialData, String startStepId) {}

    @PostMapping
    public ResponseEntity<Workflow> createWorkflow(@RequestBody CreateWorkflowRequest request) {
        String schema = request.schemaDefinition() != null ? request.schemaDefinition() : "{\"version\":1,\"steps\":[]}";
        return ResponseEntity.ok(workflowService.createWorkflow(request.name(), request.description(), schema));
    }

    @GetMapping
    public ResponseEntity<List<Workflow>> getWorkflows() {
        return ResponseEntity.ok(workflowService.getAllWorkflows());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String id) {
        return ResponseEntity.ok(workflowService.getWorkflow(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Workflow> updateWorkflow(@PathVariable("id") String id, @RequestBody UpdateWorkflowRequest request) {
        Workflow current = workflowService.getWorkflow(id);
        String name = request.name() != null ? request.name() : current.getName();
        String description = request.description() != null ? request.description() : current.getDescription();
        String schema = request.schemaDefinition() != null ? request.schemaDefinition() : current.getSchemaDefinition();
        return ResponseEntity.ok(workflowService.updateWorkflow(id, name, description, schema));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable("id") String id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/executions")
    public ResponseEntity<Map<String, String>> startExecution(@PathVariable("id") String id,
            @RequestBody(required = false) StartExecutionRequest request) {
        Map<String, Object> initialData = request != null && request.initialData() != null
                ? request.initialData()
                : Collections.emptyMap();
        String startStepId = request != null ? request.startStepId() : null;
        String executionId = workflowEngine.execute(id, initialData, startStepId);
        return ResponseEntity.ok(Map.of("executionId", executionId));
    }

    public record TestStepRequest(WorkflowStepDefinition step, Map<String, Object> mockContext) {}

    @PostMapping("/test-step")
    public ResponseEntity<Object> testStep(@RequestBody TestStepRequest request) {
        return ResponseEntity.ok(workflowEngine.testStep(request.step(), request.mockContext()));
    }
}
