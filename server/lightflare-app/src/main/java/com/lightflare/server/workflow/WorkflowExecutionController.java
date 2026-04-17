package com.lightflare.server.workflow;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/executions")
public class WorkflowExecutionController {

    private final WorkflowService workflowService;

    @GetMapping("/{id}")
    public WorkflowRun getExecution(@PathVariable("id") String id) {
        return workflowService.getRun(id);
    }

    @GetMapping("/{id}/steps")
    public List<WorkflowStepRun> getExecutionSteps(@PathVariable("id") String id) {
        return workflowService.getStepRuns(id);
    }
}
