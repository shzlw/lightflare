package com.lightflare.server.workflow;

import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String DEFAULT_DEFINITION = "{\"version\":1,\"inputs\":[],\"triggers\":[],\"steps\":[]}";

    private final WorkflowRepository workflowRepository;
    private final WorkflowTriggerRepository triggerRepository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowStepRunRepository stepRunRepository;

    @Transactional
    public Workflow createWorkflow(String name,
                                   String description,
                                   String schemaDefinition,
                                   String status,
                                   String createdBy) {
        String normalizedDefinition = normalizeDefinition(schemaDefinition);
        String id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        workflowRepository.insertWorkflow(
                id,
                StringUtils.hasText(name) ? name.trim() : "Untitled workflow",
                description,
                StringUtils.hasText(status) ? status.trim().toLowerCase() : "draft",
                normalizedDefinition,
                createdBy,
                now,
                now
        );
        return workflowRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Workflow updateWorkflow(String id,
                                   String name,
                                   String description,
                                   String schemaDefinition,
                                   String status) {
        Workflow current = getWorkflow(id);
        String normalizedDefinition = schemaDefinition != null
                ? normalizeDefinition(schemaDefinition)
                : current.getSchemaDefinition();
        workflowRepository.updateWorkflow(
                id,
                StringUtils.hasText(name) ? name.trim() : current.getName(),
                description != null ? description : current.getDescription(),
                StringUtils.hasText(status) ? status.trim().toLowerCase() : current.getStatus(),
                normalizedDefinition,
                OffsetDateTime.now()
        );
        return workflowRepository.findById(id).orElseThrow();
    }

    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAllOrdered();
    }

    public Workflow getWorkflow(String id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
    }

    @Transactional
    public void deleteWorkflow(String id) {
        getWorkflow(id);
        stepRunRepository.deleteByWorkflowId(id);
        runRepository.deleteByWorkflowId(id);
        triggerRepository.deleteByWorkflowId(id);
        workflowRepository.deleteWorkflowById(id);
    }

    @Transactional
    public WorkflowTrigger createTrigger(String workflowId,
                                         String triggerType,
                                         String name,
                                         boolean enabled,
                                         String configJson) {
        getWorkflow(workflowId);
        String normalizedTriggerType = normalizeTriggerType(triggerType);
        String normalizedConfigJson = normalizeTriggerConfig(normalizedTriggerType, configJson);
        if ("scheduler".equals(normalizedTriggerType)) {
            for (WorkflowTrigger existing : triggerRepository.findByWorkflowIdAndType(workflowId, "scheduler")) {
                if (sameSchedulerConfig(existing.getConfigJson(), normalizedConfigJson)) {
                    return existing;
                }
            }
        }
        String id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        triggerRepository.insertTrigger(
                id,
                workflowId,
                normalizedTriggerType,
                name,
                enabled,
                normalizedConfigJson,
                now,
                now
        );
        return triggerRepository.findById(id).orElseThrow();
    }

    @Transactional
    public WorkflowTrigger updateTrigger(String workflowId,
                                         String triggerId,
                                         String triggerType,
                                         String name,
                                         Boolean enabled,
                                         String configJson) {
        getWorkflow(workflowId);
        WorkflowTrigger current = getTriggerForWorkflow(workflowId, triggerId);
        String normalizedTriggerType = StringUtils.hasText(triggerType)
                ? normalizeTriggerType(triggerType)
                : current.getTriggerType();
        String normalizedConfigJson = configJson != null
                ? normalizeTriggerConfig(normalizedTriggerType, configJson)
                : current.getConfigJson();
        triggerRepository.updateTrigger(
                triggerId,
                normalizedTriggerType,
                name != null ? name : current.getName(),
                enabled != null ? enabled : current.isEnabled(),
                normalizedConfigJson,
                OffsetDateTime.now()
        );
        return triggerRepository.findById(triggerId).orElseThrow();
    }

    @Transactional
    public Workflow setWorkflowEnabled(String workflowId, boolean enabled) {
        Workflow current = getWorkflow(workflowId);
        return updateWorkflow(
                workflowId,
                current.getName(),
                current.getDescription(),
                current.getSchemaDefinition(),
                enabled ? "active" : "disabled"
        );
    }

    @Transactional
    public void deleteTrigger(String workflowId, String triggerId) {
        getTriggerForWorkflow(workflowId, triggerId);
        triggerRepository.deleteTriggerById(triggerId);
    }

    public List<WorkflowTrigger> getTriggers(String workflowId) {
        getWorkflow(workflowId);
        return triggerRepository.findByWorkflowId(workflowId);
    }

    @Transactional
    public WorkflowTrigger createDefaultManualTriggerIfMissing(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
        List<WorkflowTrigger> existingTriggers = triggerRepository.findByWorkflowId(workflowId);
        if (!existingTriggers.isEmpty()) {
            return null;
        }
        return createTrigger(
                workflowId,
                "manual",
                "Run manually",
                true,
                defaultManualTriggerConfig(workflow.getSchemaDefinition())
        );
    }

    public WorkflowTrigger getTriggerForWorkflow(String workflowId, String triggerId) {
        WorkflowTrigger trigger = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow trigger not found: " + triggerId));
        if (!workflowId.equals(trigger.getWorkflowId())) {
            throw new IllegalArgumentException("Workflow trigger does not belong to workflow: " + workflowId);
        }
        return trigger;
    }

    public WorkflowRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
    }

    public List<WorkflowRun> getRecentRuns(String workflowId, int limit) {
        getWorkflow(workflowId);
        return runRepository.findRecentByWorkflowId(workflowId, Math.max(1, Math.min(limit, 100)));
    }

    public List<WorkflowStepRun> getStepRuns(String runId) {
        return stepRunRepository.findByWorkflowRunId(runId);
    }

    String normalizeDefinition(String schemaDefinition) {
        String definition = StringUtils.hasText(schemaDefinition) ? schemaDefinition : DEFAULT_DEFINITION;
        Object parsed = JsonUtils.fromJson(definition);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Workflow definition must be a JSON object.");
        }
        return definition;
    }

    @SuppressWarnings("unchecked")
    private String defaultManualTriggerConfig(String schemaDefinition) {
        Object parsed = JsonUtils.fromJson(StringUtils.hasText(schemaDefinition) ? schemaDefinition : DEFAULT_DEFINITION);
        List<Object> inputFields = List.of();
        if (parsed instanceof Map<?, ?> definition
                && definition.get("inputs") instanceof List<?> inputs) {
            inputFields = inputs.stream()
                    .filter(Map.class::isInstance)
                    .map(input -> new HashMap<>((Map<String, Object>) input))
                    .map(input -> (Object) input)
                    .toList();
        }
        return JsonUtils.toJson(Map.of("inputFields", inputFields));
    }

    private void validateJsonObject(String json, String fallback) {
        Object parsed = JsonUtils.fromJson(StringUtils.hasText(json) ? json : fallback);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("JSON value must be an object.");
        }
    }

    private String normalizeTriggerType(String triggerType) {
        if (!StringUtils.hasText(triggerType)) {
            throw new IllegalArgumentException("Workflow trigger type is required.");
        }
        String normalized = triggerType.trim().toLowerCase();
        if ("schedule".equals(normalized)) {
            normalized = "scheduler";
        }
        if (!List.of("manual", "scheduler", "webhook").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported workflow trigger type: " + triggerType);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private String normalizeTriggerConfig(String triggerType, String configJson) {
        Object parsed = JsonUtils.fromJson(StringUtils.hasText(configJson) ? configJson : "{}");
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Workflow trigger config must be a JSON object.");
        }
        Map<String, Object> config = new HashMap<>((Map<String, Object>) map);
        if ("scheduler".equals(triggerType)) {
            String cron = config.get("cron") instanceof String value ? value : null;
            if (!StringUtils.hasText(cron)) {
                throw new IllegalArgumentException("Scheduler trigger config_json.cron is required.");
            }
            config.put("cron", normalizeCronExpression(cron));
            if (!config.containsKey("nextRunAt")) {
                config.put("nextRunAt", computeNextRunAt(config, OffsetDateTime.now()).toString());
            }
        }
        return JsonUtils.toJson(config);
    }

    private boolean sameSchedulerConfig(String leftJson, String rightJson) {
        Map<String, Object> left = schedulerConfigIdentity(leftJson);
        Map<String, Object> right = schedulerConfigIdentity(rightJson);
        return left.equals(right);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schedulerConfigIdentity(String configJson) {
        Object parsed = JsonUtils.fromJson(StringUtils.hasText(configJson) ? configJson : "{}");
        if (!(parsed instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> config = new HashMap<>((Map<String, Object>) map);
        return Map.of(
                "cron", config.get("cron") != null ? config.get("cron") : "",
                "timezone", config.get("timezone") != null ? config.get("timezone") : "",
                "input", config.get("input") != null ? config.get("input") : Map.of()
        );
    }

    private String normalizeCronExpression(String cron) {
        String normalized = cron.trim().replaceAll("\\s+", " ");
        String[] fields = normalized.split(" ");
        if (fields.length == 5) {
            normalized = "0 " + normalized;
        } else if (fields.length == 6 && "*".equals(fields[0])) {
            normalized = "0 " + String.join(" ", java.util.Arrays.copyOfRange(fields, 1, fields.length));
        } else if (fields.length != 6) {
            throw new IllegalArgumentException("Scheduler cron must use 5-field unix or 6-field Spring format.");
        }
        try {
            CronExpression.parse(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid scheduler cron: " + cron + ". Use examples like '0 * * * * *' for every minute.");
        }
        return normalized;
    }

    private OffsetDateTime computeNextRunAt(Map<String, Object> config, OffsetDateTime fromTime) {
        String cron = config.get("cron") instanceof String value ? value : null;
        if (!StringUtils.hasText(cron)) {
            throw new IllegalArgumentException("Scheduler trigger config_json.cron is required.");
        }
        String timezone = config.get("timezone") instanceof String value && StringUtils.hasText(value)
                ? value
                : "UTC";
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime next = CronExpression.parse(cron).next(fromTime.atZoneSameInstant(zoneId));
        if (next == null) {
            throw new IllegalStateException("Cron expression does not produce a future run: " + cron);
        }
        return next.toOffsetDateTime();
    }
}
