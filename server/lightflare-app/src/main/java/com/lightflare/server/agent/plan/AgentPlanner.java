package com.lightflare.server.agent.plan;

import com.lightflare.server.agent.excecution.StepExecutionContext;
import com.lightflare.server.agent.excecution.PlanContinuationDecision;
import com.lightflare.server.agent.excecution.ResponseResolution;
import com.lightflare.server.agent.memory.ChatSessionUsageService;
import com.lightflare.server.agent.prompts.*;
import com.lightflare.server.llmproviders.core.LLMFinalResponse;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.llmproviders.core.LLMResponse;
import com.lightflare.server.llmproviders.core.LLMStepResponse;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.utils.FileUtils;
import com.lightflare.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private static final String LOG_STAGE = "LLM_PLAN";

    private final LLMProvider llmProvider;
    private final ChatSessionUsageService chatSessionUsageService;

    public LLMPlanResponse plan(String sessionId,
                                String userId,
                                String task,
                                List<MemoryPromptItem> memoryList,
                                List<SkillPromptItem> skills,
                                List<ToolDefinition> tools) {
        log.info("[{}][PLAN_REQUEST] sessionId={}, memoryCount={}, skillCount={}, toolCount={}",
                LOG_STAGE,
                sessionId,
                memoryList != null ? memoryList.size() : 0,
                skills != null ? skills.size() : 0,
                tools != null ? tools.size() : 0);
        PlanTaskPromptRequest promptRequest = new PlanTaskPromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("plan-task.md"));
        promptRequest.setTask(task);
        promptRequest.setMemoryList(memoryList);
        promptRequest.setSkills(skills);
        // Deferred loading: the planner sees only lightweight tool metadata here.
        // Full tool schemas stay on the server and are loaded later for step execution.
        promptRequest.setTools(toPlanToolPromptItems(tools));

        String ask = JsonUtils.toJson(promptRequest);
        LLMResponse<LLMPlanResponse> llmResponse = llmProvider.getStructuredResponse(ask, LLMPlanResponse.class);
        chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
        log.info("[{}][PLAN_RESPONSE] sessionId={}, stepCount={}, hasResponse={}",
                LOG_STAGE,
                sessionId,
                llmResponse.getOutputData() != null && llmResponse.getOutputData().getSteps() != null
                        ? llmResponse.getOutputData().getSteps().size() : 0,
                llmResponse.getOutputData() != null && llmResponse.getOutputData().getResponse() != null);
        return llmResponse.getOutputData();
    }

    public LLMStepResponse executeStep(String sessionId,
                                       String userId,
                                       StepExecutionContext executionContext,
                                       LLMPlanResponse.PlanStep currentStep,
                                       StepExecutionStatePrompt stepState,
                                       List<String> dependencyContext,
                                       List<String> stepExecutionLog) {
        log.info("[{}][STEP_REQUEST] sessionId={}, stepId={}, selectedSkill={}, dependencyContextSize={}, stepExecutionLogSize={}",
                LOG_STAGE,
                sessionId,
                currentStep != null ? currentStep.getId() : null,
                executionContext != null ? executionContext.selectedSkillName() : null,
                dependencyContext != null ? dependencyContext.size() : 0,
                stepExecutionLog != null ? stepExecutionLog.size() : 0);
        ExecuteStepPromptRequest promptRequest = new ExecuteStepPromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("execute-step.md"));
        promptRequest.setTask(executionContext != null ? executionContext.task() : null);
        promptRequest.setSelectedSkillInstructions(executionContext != null ? executionContext.selectedSkillInstructions() : null);
        promptRequest.setMemoryList(executionContext != null ? executionContext.promptMemories() : List.of());
        promptRequest.setTools(executionContext != null ? executionContext.toolsFor(currentStep) : List.of());
        promptRequest.setCurrentStep(currentStep);
        promptRequest.setStepState(stepState);
        promptRequest.setDependencyContext(dependencyContext);
        promptRequest.setStepExecutionLog(stepExecutionLog);

        String ask = JsonUtils.toJson(promptRequest);
        LLMResponse<LLMStepResponse> llmResponse = llmProvider.getStructuredResponse(ask, LLMStepResponse.class);
        chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
        log.info("[{}][STEP_RESPONSE] sessionId={}, stepId={}, action={}",
                LOG_STAGE,
                sessionId,
                currentStep != null ? currentStep.getId() : null,
                llmResponse.getOutputData() != null ? llmResponse.getOutputData().getAction() : null);
        return llmResponse.getOutputData();
    }

    public PlanContinuationDecision reviewPlanContinuation(String sessionId,
                                                           String userId,
                                                           String task,
                                                           List<LLMPlanResponse.PlanStep> plan,
                                                           List<String> executionLog,
                                                           List<String> lastWaveExecutionLog,
                                                           boolean hasRunnablePendingSteps,
                                                           boolean hasPendingSteps) {
        log.info("[{}][CONTINUATION_REVIEW_REQUEST] sessionId={}, stepCount={}, executionLogSize={}, lastWaveLogSize={}, hasRunnablePendingSteps={}, hasPendingSteps={}",
                LOG_STAGE,
                sessionId,
                plan != null ? plan.size() : 0,
                executionLog != null ? executionLog.size() : 0,
                lastWaveExecutionLog != null ? lastWaveExecutionLog.size() : 0,
                hasRunnablePendingSteps,
                hasPendingSteps);
        ReviewPlanContinuationPromptRequest promptRequest = new ReviewPlanContinuationPromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("review-plan-continuation.md"));
        promptRequest.setTask(task);
        promptRequest.setPlan(plan);
        promptRequest.setExecutionLog(executionLog);
        promptRequest.setLastWaveExecutionLog(lastWaveExecutionLog);
        promptRequest.setHasRunnablePendingSteps(hasRunnablePendingSteps);
        promptRequest.setHasPendingSteps(hasPendingSteps);

        try {
            String ask = JsonUtils.toJson(promptRequest);
            LLMResponse<PlanContinuationDecision> llmResponse =
                    llmProvider.getStructuredResponse(ask, PlanContinuationDecision.class);
            chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
            PlanContinuationDecision decision = llmResponse.getOutputData();
            log.info("[{}][CONTINUATION_REVIEW_RESULT] sessionId={}, outcome={}",
                    LOG_STAGE,
                    sessionId,
                    decision != null ? decision.getOutcome() : null);
            return decision;
        } catch (RuntimeException e) {
            log.warn("[{}][CONTINUATION_REVIEW_FAIL] sessionId={}", LOG_STAGE, sessionId, e);
            return null;
        }
    }

    public LLMPlanResponse replan(String sessionId,
                                  String userId,
                                  String task,
                                  List<MemoryPromptItem> memoryList,
                                  List<SkillPromptItem> skills,
                                  List<ToolDefinition> tools,
                                  List<LLMPlanResponse.PlanStep> currentPlan,
                                  List<String> executionLog,
                                  List<String> immutableStepIds,
                                  String replanReason) {
        log.info("[{}][REPLAN_REQUEST] sessionId={}, currentStepCount={}, executionLogSize={}, immutableStepCount={}",
                LOG_STAGE,
                sessionId,
                currentPlan != null ? currentPlan.size() : 0,
                executionLog != null ? executionLog.size() : 0,
                immutableStepIds != null ? immutableStepIds.size() : 0);
        ReplanTaskPromptRequest promptRequest = new ReplanTaskPromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("replan-task.md"));
        promptRequest.setTask(task);
        promptRequest.setMemoryList(memoryList);
        promptRequest.setSkills(skills);
        promptRequest.setTools(toPlanToolPromptItems(tools));
        promptRequest.setCurrentPlan(currentPlan);
        promptRequest.setExecutionLog(executionLog);
        promptRequest.setImmutableStepIds(immutableStepIds);
        promptRequest.setReplanReason(replanReason);

        String ask = JsonUtils.toJson(promptRequest);
        LLMResponse<LLMPlanResponse> llmResponse = llmProvider.getStructuredResponse(ask, LLMPlanResponse.class);
        chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
        LLMPlanResponse replanResponse = llmResponse.getOutputData();
        log.info("[{}][REPLAN_RESPONSE] sessionId={}, stepCount={}, hasResponse={}",
                LOG_STAGE,
                sessionId,
                replanResponse != null && replanResponse.getSteps() != null ? replanResponse.getSteps().size() : 0,
                replanResponse != null && replanResponse.getResponse() != null);
        return replanResponse;
    }

    public String composeResponse(String sessionId,
                                  String userId,
                                  String task,
                                  List<LLMPlanResponse.PlanStep> steps,
                                  List<String> executionLog,
                                  String candidateResponse,
                                  String evaluationFeedback) {
        log.info("[{}][RESPONSE_REQUEST] sessionId={}, stepCount={}, executionLogSize={}",
                LOG_STAGE,
                sessionId,
                steps != null ? steps.size() : 0,
                executionLog != null ? executionLog.size() : 0);
        ResolveResponsePromptRequest promptRequest = new ResolveResponsePromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("resolve-response.md"));
        promptRequest.setTask(task);
        promptRequest.setPlan(steps);
        promptRequest.setExecutionLog(executionLog);
        promptRequest.setCandidateResponse(candidateResponse);
        promptRequest.setEvaluationFeedback(evaluationFeedback);

        try {
            String ask = JsonUtils.toJson(promptRequest);
            LLMResponse<LLMFinalResponse> llmResponse = llmProvider.getStructuredResponse(ask, LLMFinalResponse.class);
            chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
            LLMFinalResponse finalResponse = llmResponse.getOutputData();
            log.info("[{}][RESPONSE_RESULT] sessionId={}, hasResponse={}",
                    LOG_STAGE,
                    sessionId,
                    finalResponse != null && finalResponse.getResponse() != null);
            return finalResponse != null ? finalResponse.getResponse() : null;
        } catch (RuntimeException e) {
            log.warn("[{}][RESPONSE_FAIL] sessionId={}", LOG_STAGE, sessionId, e);
            return null;
        }
    }

    public ResponseResolution reviewResponse(String sessionId,
                                             String userId,
                                             String task,
                                             List<LLMPlanResponse.PlanStep> steps,
                                             List<String> executionLog,
                                             String candidateResponse) {
        log.info("[{}][RESPONSE_REVIEW_REQUEST] sessionId={}, stepCount={}, executionLogSize={}, candidateLength={}",
                LOG_STAGE,
                sessionId,
                steps != null ? steps.size() : 0,
                executionLog != null ? executionLog.size() : 0,
                candidateResponse != null ? candidateResponse.length() : 0);
        ReviewResponsePromptRequest promptRequest = new ReviewResponsePromptRequest();
        promptRequest.setPromptDescription(FileUtils.loadPromptTemplate("review-response.md"));
        promptRequest.setTask(task);
        promptRequest.setPlan(steps);
        promptRequest.setExecutionLog(executionLog);
        promptRequest.setCandidateResponse(candidateResponse);

        try {
            String ask = JsonUtils.toJson(promptRequest);
            LLMResponse<ResponseResolution> llmResponse =
                    llmProvider.getStructuredResponse(ask, ResponseResolution.class);
            chatSessionUsageService.recordLlmUsage(sessionId, userId, llmResponse);
            ResponseResolution resolution = llmResponse.getOutputData();
            log.info("[{}][RESPONSE_REVIEW_RESULT] sessionId={}, outcome={}",
                    LOG_STAGE,
                    sessionId,
                    resolution != null ? resolution.getOutcome() : null);
            return resolution;
        } catch (RuntimeException e) {
            log.warn("[{}][RESPONSE_REVIEW_FAIL] sessionId={}", LOG_STAGE, sessionId, e);
            return null;
        }
    }

    private List<PlanToolPromptItem> toPlanToolPromptItems(List<ToolDefinition> tools) {
        if (tools == null) {
            return List.of();
        }

        return tools.stream()
                .map(tool -> PlanToolPromptItem.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .category(tool.getCategory())
                        .build())
                .toList();
    }
}
