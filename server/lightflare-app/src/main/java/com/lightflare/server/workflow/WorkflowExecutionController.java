package com.lightflare.server.workflow;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/executions")
public class WorkflowExecutionController {
    private final WorkflowService workflowService;

    public WorkflowExecutionController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/{execId}")
    public ResponseEntity<WorkflowExecution> getExecution(@PathVariable("execId") String execId) {
        return ResponseEntity.ok(workflowService.getExecution(execId));
    }

    @GetMapping("/{execId}/steps")
    public ResponseEntity<List<WorkflowStepExecution>> getExecutionSteps(@PathVariable("execId") String execId) {
        return ResponseEntity.ok(workflowService.getExecutionSteps(execId));
    }
}
