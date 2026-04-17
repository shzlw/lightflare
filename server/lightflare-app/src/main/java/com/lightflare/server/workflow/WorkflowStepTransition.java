package com.lightflare.server.workflow;

public record WorkflowStepTransition(
    String conditionExpression,
    String targetStepId
) {}
