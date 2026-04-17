package com.lightflare.server.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowStepDefinition(
    String stepId,
    String type,
    String actionIdentifier,
    Map<String, Object> inputMapping,
    Map<String, Object> outputMapping,
    List<WorkflowStepTransition> transitions,
    Map<String, Object> metadata
) {}
