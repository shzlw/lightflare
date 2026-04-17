package com.lightflare.server.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(
        int version,
        List<Map<String, Object>> inputs,
        List<WorkflowStepDefinition> steps,
        List<Map<String, Object>> triggers
) {
}
