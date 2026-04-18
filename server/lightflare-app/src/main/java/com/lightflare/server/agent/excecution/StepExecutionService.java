package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.agent.plan.AgentPlanner;
import com.lightflare.server.agent.tool.ToolCallExecutor;
import com.lightflare.server.config.AgentProperties;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.llmproviders.core.LLMStepResponse;
import com.lightflare.server.agent.prompts.StepExecutionStatePrompt;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepExecutionService {

    private static final String LOG_STAGE = "STEP_EXEC";

    private final AgentProperties agentProperties;
    private final AgentPlanner agentPlanner;
    private final StepResponseCanonicalizer stepResponseCanonicalizer;
    private final ToolCallExecutor toolCallExecutor;
    private final PlanStepFormatter planStepFormatter;

    public StepExecutionResult executeStepWithRetries(AgentRunContext runContext,
                                                      StepExecutionContext executionContext,
                                                      LLMPlanResponse.PlanStep step,
                                                      AgentExecutionListener listener) {
        AgentExecutionListener executionListener = listener != null ? listener : AgentExecutionListener.NOOP;
        if (step == null) {
            throw new IllegalStateException("Current step snapshot must not be null");
        }

        List<String> runEntries = new ArrayList<>();
        boolean stepCompleted = false;
        boolean successfulToolResultSeen = false;
        StepExecutionStatePrompt stepState = new StepExecutionStatePrompt();
        List<String> dependencyContext = executionContext.dependencyContextFor(step);
        HashSet<String> executedToolCallSignatures = new HashSet<>();

        int maxStepAttempts = agentProperties.getMaxStepAttempts();
        for (int attempt = 1; attempt <= maxStepAttempts; attempt++) {
            stepState.setAttemptNumber(attempt);
            executionListener.onStepProgress(
                    runContext.executionId(),
                    cloneStep(step),
                    "ATTEMPT_START",
                    "Attempt " + attempt + " of " + maxStepAttempts
            );
            log.info("[{}][ATTEMPT_START] sessionId={}, stepId={}, attempt={}/{}, stepTitle={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    step.getId(),
                    attempt,
                    maxStepAttempts,
                    step.getContent());
            LLMStepResponse stepResponse;
            try {
                stepResponse = agentPlanner.executeStep(
                        runContext.executionId(),
                        runContext.executionType(),
                        runContext.userId(),
                        executionContext,
                        step,
                        stepState,
                        dependencyContext,
                        List.copyOf(runEntries)
                );
            } catch (Exception e) {
                log.error("[{}][STEP_EXEC_ERROR] sessionId={}, stepId={}, error={}",
                        LOG_STAGE, runContext.executionId(), step.getId(), e.getMessage(), e);
                String errorEntry = planStepFormatter.formatStepEntry(step, "ERROR", "Unexpected execution error: " + e.getMessage());
                runEntries.add(errorEntry);
                return finalizeStepResult(
                        step,
                        LLMPlanResponse.PlanStep.Status.FAILED,
                        runEntries,
                        "Execution error: " + e.getMessage(),
                        executionListener,
                        runContext.executionId()
                );
            }

            if (stepResponse == null || stepResponse.getAction() == null) {
                throw new IllegalStateException("Step response action must not be null");
            }
            stepResponse = stepResponseCanonicalizer.canonicalize(stepResponse, stepState);
            stepResponse = toolCallExecutor.normalizeStepResponse(runContext, stepResponse);
            if (StringUtils.hasText(stepResponse.getThoughtProcess())) {
                executionListener.onStepProgress(
                        runContext.executionId(),
                        cloneStep(step),
                        "THOUGHT",
                        stepResponse.getThoughtProcess()
                );
            }
            log.info("[{}][ATTEMPT_RESULT] sessionId={}, stepId={}, action={}, stepComplete={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    step.getId(),
                    stepResponse.getAction(),
                    stepResponse.getStepComplete());

            switch (stepResponse.getAction()) {
                case USE_TOOL -> {
                    String toolName = stepResponse.getToolCall() != null ? stepResponse.getToolCall().getToolName() : null;
                    executionListener.onStepProgress(
                            runContext.executionId(),
                            cloneStep(step),
                            "TOOL_CALL",
                            "Calling tool: " + (StringUtils.hasText(toolName) ? toolName : "unknown")
                    );
                    ToolResult toolResult = toolCallExecutor.handleToolAction(
                            runContext,
                            step,
                            stepResponse,
                            runEntries,
                            executedToolCallSignatures
                    );
                    successfulToolResultSeen = successfulToolResultSeen || (toolResult != null && toolResult.success());
                    stepState.setSuccessfulToolResultAvailable(successfulToolResultSeen);
                    stepState.setLatestToolName(toolName);
                    stepState.setLatestToolOutcome(toolResult != null && toolResult.success() ? "SUCCESS" : "FAILURE");
                    stepState.setLatestToolResult(toolResult != null ? toolResult.content() : null);
                    executionListener.onStepProgress(
                            runContext.executionId(),
                            cloneStep(step),
                            toolResult != null && toolResult.success() ? "TOOL_SUCCESS" : "TOOL_FAILURE",
                            toolResult != null ? toolResult.content() : "Tool execution returned no result"
                    );
                }
                case REQUEST_TOOL_INPUT -> {
                    String missingInputResponse = toolCallExecutor.buildMissingToolInputResponse(stepResponse);
                    executionListener.onStepProgress(
                            runContext.executionId(),
                            cloneStep(step),
                            "REQUEST_TOOL_INPUT",
                            missingInputResponse
                    );
                    log.info("[{}][MISSING_TOOL_INPUT] sessionId={}, stepId={}, missingInputs={}",
                            LOG_STAGE,
                            runContext.executionId(),
                            step.getId(),
                            stepResponse.getMissingInputs());
                    return finalizeStepResult(
                            step,
                            LLMPlanResponse.PlanStep.Status.FAILED,
                            runEntries,
                            missingInputResponse,
                            executionListener,
                            runContext.executionId()
                    );
                }
                case DIRECT_RESPONSE -> {
                    if (StringUtils.hasText(stepResponse.getResponse())) {
                        String responseType = Boolean.TRUE.equals(stepResponse.getStepComplete()) ? "STEP_RESULT" : "PARTIAL_RESPONSE";
                        String responseMessage = planStepFormatter.formatStepEntry(step, responseType, stepResponse.getResponse());
                        runEntries.add(responseMessage);
                        executionListener.onStepProgress(
                                runContext.executionId(),
                                cloneStep(step),
                                responseType,
                                stepResponse.getResponse()
                        );
                    }

                    boolean shouldCompleteStep = Boolean.TRUE.equals(stepResponse.getStepComplete())
                            || (successfulToolResultSeen && StringUtils.hasText(stepResponse.getResponse()));
                    if (shouldCompleteStep) {
                        stepCompleted = true;
                        log.info("[{}][STEP_MARKED_COMPLETE] sessionId={}, stepId={}",
                                LOG_STAGE,
                                runContext.executionId(),
                                step.getId());
                    }
                }
                case DESIGN_INSTRUCTIONS -> {
                    String instructions = StringUtils.hasText(stepResponse.getResponse())
                            ? stepResponse.getResponse()
                            : "Local skill instructions were unavailable. Please provide the execution instructions directly.";
                    String instructionMessage = planStepFormatter.formatStepEntry(step, "DESIGN_INSTRUCTIONS", instructions);
                    runEntries.add(instructionMessage);
                    executionListener.onStepProgress(
                            runContext.executionId(),
                            cloneStep(step),
                            "DESIGN_INSTRUCTIONS",
                            instructions
                    );
                    log.info("[{}][DESIGN_INSTRUCTIONS] sessionId={}, stepId={}, selectedSkill={}",
                            LOG_STAGE,
                            runContext.executionId(),
                            step.getId(),
                            executionContext.selectedSkillName());
                    return finalizeStepResult(
                            step,
                            LLMPlanResponse.PlanStep.Status.COMPLETED,
                            runEntries,
                            instructions,
                            executionListener,
                            runContext.executionId()
                    );
                }
            }

            if (stepCompleted) {
                break;
            }
        }

        LLMPlanResponse.PlanStep.Status finalStatus = stepCompleted
                ? LLMPlanResponse.PlanStep.Status.COMPLETED
                : LLMPlanResponse.PlanStep.Status.FAILED;
        return finalizeStepResult(step, finalStatus, runEntries, null, executionListener, runContext.executionId());
    }

    private StepExecutionResult finalizeStepResult(LLMPlanResponse.PlanStep step,
                                                   LLMPlanResponse.PlanStep.Status status,
                                                   List<String> runEntries,
                                                   String terminalResponse,
                                                   AgentExecutionListener listener,
                                                   String executionId) {
        List<String> finalEntries = new ArrayList<>(runEntries);
        finalEntries.add(planStepFormatter.formatStepEntry(step, "STEP_STATUS", "Status=" + status));
        listener.onStepCompleted(
                executionId,
                cloneStep(step),
                status.name(),
                terminalResponse,
                List.copyOf(finalEntries)
        );
        log.info("[{}][STEP_FINISH] stepId={}, status={}, terminalResponsePresent={}, runEntryCount={}",
                LOG_STAGE,
                step.getId(),
                status,
                terminalResponse != null,
                finalEntries.size());
        return new StepExecutionResult(step.getId(), status, List.copyOf(finalEntries), terminalResponse);
    }

    private LLMPlanResponse.PlanStep cloneStep(LLMPlanResponse.PlanStep step) {
        LLMPlanResponse.PlanStep copy = new LLMPlanResponse.PlanStep();
        copy.setId(step.getId());
        copy.setContent(step.getContent());
        copy.setToolCategory(step.getToolCategory());
        copy.setDependsOn(step.getDependsOn() == null ? List.of() : List.copyOf(step.getDependsOn()));
        copy.setParallelizable(step.isParallelizable());
        copy.setStatus(step.getStatus());
        return copy;
    }

}
