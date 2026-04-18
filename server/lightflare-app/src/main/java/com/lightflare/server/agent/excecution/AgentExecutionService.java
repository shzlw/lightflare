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
import com.lightflare.server.tools.core.ToolDefinition;
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

    public String resume(String executionType,
                         String referenceType,
                         String referenceId,
                         List<ToolDefinition> tools,
                         AgentExecutionListener listener) {
        AgentExecutionListener executionListener = listener != null ? listener : AgentExecutionListener.NOOP;
        ExecutionCheckpoint storedCheckpoint = checkpointService.findResumableCheckpoint(executionType, referenceType, referenceId)
                .orElseThrow(() -> new IllegalStateException("No resumable execution checkpoint found for referenceId=" + referenceId));
        AgentRunCheckpoint checkpoint = checkpointService.parse(storedCheckpoint);
        resetRunningSteps(checkpoint.safeSteps());

        AgentRunContext runContext = new AgentRunContext(
                checkpoint.getExecutionId(),
                checkpoint.getExecutionType() != null ? checkpoint.getExecutionType() : executionType,
                checkpoint.getReferenceType() != null ? checkpoint.getReferenceType() : referenceType,
                checkpoint.getReferenceId(),
                checkpoint.getUserId(),
                checkpoint.getTask(),
                tools
        );
        ConversationContext conversationContext = new ConversationContext(null, checkpoint.safePromptMemories());
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
                referenceId,
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

        return continueExecution(
                storedCheckpoint.id(),
                checkpoint,
                runContext,
                conversationContext,
                skillContext,
                planDag,
                selectedSkill,
                new ArrayList<>(checkpoint.safeExecutionLog()),
                checkpoint.getWaveNumber(),
                checkpoint.getReplanCount(),
                executionListener
        );
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

        return continueExecution(
                checkpointId,
                checkpoint,
                runContext,
                conversationContext,
                skillContext,
                planDag,
                selectedSkill,
                executionLog,
                0,
                0,
                executionListener
        );
    }

    private String continueExecution(String checkpointId,
                                     AgentRunCheckpoint checkpoint,
                                     AgentRunContext runContext,
                                     ConversationContext conversationContext,
                                     SkillContext skillContext,
                                     PlanDag planDag,
                                     Skill selectedSkill,
                                     List<String> executionLog,
                                     int waveNumber,
                                     int replanCount,
                                     AgentExecutionListener executionListener) {
        updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);
        try {
        while (planDag.hasPendingSteps()) {
            waveNumber++;
            if (waveNumber > Math.max(1, agentProperties.getMaxExecutionWaves())) {
                executionLog.add("[system][execution-limit][CANNOT_COMPLETE] Reached maximum execution waves: "
                        + agentProperties.getMaxExecutionWaves());
                log.info("[{}][WAVE_LIMIT_REACHED] sessionId={}, maxExecutionWaves={}",
                        LOG_STAGE,
                        runContext.executionId(),
                        agentProperties.getMaxExecutionWaves());
                break;
            }

            List<LLMPlanResponse.PlanStep> parallelSteps = dagPlanExecutor.selectNextParallelSteps(runContext, planDag);
            updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);
            for (LLMPlanResponse.PlanStep parallelStep : parallelSteps) {
                executionListener.onStepStarted(runContext.executionId(), cloneStep(parallelStep));
            }
            if (parallelSteps.isEmpty()) {
                PlanContinuationDecision blockedDecision = reviewContinuation(
                        runContext,
                        planDag,
                        executionLog,
                        List.of()
                );
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.REPLAN) {
                    if (replanCount < Math.max(0, agentProperties.getMaxReplans())) {
                        ReplanResult replanResult = replanPendingSteps(
                                runContext,
                                conversationContext,
                                skillContext,
                                planDag,
                                executionLog,
                                executionListener,
                                blockedDecision,
                                ++replanCount
                        );
                        if (StringUtils.hasText(replanResult.directResponse())) {
                            executionListener.onFinalResponse(runContext.executionId(), replanResult.directResponse());
                            checkpointService.saveCompleted(checkpointId, checkpoint, replanResult.directResponse());
                            return replanResult.directResponse();
                        }
                        planDag = replanResult.planDag();
                        selectedSkill = replanResult.selectedSkill() != null ? replanResult.selectedSkill() : selectedSkill;
                        updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);
                        continue;
                    } else {
                        executionLog.add("[system][replan-limit][CANNOT_COMPLETE] Reached maximum replans: "
                                + agentProperties.getMaxReplans());
                        log.info("[{}][REPLAN_LIMIT_REACHED] sessionId={}, maxReplans={}",
                                LOG_STAGE,
                                runContext.executionId(),
                                agentProperties.getMaxReplans());
                        planDag = markRemainingPendingAsSkipped(
                                planDag,
                                executionLog,
                                executionListener,
                                runContext.executionId()
                        );
                        break;
                    }
                }
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.ASK_USER
                        || blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.CANNOT_COMPLETE) {
                    if (StringUtils.hasText(blockedDecision.getUserMessage())) {
                        executionListener.onFinalResponse(runContext.executionId(), blockedDecision.getUserMessage());
                        checkpointService.saveCompleted(checkpointId, checkpoint, blockedDecision.getUserMessage());
                        return blockedDecision.getUserMessage();
                    }
                }
                if (blockedDecision.getOutcome() == PlanContinuationDecision.Outcome.FINAL_RESPONSE) {
                    planDag = markRemainingPendingAsSkipped(
                            planDag,
                            executionLog,
                            executionListener,
                            runContext.executionId()
                    );
                }
                break;
            }

            List<StepExecutionResult> results = dagPlanExecutor.executeParallelSteps(
                    runContext,
                    conversationContext,
                    selectedSkill,
                    parallelSteps,
                    executionLog,
                    executionListener
            );

            String terminalResponse = dagPlanExecutor.applyParallelStepResults(
                    planDag,
                    parallelSteps,
                    results,
                    executionLog
            );
            updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);
            if (StringUtils.hasText(terminalResponse)) {
                log.info("[{}][EARLY_RETURN] sessionId={}, responseLength={}",
                        LOG_STAGE,
                        runContext.executionId(), terminalResponse.length());
                executionListener.onFinalResponse(runContext.executionId(), terminalResponse);
                checkpointService.saveCompleted(checkpointId, checkpoint, terminalResponse);
                return terminalResponse;
            }

            List<String> lastWaveExecutionLog = results.stream()
                    .flatMap(result -> result.executionLogEntries().stream())
                    .toList();
            PlanContinuationDecision decision = reviewContinuation(
                    runContext,
                    planDag,
                    executionLog,
                    lastWaveExecutionLog
            );
            log.info("[{}][WAVE_REVIEW] sessionId={}, waveNumber={}, outcome={}, rationale={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    waveNumber,
                    decision.getOutcome(),
                    decision.getRationale());
            switch (decision.getOutcome()) {
                case CONTINUE -> {
                }
                case REPLAN -> {
                    if (replanCount >= Math.max(0, agentProperties.getMaxReplans())) {
                        executionLog.add("[system][replan-limit][CANNOT_COMPLETE] Reached maximum replans: "
                                + agentProperties.getMaxReplans());
                        log.info("[{}][REPLAN_LIMIT_REACHED] sessionId={}, maxReplans={}",
                                LOG_STAGE,
                                runContext.executionId(),
                                agentProperties.getMaxReplans());
                        planDag = markRemainingPendingAsSkipped(
                                planDag,
                                executionLog,
                                executionListener,
                                runContext.executionId()
                        );
                        break;
                    }
                    ReplanResult replanResult = replanPendingSteps(
                            runContext,
                            conversationContext,
                            skillContext,
                            planDag,
                            executionLog,
                            executionListener,
                            decision,
                            ++replanCount
                    );
                    if (StringUtils.hasText(replanResult.directResponse())) {
                        executionListener.onFinalResponse(runContext.executionId(), replanResult.directResponse());
                        checkpointService.saveCompleted(checkpointId, checkpoint, replanResult.directResponse());
                        return replanResult.directResponse();
                    }
                    planDag = replanResult.planDag();
                    selectedSkill = replanResult.selectedSkill() != null ? replanResult.selectedSkill() : selectedSkill;
                    updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);
                }
                case FINAL_RESPONSE -> {
                    planDag = markRemainingPendingAsSkipped(
                            planDag,
                            executionLog,
                            executionListener,
                            runContext.executionId()
                    );
                }
                case ASK_USER, CANNOT_COMPLETE -> {
                    if (StringUtils.hasText(decision.getUserMessage())) {
                        executionListener.onFinalResponse(runContext.executionId(), decision.getUserMessage());
                        checkpointService.saveCompleted(checkpointId, checkpoint, decision.getUserMessage());
                        return decision.getUserMessage();
                    }
                    planDag = markRemainingPendingAsSkipped(
                            planDag,
                            executionLog,
                            executionListener,
                            runContext.executionId()
                    );
                }
            }
        }

        dagPlanExecutor.markBlockedStepsAsFailed(planDag, executionLog, executionListener, runContext.executionId());
        updateCheckpoint(checkpointId, checkpoint, runContext, conversationContext, selectedSkill, planDag, executionLog, waveNumber, replanCount);

        String finalResponse = responseResolver.resolve(runContext, planDag.steps(), executionLog);
        executionListener.onFinalResponse(runContext.executionId(), finalResponse);
        checkpointService.saveCompleted(checkpointId, checkpoint, finalResponse);
        log.info("[{}][COMPLETE] sessionId={}, finalResponseLength={}",
                LOG_STAGE,
                runContext.executionId(), finalResponse.length());
        return finalResponse;
        } catch (RuntimeException exception) {
            checkpointService.saveFailed(checkpointId, checkpoint, exception.getMessage());
            throw exception;
        }
    }

    private void updateCheckpoint(String checkpointId,
                                  AgentRunCheckpoint checkpoint,
                                  AgentRunContext runContext,
                                  ConversationContext conversationContext,
                                  Skill selectedSkill,
                                  PlanDag planDag,
                                  List<String> executionLog,
                                  int waveNumber,
                                  int replanCount) {
        checkpoint.setTask(runContext.task());
        checkpoint.setExecutionId(runContext.executionId());
        checkpoint.setExecutionType(runContext.executionType());
        checkpoint.setReferenceType(runContext.referenceType());
        checkpoint.setReferenceId(runContext.referenceId());
        checkpoint.setUserId(runContext.userId());
        checkpoint.setPromptMemories(conversationContext.promptMemories());
        checkpoint.setSelectedSkillName(selectedSkill != null ? selectedSkill.getName() : checkpoint.getSelectedSkillName());
        checkpoint.setSelectedSkillInstructions(selectedSkill != null ? selectedSkill.getContent() : checkpoint.getSelectedSkillInstructions());
        checkpoint.setSteps(cloneSteps(planDag.steps()));
        checkpoint.setExecutionLog(List.copyOf(executionLog));
        checkpoint.setWaveNumber(waveNumber);
        checkpoint.setReplanCount(replanCount);
        checkpointService.saveRunning(checkpointId, checkpoint);
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

    private PlanContinuationDecision reviewContinuation(AgentRunContext runContext,
                                                        PlanDag planDag,
                                                        List<String> executionLog,
                                                        List<String> lastWaveExecutionLog) {
        PlanContinuationDecision decision = agentPlanner.reviewPlanContinuation(
                runContext.executionId(),
                runContext.executionType(),
                runContext.userId(),
                runContext.task(),
                planDag.steps(),
                executionLog,
                lastWaveExecutionLog,
                planDag.hasRunnablePendingSteps(),
                planDag.hasPendingSteps()
        );
        if (decision == null || decision.getOutcome() == null) {
            decision = new PlanContinuationDecision();
            if (!planDag.hasPendingSteps()) {
                decision.setOutcome(PlanContinuationDecision.Outcome.FINAL_RESPONSE);
                decision.setRationale("No pending steps remain.");
            } else if (!planDag.hasRunnablePendingSteps()) {
                decision.setOutcome(PlanContinuationDecision.Outcome.REPLAN);
                decision.setRationale("Pending steps are blocked.");
            } else {
                decision.setOutcome(PlanContinuationDecision.Outcome.CONTINUE);
                decision.setRationale("Runnable pending steps remain.");
            }
        }
        return decision;
    }

    private ReplanResult replanPendingSteps(AgentRunContext runContext,
                                            ConversationContext conversationContext,
                                            SkillContext skillContext,
                                            PlanDag currentDag,
                                            List<String> executionLog,
                                            AgentExecutionListener executionListener,
                                            PlanContinuationDecision decision,
                                            int replanCount) {
        List<String> immutableStepIds = currentDag.steps().stream()
                .filter(step -> step != null && step.getStatus() != LLMPlanResponse.PlanStep.Status.PENDING)
                .filter(step -> step.getStatus() != LLMPlanResponse.PlanStep.Status.RUNNING)
                .map(LLMPlanResponse.PlanStep::getId)
                .toList();
        LLMPlanResponse replanResponse = agentPlanner.replan(
                runContext.executionId(),
                runContext.executionType(),
                runContext.userId(),
                runContext.task(),
                conversationContext.promptMemories(),
                skillContext.plannerSkills(),
                runContext.tools(),
                cloneSteps(currentDag.steps()),
                executionLog,
                immutableStepIds,
                decision.getRationale()
        );
        if (replanResponse == null) {
            executionLog.add("[system][replan][CANNOT_COMPLETE] Replanner returned no response.");
            return new ReplanResult(markRemainingPendingAsSkipped(
                    currentDag,
                    executionLog,
                    executionListener,
                    runContext.executionId()
            ), null, null);
        }
        normalizePlannerResponse(replanResponse);
        if (StringUtils.hasText(replanResponse.getResponse()) && CollectionUtils.isEmpty(replanResponse.getSteps())) {
            log.info("[{}][REPLAN_DIRECT_RESPONSE] sessionId={}, responseLength={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    replanResponse.getResponse().length());
            return new ReplanResult(currentDag, null, replanResponse.getResponse());
        }

        List<LLMPlanResponse.PlanStep> mergedSteps = mergeImmutableAndReplannedSteps(
                currentDag.steps(),
                replanResponse.getSteps(),
                replanCount,
                executionLog
        );
        if (mergedSteps.stream().noneMatch(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)) {
            log.info("[{}][REPLAN_EMPTY] sessionId={}, replanCount={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    replanCount);
            return new ReplanResult(planGraphValidator.buildValidatedDag(mergedSteps), null, null);
        }

        PlanDag replannedDag = planGraphValidator.buildValidatedDag(mergedSteps);
        executionListener.onPlanCreated(
                runContext.executionId(),
                replanResponse.getThoughtProcess(),
                replanResponse.getSelectedSkill(),
                cloneSteps(replannedDag.steps())
        );
        Skill selectedSkill = skillSelectionService.resolveSelectedSkill(
                replanResponse.getSelectedSkill(),
                skillContext.availableSkills()
        );
        log.info("[{}][REPLAN_APPLIED] sessionId={}, replanCount={}, stepCount={}, pendingStepCount={}",
                LOG_STAGE,
                runContext.executionId(),
                replanCount,
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
