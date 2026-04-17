package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.lightflare.server.utils.JsonUtils;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowStepExecutionRepository stepExecutionRepository;
    private final WorkflowValidator workflowValidator;

    public WorkflowService(WorkflowRepository workflowRepository, WorkflowExecutionRepository executionRepository,
            WorkflowStepExecutionRepository stepExecutionRepository, WorkflowValidator workflowValidator) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.workflowValidator = workflowValidator;
    }

    public Workflow createWorkflow(String name, String description, String schemaDefinition) {
        validateSchemaDefinition(schemaDefinition);
        String id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        workflowRepository.insertWorkflow(id, name, description, schemaDefinition, now, now);
        return workflowRepository.findById(id).orElseThrow();
    }

    public Workflow updateWorkflow(String id, String name, String description, String schemaDefinition) {
        validateSchemaDefinition(schemaDefinition);
        workflowRepository.updateWorkflow(id, name, description, schemaDefinition, OffsetDateTime.now());
        return workflowRepository.findById(id).orElseThrow();
    }

    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAllOrdered();
    }

    public Workflow getWorkflow(String id) {
        return workflowRepository.findById(id).orElseThrow();
    }

    public void deleteWorkflow(String id) {
        workflowRepository.deleteWorkflowById(id);
    }

    public List<WorkflowStepExecution> getExecutionSteps(String executionId) {
        return stepExecutionRepository.findByWorkflowExecutionId(executionId);
    }

    public WorkflowExecution getExecution(String executionId) {
        return executionRepository.findById(executionId).orElseThrow();
    }

    private void validateSchemaDefinition(String schemaDefinition) {
        workflowValidator.validate(JsonUtils.fromJson(schemaDefinition, WorkflowSchema.class));
    }
}
