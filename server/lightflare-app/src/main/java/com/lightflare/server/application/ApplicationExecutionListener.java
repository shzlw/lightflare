package com.lightflare.server.application;

import java.util.Map;

public interface ApplicationExecutionListener {

    ApplicationExecutionListener NOOP = new ApplicationExecutionListener() {
    };

    default void runStarted(String runId) {
    }

    default void stepStarted(String runId, String stepRunId, ApplicationStepDefinition step, Map<String, Object> input) {
    }

    default void stepCompleted(String runId, String stepRunId, ApplicationStepDefinition step, Object output) {
    }

    default void stepFailed(String runId, String stepRunId, ApplicationStepDefinition step, String errorMessage) {
    }

    default void runCompleted(String runId, Map<String, Object> output) {
    }

    default void runFailed(String runId, String errorMessage) {
    }
}
