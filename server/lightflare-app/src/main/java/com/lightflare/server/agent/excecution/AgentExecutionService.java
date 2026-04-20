package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.plan.AgentPlanner;
import com.lightflare.server.agent.skill.SkillContext;
import com.lightflare.server.agent.skill.SkillSelectionService;
import com.lightflare.server.config.AgentProperties;
import com.lightflare.server.execution.ExecutionCheckpoint;
import com.lightflare.server.skill.Skill;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionService {

    private static final String LOG_STAGE = "AGENT_EXEC";
    private static final Pattern JUNK_SCALAR_PATTERN = Pattern.compile(
            "^(?i:(null|none|n/?a))$|^[\\p{Punct}\\s]+$"
    );

    private final AgentPlanner agentPlanner;
    private final SkillSelectionService skillSelectionService;
    private final PlanGraphValidator planGraphValidator;
    private final DagPlanExecutor dagPlanExecutor;
    private final ResponseResolver responseResolver;
    private final AgentProperties agentProperties;
    private final AgentExecutionCheckpointService checkpointService;

    public boolean hasResumableCheckpoint(String executionType, String referenceType, String referenceId) {
        return StringUtils.hasText(executionType)
                && StringUtils.hasText(referenceType)
                && StringUtils.hasText(referenceId)
                && checkpointService.findResumableCheckpoint(executionType, referenceType, referenceId).isPresent();
    }

    public boolean hasWaitingForUserCheckpoint(String executionType, String referenceType, String referenceId) {
        return StringUtils.hasText(executionType)
                && StringUtils.hasText(referenceType)
                && StringUtils.hasText(referenceId)
                && checkpointService.findWaitingForUserCheckpoint(executionType, referenceType, referenceId).isPresent();
    }

    public String resume(ResumeExecutionRequest request) {
        AgentExecutionListener executionListener = request.listener() != null ? request.listener() : AgentExecutionListener.NOOP;
        ExecutionCheckpoint storedCheckpoint = checkpointService.findResumableCheckpoint(
                        request.executionType(),
                        request.referenceType(),
                        request.referenceId()
                )
                .orElseThrow(() -> new IllegalStateException("No resumable execution checkpoint found for referenceId=" + request.referenceId()));
        AgentRunCheckpoint checkpoint = checkpointService.parse(storedCheckpoint);
        PendingUserInputRequest pendingUserInputRequest = checkpoint.getPendingUserInputRequest();
        resetRunningSteps(checkpoint.safeSteps());
        if (pendingUserInputRequest != null) {
            resetWaitingSteps(checkpoint.safeSteps());
            appendUserInput(checkpoint, pendingUserInputRequest, request.userInput());
            checkpoint.setPendingUserInputRequest(null);
        }

        AgentRunContext runContext = new AgentRunContext(
                checkpoint.getExecutionId(),
                checkpoint.getExecutionType(),
                checkpoint.getReferenceType(),
                checkpoint.getReferenceId(),
                checkpoint.getUserId(),
                checkpoint.getTask(),
                request.tools(),
                request.toolExecutionRouter()
        );
        ConversationContext conversationContext = request.conversationContext() != null
                ? request.conversationContext()
                : new ConversationContext(null, checkpoint.safePromptMemories());
        SkillContext skillContext = skillSelectionService.buildSkillContext(null);
        PlanDag planDag = planGraphValidator.buildValidatedDag(checkpoint.safeSteps());
        Skill selectedSkill = skillSelectionService.resolveSelectedSkill(
                checkpoint.getSelectedSkillName(),
                skillContext.availableSkills()
        );
        if (selectedSkill == null && StringUtils.hasText(checkpoint.getSelectedSkillInstructions())) {
            selectedSkill = new Skill();
            selectedSkill.setName(checkpoint.getSelectedSkillName());
            selectedSkill.setContent(checkpoint.getSelectedSkillInstructions());
        }

        log.info("[{}][RESUME] sessionId={}, checkpointId={}, pendingStepCount={}",
                LOG_STAGE,
                request.referenceId(),
                storedCheckpoint.id(),
                planDag.steps().stream()
                        .filter(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)
                        .count());
        executionListener.onPlanCreated(
                runContext.executionId(),
                null,
                checkpoint.getSelectedSkillName(),
                cloneSteps(planDag.steps())
        );

        AgentExecutionState state = new AgentExecutionState();
        state.setCheckpointId(storedCheckpoint.id());
        state.setCheckpoint(checkpoint);
        state.setRunContext(runContext);
        state.setConversationContext(conversationContext);
        state.setSkillContext(skillContext);
        state.setPlanDag(planDag);
        state.setSelectedSkill(selectedSkill);
        state.setExecutionLog(new ArrayList<>(checkpoint.safeExecutionLog()));
        state.setWaveNumber(checkpoint.getWaveNumber());
        state.setReplanCount(checkpoint.getReplanCount());
        state.setListener(executionListener);

        return continueExecution(state);
    }

    public String execute(AgentRunContext runContext,
                          ConversationContext conversationContext,
                          SkillContext skillContext,
                          AgentExecutionListener listener) {
        AgentExecutionListener executionListener = listener != null ? listener : AgentExecutionListener.NOOP;
        log.info("[{}][START] sessionId={}, taskLength={}, toolCount={}, memoryCount={}, skillCount={}",
                LOG_STAGE,
                runContext.executionId(),
                runContext.task() != null ? runContext.task().length() : 0,
                runContext.tools().size(),
                conversationContext.promptMemories().size(),
                skillContext.availableSkills().size());

        // Call LLM to get the plan with steps
        LLMPlanResponse planResponse = agentPlanner.plan(
                runContext.executionId(),
                runContext.executionType(),
                runContext.userId(),
                runContext.task(),
                conversationContext.promptMemories(),
                skillContext.plannerSkills(),
                runContext.tools()
        );
        if (planResponse == null) {
            throw new IllegalStateException("Planner response must not be null");
        }
        normalizePlannerResponse(planResponse);
        executionListener.onPlanCreated(
                runContext.executionId(),
                planResponse.getThoughtProcess(),
                planResponse.getSelectedSkill(),
                cloneSteps(planResponse.getSteps())
        );
        log.info("[{}][PLAN_RECEIVED] sessionId={}, hasResponse={}, stepCount={}, selectedSkill={}",
                LOG_STAGE,
                runContext.executionId(),
                StringUtils.hasText(planResponse.getResponse()),
                CollectionUtils.isEmpty(planResponse.getSteps()) ? 0 : planResponse.getSteps().size(),
                planResponse.getSelectedSkill());

        if (StringUtils.hasText(planResponse.getResponse()) && CollectionUtils.isEmpty(planResponse.getSteps())) {
            log.info("[{}][SHORT_CIRCUIT] sessionId={}, reason=planner-returned-direct-response",
                    LOG_STAGE,
                    runContext.executionId());
            executionListener.onFinalResponse(runContext.executionId(), planResponse.getResponse());
            return planResponse.getResponse();
        }

        if (CollectionUtils.isEmpty(planResponse.getSteps())) {
            log.info("[{}][SHORT_CIRCUIT] sessionId={}, reason=planner-returned-no-steps",
                    LOG_STAGE,
                    runContext.executionId());
            String response = "No execution steps were produced.";
            executionListener.onFinalResponse(runContext.executionId(), response);
            return response;
        }

        initializePlanStatuses(planResponse.getSteps());
        PlanDag planDag = planGraphValidator.buildValidatedDag(planResponse.getSteps());

        Skill selectedSkill = skillSelectionService.resolveSelectedSkill(
                planResponse.getSelectedSkill(),
                skillContext.availableSkills()
        );
        boolean missingSelectedSkill = StringUtils.hasText(planResponse.getSelectedSkill()) && selectedSkill == null;
        if (missingSelectedSkill) {
            log.info("[{}][SKILL_MISSING] sessionId={}, selectedSkill={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    planResponse.getSelectedSkill(), runContext.executionId());
        }
        log.info("[{}][SKILL_RESOLVED] sessionId={}, selectedSkill={}",
                LOG_STAGE,
                runContext.executionId(),
                selectedSkill != null ? selectedSkill.getName() : null, runContext.executionId());

        List<String> executionLog = new ArrayList<>();
        if (missingSelectedSkill) {
            executionLog.add("[system][skill-fallback][INFO] Local skill '" + planResponse.getSelectedSkill()
                    + "' was not found. Continue without local skill instructions and provide the necessary instructions directly.");
        }
        String checkpointId = checkpointService.create(
                runContext,
                conversationContext,
                selectedSkill,
                cloneSteps(planDag.steps()),
                List.copyOf(executionLog)
        );
        AgentRunCheckpoint checkpoint = new AgentRunCheckpoint();
        checkpoint.setTask(runContext.task());
        checkpoint.setExecutionId(runContext.executionId());
        checkpoint.setExecutionType(runContext.executionType());
        checkpoint.setReferenceType(runContext.referenceType());
        checkpoint.setReferenceId(runContext.referenceId());
        checkpoint.setUserId(runContext.userId());
        checkpoint.setPromptMemories(conversationContext.promptMemories());

        AgentExecutionState state = new AgentExecutionState();
        state.setCheckpointId(checkpointId);
        state.setCheckpoint(checkpoint);
        state.setRunContext(runContext);
        state.setConversationContext(conversationContext);
        state.setSkillContext(skillContext);
        state.setPlanDag(planDag);
        state.setSelectedSkill(selectedSkill);
        state.setExecutionLog(executionLog);
        state.setListener(executionListener);

        return continueExecution(state);
    }

    private String continueExecution(AgentExecutionState state) {
        updateCheckpoint(state);
        try {
        while (state.getPlanDag().hasPendingSteps()) {
            state.setWaveNumber(state.getWaveNumber() + 1);
            if (state.getWaveNumber() > Math.max(1, agentProperties.getMaxExecutionWaves())) {
                state.getExecutionLog().add("[system][execution-limit][CANNOT_COMPLETE] Reached maximum execution waves: "
                        + agentProperties.getMaxExecutionWaves());
                log.info("[{}][WAVE_LIMIT_REACHED] sessionId={}, maxExecutionWaves={}",
                        LOG_STAGE,
                        state.getRunContext().executionId(),
                        agentProperties.getMaxExecutionWaves());
                break;
            }

            List<LLMPlanResponse.PlanStep> parallelSteps = dagPlanExecutor.selectNextParallelSteps(state.getRunContext(), state.getPlanDag());
            updateCheckpoint(state);
            for (LLMPlanResponse.PlanStep parallelStep : parallelSteps) {
                state.getListener().onStepStarted(state.getRunContext().executionId(), cloneStep(parallelStep));
            }
            if (parallelSteps.isEmpty()) {
                PlanContinuationDecision blockedDecision = reviewContinuation(state, List.of());
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.REPLAN) {
                    if (state.getReplanCount() < Math.max(0, agentProperties.getMaxReplans())) {
                        state.setReplanCount(state.getReplanCount() + 1);
                        ReplanResult replanResult = replanPendingSteps(state, blockedDecision);
                        if (StringUtils.hasText(replanResult.directResponse())) {
                            state.getListener().onFinalResponse(state.getRunContext().executionId(), replanResult.directResponse());
                            checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), replanResult.directResponse());
                            return replanResult.directResponse();
                        }
                        state.setPlanDag(replanResult.planDag());
                        state.setSelectedSkill(replanResult.selectedSkill() != null ? replanResult.selectedSkill() : state.getSelectedSkill());
                        updateCheckpoint(state);
                        continue;
                    } else {
                        state.getExecutionLog().add("[system][replan-limit][CANNOT_COMPLETE] Reached maximum replans: "
                                + agentProperties.getMaxReplans());
                        log.info("[{}][REPLAN_LIMIT_REACHED] sessionId={}, maxReplans={}",
                                LOG_STAGE,
                                state.getRunContext().executionId(),
                                agentProperties.getMaxReplans());
                        state.setPlanDag(markRemainingPendingAsSkipped(
                                state.getPlanDag(),
                                state.getExecutionLog(),
                                state.getListener(),
                                state.getRunContext().executionId()
                        ));
                        break;
                    }
                }
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.ASK_USER) {
                    if (StringUtils.hasText(blockedDecision.getUserMessage())) {
                        state.getListener().onFinalResponse(state.getRunContext().executionId(), blockedDecision.getUserMessage());
                        checkpointService.saveWaitingForUser(
                                state.getCheckpointId(),
                                state.getCheckpoint(),
                                buildPendingUserInputRequest(null, blockedDecision.getUserMessage())
                        );
                        return blockedDecision.getUserMessage();
                    }
                }
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.CANNOT_COMPLETE
                        && StringUtils.hasText(blockedDecision.getUserMessage())) {
                    state.getListener().onFinalResponse(state.getRunContext().executionId(), blockedDecision.getUserMessage());
                    checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), blockedDecision.getUserMessage());
                    return blockedDecision.getUserMessage();
                }
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.FINAL_RESPONSE) {
                    state.setPlanDag(markRemainingPendingAsSkipped(
                            state.getPlanDag(),
                            state.getExecutionLog(),
                            state.getListener(),
                            state.getRunContext().executionId()
                    ));
                }
                break;
            }

            List<StepExecutionResult> results = dagPlanExecutor.executeParallelSteps(state, parallelSteps);

            AppliedStepResults applicationResult = dagPlanExecutor.applyParallelStepResults(state, parallelSteps, results);
            String userMessage = applicationResult.userMessage();
            updateCheckpoint(state);
            if (applicationResult.pendingUserInputRequest() != null) {
                String waitingResponse = StringUtils.hasText(userMessage)
                        ? userMessage
                        : applicationResult.pendingUserInputRequest().getQuestion();
                state.getListener().onFinalResponse(state.getRunContext().executionId(), waitingResponse);
                checkpointService.saveWaitingForUser(state.getCheckpointId(), state.getCheckpoint(), applicationResult.pendingUserInputRequest());
                return waitingResponse;
            }
            if (StringUtils.hasText(userMessage)) {
                log.info("[{}][EARLY_RETURN] sessionId={}, responseLength={}",
                        LOG_STAGE,
                        state.getRunContext().executionId(), userMessage.length());
                state.getListener().onFinalResponse(state.getRunContext().executionId(), userMessage);
                checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), userMessage);
                return userMessage;
            }

            List<String> lastWaveExecutionLog = results.stream()
                    .flatMap(result -> result.executionLogEntries().stream())
                    .toList();
            PlanContinuationDecision decision = reviewContinuation(state, lastWaveExecutionLog);
            log.info("[{}][WAVE_REVIEW] sessionId={}, waveNumber={}, outcome={}, rationale={}",
                    LOG_STAGE,
                    state.getRunContext().executionId(),
                    state.getWaveNumber(),
                    decision.getOutcome(),
                    decision.getRationale());
            switch (decision.getOutcome()) {
                case CONTINUE -> {
                }
                case REPLAN -> {
                    if (state.getReplanCount() >= Math.max(0, agentProperties.getMaxReplans())) {
                        state.getExecutionLog().add("[system][replan-limit][CANNOT_COMPLETE] Reached maximum replans: "
                                + agentProperties.getMaxReplans());
                        log.info("[{}][REPLAN_LIMIT_REACHED] sessionId={}, maxReplans={}",
                                LOG_STAGE,
                                state.getRunContext().executionId(),
                                agentProperties.getMaxReplans());
                        state.setPlanDag(markRemainingPendingAsSkipped(
                                state.getPlanDag(),
                                state.getExecutionLog(),
                                state.getListener(),
                                state.getRunContext().executionId()
                        ));
                        break;
                    }
                    state.setReplanCount(state.getReplanCount() + 1);
                    ReplanResult replanResult = replanPendingSteps(state, decision);
                    if (StringUtils.hasText(replanResult.directResponse())) {
                        state.getListener().onFinalResponse(state.getRunContext().executionId(), replanResult.directResponse());
                        checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), replanResult.directResponse());
                        return replanResult.directResponse();
                    }
                    state.setPlanDag(replanResult.planDag());
                    state.setSelectedSkill(replanResult.selectedSkill() != null ? replanResult.selectedSkill() : state.getSelectedSkill());
                    updateCheckpoint(state);
                }
                case FINAL_RESPONSE -> {
                    state.setPlanDag(markRemainingPendingAsSkipped(
                            state.getPlanDag(),
                            state.getExecutionLog(),
                            state.getListener(),
                            state.getRunContext().executionId()
                    ));
                }
                case ASK_USER -> {
                    if (StringUtils.hasText(decision.getUserMessage())) {
                        state.getListener().onFinalResponse(state.getRunContext().executionId(), decision.getUserMessage());
                        checkpointService.saveWaitingForUser(
                                state.getCheckpointId(),
                                state.getCheckpoint(),
                                buildPendingUserInputRequest(null, decision.getUserMessage())
                        );
                        return decision.getUserMessage();
                    }
                    state.setPlanDag(markRemainingPendingAsSkipped(
                            state.getPlanDag(),
                            state.getExecutionLog(),
                            state.getListener(),
                            state.getRunContext().executionId()
                    ));
                }
                case CANNOT_COMPLETE -> {
                    if (StringUtils.hasText(decision.getUserMessage())) {
                        state.getListener().onFinalResponse(state.getRunContext().executionId(), decision.getUserMessage());
                        checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), decision.getUserMessage());
                        return decision.getUserMessage();
                    }
                    state.setPlanDag(markRemainingPendingAsSkipped(
                            state.getPlanDag(),
                            state.getExecutionLog(),
                            state.getListener(),
                            state.getRunContext().executionId()
                    ));
                }
            }
        }

        dagPlanExecutor.markBlockedStepsAsFailed(state.getPlanDag(), state.getExecutionLog(), state.getListener(), state.getRunContext().executionId());
        updateCheckpoint(state);

        ResponseResolutionResult resolvedResponse = responseResolver.resolveWithMetadata(state.getRunContext(), state.getPlanDag().steps(), state.getExecutionLog());
        String finalResponse = resolvedResponse.response();
        state.getListener().onFinalResponse(state.getRunContext().executionId(), finalResponse);
        if (resolvedResponse.waitingForUser()) {
            checkpointService.saveWaitingForUser(
                    state.getCheckpointId(),
                    state.getCheckpoint(),
                    buildPendingUserInputRequest(null, finalResponse)
            );
            return finalResponse;
        }
        checkpointService.saveCompleted(state.getCheckpointId(), state.getCheckpoint(), finalResponse);
        log.info("[{}][COMPLETE] sessionId={}, finalResponseLength={}",
                LOG_STAGE,
                state.getRunContext().executionId(), finalResponse.length());
        return finalResponse;
        } catch (RuntimeException exception) {
            checkpointService.saveFailed(state.getCheckpointId(), state.getCheckpoint(), exception.getMessage());
            throw exception;
        }
    }

    private void updateCheckpoint(AgentExecutionState state) {
        AgentRunCheckpoint checkpoint = state.getCheckpoint();
        AgentRunContext runContext = state.getRunContext();
        Skill selectedSkill = state.getSelectedSkill();
        checkpoint.setTask(runContext.task());
        checkpoint.setExecutionId(runContext.executionId());
        checkpoint.setExecutionType(runContext.executionType());
        checkpoint.setReferenceType(runContext.referenceType());
        checkpoint.setReferenceId(runContext.referenceId());
        checkpoint.setUserId(runContext.userId());
        checkpoint.setPromptMemories(state.getConversationContext().promptMemories());
        checkpoint.setSelectedSkillName(selectedSkill != null ? selectedSkill.getName() : checkpoint.getSelectedSkillName());
        checkpoint.setSelectedSkillInstructions(selectedSkill != null ? selectedSkill.getContent() : checkpoint.getSelectedSkillInstructions());
        checkpoint.setSteps(cloneSteps(state.getPlanDag().steps()));
        checkpoint.setExecutionLog(List.copyOf(state.getExecutionLog()));
        checkpoint.setWaveNumber(state.getWaveNumber());
        checkpoint.setReplanCount(state.getReplanCount());
        checkpointService.saveRunning(state.getCheckpointId(), checkpoint);
    }

    private void resetRunningSteps(List<LLMPlanResponse.PlanStep> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return;
        }
        for (LLMPlanResponse.PlanStep step : steps) {
            if (step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.RUNNING) {
                step.setStatus(LLMPlanResponse.PlanStep.Status.PENDING);
            }
        }
    }

    private void resetWaitingSteps(List<LLMPlanResponse.PlanStep> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return;
        }
        for (LLMPlanResponse.PlanStep step : steps) {
            if (step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.WAITING_FOR_USER) {
                step.setStatus(LLMPlanResponse.PlanStep.Status.PENDING);
            }
        }
    }

    private void appendUserInput(AgentRunCheckpoint checkpoint,
                                 PendingUserInputRequest pendingUserInputRequest,
                                 String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return;
        }
        List<String> executionLog = new ArrayList<>(checkpoint.safeExecutionLog());
        String stepPrefix = StringUtils.hasText(pendingUserInputRequest.getStepId())
                ? "[" + pendingUserInputRequest.getStepId() + "]"
                : "[system]";
        String question = StringUtils.hasText(pendingUserInputRequest.getQuestion())
                ? " question=\"" + pendingUserInputRequest.getQuestion().replace("\"", "'") + "\""
                : "";
        executionLog.add(stepPrefix + "[USER_INPUT_RECEIVED][INFO]" + question + " answer=\"" + userInput.replace("\"", "'") + "\"");
        checkpoint.setExecutionLog(executionLog);
    }

    private PendingUserInputRequest buildPendingUserInputRequest(String stepId, String question) {
        PendingUserInputRequest pendingUserInputRequest = new PendingUserInputRequest();
        pendingUserInputRequest.setStepId(stepId);
        pendingUserInputRequest.setQuestion(question);
        return pendingUserInputRequest;
    }

    private PlanContinuationDecision reviewContinuation(AgentExecutionState state,
                                                        List<String> lastWaveExecutionLog) {
        PlanContinuationDecision decision = agentPlanner.reviewPlanContinuation(
                state.getRunContext().executionId(),
                state.getRunContext().executionType(),
                state.getRunContext().userId(),
                state.getRunContext().task(),
                state.getPlanDag().steps(),
                state.getExecutionLog(),
                lastWaveExecutionLog,
                state.getPlanDag().hasRunnablePendingSteps(),
                state.getPlanDag().hasPendingSteps()
        );
        if (decision == null || decision.getOutcome() == null) {
            decision = new PlanContinuationDecision();
            if (!state.getPlanDag().hasPendingSteps()) {
                decision.setOutcome(PlanContinuationDecision.Outcome.FINAL_RESPONSE);
                decision.setRationale("No pending steps remain.");
            } else if (!state.getPlanDag().hasRunnablePendingSteps()) {
                decision.setOutcome(PlanContinuationDecision.Outcome.REPLAN);
                decision.setRationale("Pending steps are blocked.");
            } else {
                decision.setOutcome(PlanContinuationDecision.Outcome.CONTINUE);
                decision.setRationale("Runnable pending steps remain.");
            }
        }
        return decision;
    }

    private ReplanResult replanPendingSteps(AgentExecutionState state,
                                            PlanContinuationDecision decision) {
        List<String> immutableStepIds = state.getPlanDag().steps().stream()
                .filter(step -> step != null && step.getStatus() != LLMPlanResponse.PlanStep.Status.PENDING)
                .filter(step -> step.getStatus() != LLMPlanResponse.PlanStep.Status.RUNNING)
                .map(LLMPlanResponse.PlanStep::getId)
                .toList();
        LLMPlanResponse replanResponse = agentPlanner.replan(
                state.getRunContext().executionId(),
                state.getRunContext().executionType(),
                state.getRunContext().userId(),
                state.getRunContext().task(),
                state.getConversationContext().promptMemories(),
                state.getSkillContext().plannerSkills(),
                state.getRunContext().tools(),
                cloneSteps(state.getPlanDag().steps()),
                state.getExecutionLog(),
                immutableStepIds,
                decision.getRationale()
        );
        if (replanResponse == null) {
            state.getExecutionLog().add("[system][replan][CANNOT_COMPLETE] Replanner returned no response.");
            return new ReplanResult(markRemainingPendingAsSkipped(
                    state.getPlanDag(),
                    state.getExecutionLog(),
                    state.getListener(),
                    state.getRunContext().executionId()
            ), null, null);
        }
        normalizePlannerResponse(replanResponse);
        if (StringUtils.hasText(replanResponse.getResponse()) && CollectionUtils.isEmpty(replanResponse.getSteps())) {
            log.info("[{}][REPLAN_DIRECT_RESPONSE] sessionId={}, responseLength={}",
                    LOG_STAGE,
                    state.getRunContext().executionId(),
                    replanResponse.getResponse().length());
            return new ReplanResult(state.getPlanDag(), null, replanResponse.getResponse());
        }

        List<LLMPlanResponse.PlanStep> mergedSteps = mergeImmutableAndReplannedSteps(
                state.getPlanDag().steps(),
                replanResponse.getSteps(),
                state.getReplanCount(),
                state.getExecutionLog()
        );
        if (mergedSteps.stream().noneMatch(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)) {
            log.info("[{}][REPLAN_EMPTY] sessionId={}, replanCount={}",
                    LOG_STAGE,
                    state.getRunContext().executionId(),
                    state.getReplanCount());
            return new ReplanResult(planGraphValidator.buildValidatedDag(mergedSteps), null, null);
        }

        PlanDag replannedDag = planGraphValidator.buildValidatedDag(mergedSteps);
        state.getListener().onPlanCreated(
                state.getRunContext().executionId(),
                replanResponse.getThoughtProcess(),
                replanResponse.getSelectedSkill(),
                cloneSteps(replannedDag.steps())
        );
        Skill selectedSkill = skillSelectionService.resolveSelectedSkill(
                replanResponse.getSelectedSkill(),
                state.getSkillContext().availableSkills()
        );
        log.info("[{}][REPLAN_APPLIED] sessionId={}, replanCount={}, stepCount={}, pendingStepCount={}",
                LOG_STAGE,
                state.getRunContext().executionId(),
                state.getReplanCount(),
                replannedDag.steps().size(),
                replannedDag.steps().stream()
                        .filter(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)
                        .count());
        return new ReplanResult(replannedDag, selectedSkill, null);
    }

    private List<LLMPlanResponse.PlanStep> mergeImmutableAndReplannedSteps(List<LLMPlanResponse.PlanStep> currentSteps,
                                                                           List<LLMPlanResponse.PlanStep> replannedSteps,
                                                                           int replanCount,
                                                                           List<String> executionLog) {
        List<LLMPlanResponse.PlanStep> mergedSteps = new ArrayList<>();
        Set<String> completedImmutableIds = new HashSet<>();
        Set<String> immutableIds = new LinkedHashSet<>();
        for (LLMPlanResponse.PlanStep currentStep : currentSteps) {
            if (currentStep == null
                    || currentStep.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING
                    || currentStep.getStatus() == LLMPlanResponse.PlanStep.Status.RUNNING) {
                continue;
            }
            LLMPlanResponse.PlanStep immutableCopy = cloneStep(currentStep);
            mergedSteps.add(immutableCopy);
            immutableIds.add(immutableCopy.getId());
            if (immutableCopy.getStatus() == LLMPlanResponse.PlanStep.Status.COMPLETED) {
                completedImmutableIds.add(immutableCopy.getId());
            }
        }

        if (CollectionUtils.isEmpty(replannedSteps)) {
            return mergedSteps;
        }

        Set<String> usedIds = new LinkedHashSet<>(immutableIds);
        List<String> originalIds = new ArrayList<>();
        List<String> normalizedIds = new ArrayList<>();
        for (int i = 0; i < replannedSteps.size(); i++) {
            LLMPlanResponse.PlanStep step = replannedSteps.get(i);
            String originalId = step != null ? sanitizePlannerScalar(step.getId()) : null;
            String normalizedId = originalId;
            if (!StringUtils.hasText(normalizedId) || usedIds.contains(normalizedId)) {
                normalizedId = "replan-" + replanCount + "-step-" + (i + 1);
            }
            while (usedIds.contains(normalizedId)) {
                normalizedId = normalizedId + "-next";
            }
            usedIds.add(normalizedId);
            originalIds.add(originalId);
            normalizedIds.add(normalizedId);
        }

        for (int i = 0; i < replannedSteps.size(); i++) {
            LLMPlanResponse.PlanStep replannedStep = replannedSteps.get(i);
            if (replannedStep == null) {
                continue;
            }
            LLMPlanResponse.PlanStep copy = cloneStep(replannedStep);
            copy.setId(normalizedIds.get(i));
            copy.setStatus(LLMPlanResponse.PlanStep.Status.PENDING);
            copy.setDependsOn(normalizeReplannedDependencies(
                    copy,
                    replannedStep.getDependsOn(),
                    originalIds,
                    normalizedIds,
                    i,
                    completedImmutableIds,
                    executionLog
            ));
            mergedSteps.add(copy);
        }
        return mergedSteps;
    }

    private List<String> normalizeReplannedDependencies(LLMPlanResponse.PlanStep step,
                                                        List<String> dependencies,
                                                        List<String> originalIds,
                                                        List<String> normalizedIds,
                                                        int currentStepIndex,
                                                        Set<String> completedImmutableIds,
                                                        List<String> executionLog) {
        if (CollectionUtils.isEmpty(dependencies)) {
            return List.of();
        }
        List<String> normalizedDependencies = new ArrayList<>();
        for (String dependencyId : dependencies) {
            String sanitizedDependency = sanitizePlannerScalar(dependencyId);
            if (!StringUtils.hasText(sanitizedDependency)) {
                continue;
            }
            int newStepIndex = originalIds.indexOf(sanitizedDependency);
            if (newStepIndex >= 0 && newStepIndex < currentStepIndex) {
                normalizedDependencies.add(normalizedIds.get(newStepIndex));
            } else if (completedImmutableIds.contains(sanitizedDependency)) {
                normalizedDependencies.add(sanitizedDependency);
            } else {
                executionLog.add("[system][replan][DEPENDENCY_DROPPED] stepId=" + step.getId()
                        + " dependencyId=" + sanitizedDependency);
            }
        }
        return normalizedDependencies.stream().distinct().toList();
    }

    private PlanDag markRemainingPendingAsSkipped(PlanDag planDag,
                                                  List<String> executionLog,
                                                  AgentExecutionListener listener,
                                                  String executionId) {
        for (LLMPlanResponse.PlanStep step : planDag.steps()) {
            if (step == null || step.getStatus() != LLMPlanResponse.PlanStep.Status.PENDING) {
                continue;
            }
            planDag.updateStepStatus(step.getId(), LLMPlanResponse.PlanStep.Status.FAILED);
            String entry = "[system][step-skipped][STEP_STATUS] stepId=" + step.getId()
                    + " Status=FAILED reason=continuation-review-stopped-plan";
            executionLog.add(entry);
            listener.onStepCompleted(
                    executionId,
                    cloneStep(step),
                    LLMPlanResponse.PlanStep.Status.FAILED.name(),
                    null,
                    List.of(entry)
            );
        }
        return planDag;
    }

    private void initializePlanStatuses(List<LLMPlanResponse.PlanStep> steps) {
        log.info("[{}][PLAN_INIT] resetting step statuses, stepCount={}", LOG_STAGE, steps.size());
        for (LLMPlanResponse.PlanStep step : steps) {
            if (step.getStatus() == null || step.getStatus() == LLMPlanResponse.PlanStep.Status.RUNNING) {
                step.setStatus(LLMPlanResponse.PlanStep.Status.PENDING);
                log.info("[{}][PLAN_INIT_STEP] stepId={}, status={}, title={}",
                        LOG_STAGE, step.getId(), step.getStatus(), step.getContent());
            }
        }
    }

    private void normalizePlannerResponse(LLMPlanResponse planResponse) {
        if (!CollectionUtils.isEmpty(planResponse.getSteps())) {
            planResponse.setResponse(null);
        } else if (!StringUtils.hasText(sanitizePlannerScalar(planResponse.getResponse()))) {
            planResponse.setResponse(null);
        } else {
            planResponse.setResponse(sanitizePlannerScalar(planResponse.getResponse()));
        }

        String sanitizedSelectedSkill = sanitizePlannerScalar(planResponse.getSelectedSkill());
        planResponse.setSelectedSkill(StringUtils.hasText(sanitizedSelectedSkill) ? sanitizedSelectedSkill : null);
    }

    private String sanitizePlannerScalar(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        return JUNK_SCALAR_PATTERN.matcher(trimmed).matches() ? null : trimmed;
    }

    private List<LLMPlanResponse.PlanStep> cloneSteps(List<LLMPlanResponse.PlanStep> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return List.of();
        }
        return steps.stream().map(this::cloneStep).toList();
    }

    private LLMPlanResponse.PlanStep cloneStep(LLMPlanResponse.PlanStep step) {
        if (step == null) {
            return null;
        }

        LLMPlanResponse.PlanStep copy = new LLMPlanResponse.PlanStep();
        copy.setId(step.getId());
        copy.setContent(step.getContent());
        copy.setToolCategory(step.getToolCategory());
        copy.setDependsOn(CollectionUtils.isEmpty(step.getDependsOn()) ? List.of() : List.copyOf(step.getDependsOn()));
        copy.setParallelizable(step.isParallelizable());
        copy.setStatus(step.getStatus());
        return copy;
    }

    private record ReplanResult(
            PlanDag planDag,
            Skill selectedSkill,
            String directResponse
    ) {
    }

}
