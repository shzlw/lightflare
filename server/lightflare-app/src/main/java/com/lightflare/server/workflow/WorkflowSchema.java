package com.lightflare.server.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowSchema(
    int version,
    List<WorkflowStepDefinition> steps,
    Map<String, Object> metadata
) {}
