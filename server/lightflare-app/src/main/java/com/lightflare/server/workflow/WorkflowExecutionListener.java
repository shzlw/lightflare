package com.lightflare.server.workflow;

import java.util.Map;

public interface WorkflowExecutionListener {

    WorkflowExecutionListener NOOP = new WorkflowExecutionListener() {
    };

    default void runStarted(String runId) {
    }

    default void stepStarted(String runId, String stepRunId, WorkflowStepDefinition step, Map<String, Object> input) {
    }

    default void stepCompleted(String runId, String stepRunId, WorkflowStepDefinition step, Object output) {
    }

    default void stepFailed(String runId, String stepRunId, WorkflowStepDefinition step, String errorMessage) {
    }

    default void runCompleted(String runId, Map<String, Object> output) {
    }

    default void runFailed(String runId, String errorMessage) {
    }
}
