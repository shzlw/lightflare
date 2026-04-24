package com.lightflare.server.internaltools.application;

import com.lightflare.server.application.Application;
import com.lightflare.server.application.ApplicationEdgeRepository;
import com.lightflare.server.application.ApplicationEngine;
import com.lightflare.server.application.ApplicationRepository;
import com.lightflare.server.application.ApplicationRunRepository;
import com.lightflare.server.application.ApplicationStep;
import com.lightflare.server.application.ApplicationStepRepository;
import com.lightflare.server.application.ApplicationStepRunRepository;
import com.lightflare.server.application.ApplicationTrigger;
import com.lightflare.server.application.ApplicationTriggerRepository;
import com.lightflare.server.application.ApplicationVersion;
import com.lightflare.server.application.ApplicationVersionRepository;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.DateUtils;
import com.lightflare.server.utils.FileUtils;
import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ManageApplicationTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("manage-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-application")
            .description("Create, update, delete, inspect, run, and manage triggers for applications.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("action")
                            .type("string")
                            .description("schema, list, get, upsert, delete, create-trigger, update-trigger, delete-trigger, run, runs, or run-steps.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("application_id")
                            .type("string")
                            .description("Application id for get/update/delete/trigger/run actions.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("version_id")
                            .type("string")
                            .description("Application version id for trigger and run actions.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("trigger_id")
                            .type("string")
                            .description("Application trigger id for update-trigger/delete-trigger/run.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("name")
                            .type("string")
                            .description("Application name.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("description")
                            .type("string")
                            .description("Application description.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("source_chat_session_id")
                            .type("string")
                            .description("Optional source chat session id.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("published_version_id")
                            .type("string")
                            .description("Optional published version id.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("triggers")
                            .type("array")
                            .description("Optional trigger definitions to create during upsert. Each item supports trigger_type/type, start_step_id/startStepId, and config_json/config.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("trigger_type")
                            .type("string")
                            .description("manual, webhook, or cron.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("start_step_id")
                            .type("string")
                            .description("Step id for the trigger entrypoint.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("config_json")
                            .type("string")
                            .description("Trigger config JSON as string or object.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("input_data")
                            .type("object")
                            .description("Input object for application execution.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("execution_id")
                            .type("string")
                            .description("Application run id for run-steps.")
                            .required(false)
                            .build()
            ))
            .build();

    private final ApplicationRepository applicationRepository;
    private final ApplicationVersionRepository applicationVersionRepository;
    private final ApplicationStepRepository applicationStepRepository;
    private final ApplicationTriggerRepository applicationTriggerRepository;
    private final ApplicationEdgeRepository applicationEdgeRepository;
    private final ApplicationRunRepository applicationRunRepository;
    private final ApplicationStepRunRepository applicationStepRunRepository;
    private final ApplicationEngine applicationEngine;

    public ToolDefinition definition() {
        return DEFINITION;
    }

    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String action = requiredString(arguments, "action");
        try {
            return switch (action.trim().toLowerCase()) {
                case "schema", "help" -> success(Map.of(
                        "application", Map.of("name", "Untitled application", "description", ""),
                        "versionStatuses", List.of("draft", "published", "archived"),
                        "triggerTypes", List.of("manual", "webhook", "cron"),
                        "actions", List.of("list", "get", "upsert", "delete", "create-trigger",
                                "update-trigger", "delete-trigger", "run", "runs", "run-steps")
                ));
                case "list" -> listApplications();
                case "get" -> getApplication(arguments);
                case "upsert" -> handleUpsert(arguments, context);
                case "delete" -> deleteApplication(arguments);
                case "create-trigger" -> handleCreateTrigger(arguments);
                case "update-trigger" -> handleUpdateTrigger(arguments);
                case "delete-trigger" -> handleDeleteTrigger(arguments);
                case "run" -> runApplication(arguments, context);
                case "runs" -> applicationRuns(arguments);
                case "run-steps" -> applicationRunSteps(arguments);
                default -> ToolResult.failure("Unknown application action: " + action);
            };
        } catch (Exception e) {
            return ToolResult.failure("Application action failed: " + e.getMessage());
        }
    }

    public ToolResult listApplications() {
        return success(applicationRepository.findPage(null, null, true, 100, 0));
    }

    public ToolResult getApplication(List<ToolArgument> arguments) {
        String applicationId = requiredString(arguments, "application_id");
        Application application = getExistingApplication(applicationId);
        return success(Map.of(
                "application", application,
                "versions", applicationVersionRepository.findByApplicationId(applicationId),
                "triggers", listAllTriggers(applicationId),
                "recentRuns", applicationRunRepository.findRecentByApplicationId(applicationId, 10)
        ));
    }

    public ToolResult createApplication(List<ToolArgument> arguments, ToolExecutionContext context) {
        if (StringUtils.hasText(optionalString(arguments, "application_id"))) {
            throw new IllegalArgumentException("create-application does not accept application_id. Use update-application to modify an existing application.");
        }
        return handleUpsert(arguments, context);
    }

    public ToolResult updateApplication(List<ToolArgument> arguments, ToolExecutionContext context) {
        requiredString(arguments, "application_id");
        return handleUpsert(arguments, context);
    }

    public ToolResult deleteApplication(List<ToolArgument> arguments) {
        return handleDelete(arguments);
    }

    public ToolResult manageTrigger(List<ToolArgument> arguments) {
        String action = requiredString(arguments, "action");
        return switch (action.trim().toLowerCase()) {
            case "create" -> handleCreateTrigger(arguments);
            case "update" -> handleUpdateTrigger(arguments);
            case "delete" -> handleDeleteTrigger(arguments);
            default -> ToolResult.failure("Unknown application trigger action: " + action);
        };
    }

    public ToolResult runApplication(List<ToolArgument> arguments, ToolExecutionContext context) {
        String applicationId = requiredString(arguments, "application_id");
        String versionId = optionalString(arguments, "version_id");
        String triggerId = optionalString(arguments, "trigger_id");
        Map<String, Object> inputData = optionalObject(arguments, "input_data", "initial_data");
        String executionId = applicationEngine.execute(
                applicationId,
                inputData,
                versionId,
                null,
                context != null ? context.userId() : null,
                triggerId,
                null
        );
        return success(Map.of("executionId", executionId));
    }

    public ToolResult applicationRuns(List<ToolArgument> arguments) {
        return success(applicationRunRepository.findRecentByApplicationId(requiredString(arguments, "application_id"), 20));
    }

    public ToolResult applicationRunSteps(List<ToolArgument> arguments) {
        return success(applicationStepRunRepository.findByApplicationRunId(requiredString(arguments, "execution_id")));
    }

    private ToolResult handleUpsert(List<ToolArgument> arguments, ToolExecutionContext context) {
        String applicationId = optionalString(arguments, "application_id");
        List<Object> triggerDefinitions = optionalArray(arguments, "triggers", "application_triggers", "applicationTriggers");
        if (StringUtils.hasText(applicationId)) {
            Application current = getExistingApplication(applicationId);
            String nextPublishedVersionId = optionalString(arguments, "published_version_id");
            if (StringUtils.hasText(nextPublishedVersionId)) {
                requireVersion(current.getId(), nextPublishedVersionId);
            } else {
                nextPublishedVersionId = current.getPublishedVersionId();
            }
            int updated = applicationRepository.updateApplication(
                    current.getId(),
                    optionalString(arguments, "name") != null ? optionalString(arguments, "name") : current.getName(),
                    optionalString(arguments, "description") != null ? optionalString(arguments, "description") : current.getDescription(),
                    optionalString(arguments, "source_chat_session_id") != null
                            ? optionalString(arguments, "source_chat_session_id")
                            : current.getSourceChatSessionId(),
                    nextPublishedVersionId,
                    DateUtils.now()
            );
            if (updated != 1) {
                throw new IllegalStateException("Expected one application row to be updated but got " + updated);
            }
            List<ApplicationTrigger> createdTriggers = createTriggers(current.getId(), triggerDefinitions);
            return success(Map.of(
                    "application", getExistingApplication(current.getId()),
                    "createdTriggers", createdTriggers,
                    "applicationId", current.getId()
            ));
        }

        String id = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        OffsetDateTime now = DateUtils.now();
        int inserted = applicationRepository.insertApplication(
                id,
                StringUtils.hasText(optionalString(arguments, "name")) ? optionalString(arguments, "name") : "Untitled application",
                optionalString(arguments, "description"),
                context != null ? context.userId() : null,
                optionalString(arguments, "source_chat_session_id"),
                optionalString(arguments, "published_version_id"),
                now,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one application row to be inserted but got " + inserted);
        }
        int insertedVersion = applicationVersionRepository.insertVersion(
                versionId,
                id,
                1,
                ApplicationVersion.STATUS_DRAFT,
                now
        );
        if (insertedVersion != 1) {
            throw new IllegalStateException("Expected one application_version row to be inserted but got " + insertedVersion);
        }
        List<ApplicationTrigger> createdTriggers = createTriggers(id, triggerDefinitions);
        return success(Map.of(
                "application", getExistingApplication(id),
                "initialVersion", requireVersion(id, versionId),
                "createdTriggers", createdTriggers,
                "applicationId", id
        ));
    }

    private ToolResult handleDelete(List<ToolArgument> arguments) {
        String applicationId = requiredString(arguments, "application_id");
        getExistingApplication(applicationId);
        applicationStepRunRepository.deleteByApplicationId(applicationId);
        applicationRunRepository.deleteByApplicationId(applicationId);
        applicationTriggerRepository.deleteByApplicationId(applicationId);
        applicationEdgeRepository.deleteByApplicationId(applicationId);
        applicationStepRepository.deleteByApplicationId(applicationId);
        applicationVersionRepository.deleteByApplicationId(applicationId);
        applicationRepository.deleteApplicationById(applicationId);
        return ToolResult.success("Application deleted.");
    }

    private ToolResult handleCreateTrigger(List<ToolArgument> arguments) {
        String applicationId = requiredString(arguments, "application_id");
        String versionId = requiredString(arguments, "version_id");
        ApplicationVersion version = requireVersion(applicationId, versionId);
        ApplicationTrigger trigger = createTrigger(
                version.getId(),
                requiredString(arguments, "trigger_type"),
                requiredString(arguments, "start_step_id"),
                optionalJson(arguments, "config_json", "config")
        );
        return success(trigger);
    }

    private ToolResult handleUpdateTrigger(List<ToolArgument> arguments) {
        String applicationId = requiredString(arguments, "application_id");
        String versionId = requiredString(arguments, "version_id");
        ApplicationVersion version = requireVersion(applicationId, versionId);
        ApplicationTrigger current = requireTrigger(version.getId(), requiredString(arguments, "trigger_id"));
        String nextTriggerType = optionalString(arguments, "trigger_type");
        String nextStartStepId = optionalString(arguments, "start_step_id");
        String nextConfigJson = optionalJson(arguments, "config_json", "config");
        int updated = applicationTriggerRepository.updateTrigger(
                current.getId(),
                StringUtils.hasText(nextTriggerType) ? normalizeTriggerType(nextTriggerType) : current.getTriggerType(),
                StringUtils.hasText(nextStartStepId) ? validateStep(version.getId(), nextStartStepId) : current.getStartStepId(),
                nextConfigJson != null
                        ? normalizeTriggerConfig(
                        StringUtils.hasText(nextTriggerType) ? nextTriggerType : current.getTriggerType(),
                        nextConfigJson
                )
                        : normalizeTriggerConfig(current.getTriggerType(), current.getConfigJson())
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one application_trigger row to be updated but got " + updated);
        }
        return success(requireTrigger(version.getId(), current.getId()));
    }

    private ToolResult handleDeleteTrigger(List<ToolArgument> arguments) {
        String applicationId = requiredString(arguments, "application_id");
        String versionId = requiredString(arguments, "version_id");
        ApplicationVersion version = requireVersion(applicationId, versionId);
        String triggerId = requiredString(arguments, "trigger_id");
        requireTrigger(version.getId(), triggerId);
        applicationTriggerRepository.deleteTriggerById(triggerId);
        return ToolResult.success("Application trigger deleted.");
    }

    private List<ApplicationTrigger> createTriggers(String applicationId, List<Object> triggerDefinitions) {
        if (triggerDefinitions == null || triggerDefinitions.isEmpty()) {
            return List.of();
        }
        String versionId = applicationVersionRepository.findLatestByApplicationId(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application has no versions: " + applicationId))
                .getId();
        return triggerDefinitions.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> trigger = (Map<String, Object>) item;
                    return createTrigger(
                            versionId,
                            triggerType(trigger),
                            triggerStartStepId(trigger),
                            jsonValue(trigger.get("config_json"), trigger.get("configJson"), trigger.get("config"))
                    );
                })
                .toList();
    }

    private ApplicationTrigger createTrigger(String applicationVersionId,
                                             String triggerType,
                                             String startStepId,
                                             String configJson) {
        String id = UUID.randomUUID().toString();
        int inserted = applicationTriggerRepository.insertTrigger(
                id,
                applicationVersionId,
                normalizeTriggerType(triggerType),
                validateStep(applicationVersionId, startStepId),
                normalizeTriggerConfig(triggerType, configJson)
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one application_trigger row to be inserted but got " + inserted);
        }
        return requireTrigger(applicationVersionId, id);
    }

    private List<ApplicationTrigger> listAllTriggers(String applicationId) {
        return applicationVersionRepository.findByApplicationId(applicationId).stream()
                .flatMap(version -> applicationTriggerRepository.findByApplicationVersionId(version.getId()).stream())
                .toList();
    }

    private Application getExistingApplication(String applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));
    }

    private ApplicationVersion requireVersion(String applicationId, String versionId) {
        ApplicationVersion version = applicationVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Application version not found: " + versionId));
        if (!applicationId.equals(version.getApplicationId())) {
            throw new IllegalArgumentException("Application version does not belong to application: " + applicationId);
        }
        return version;
    }

    private ApplicationTrigger requireTrigger(String applicationVersionId, String triggerId) {
        ApplicationTrigger trigger = applicationTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new NoSuchElementException("Application trigger not found: " + triggerId));
        if (!applicationVersionId.equals(trigger.getApplicationVersionId())) {
            throw new IllegalArgumentException("Application trigger does not belong to version: " + applicationVersionId);
        }
        return trigger;
    }

    private String validateStep(String applicationVersionId, String stepId) {
        String normalizedStepId = stepId != null ? stepId.trim() : null;
        if (!StringUtils.hasText(normalizedStepId)) {
            throw new IllegalArgumentException("Missing required argument: start_step_id");
        }
        boolean exists = applicationStepRepository.findByApplicationVersionId(applicationVersionId).stream()
                .map(ApplicationStep::getId)
                .anyMatch(normalizedStepId::equals);
        if (!exists) {
            throw new IllegalArgumentException("start_step_id must belong to the target application version");
        }
        return normalizedStepId;
    }

    private String normalizeTriggerType(String triggerType) {
        if (!StringUtils.hasText(triggerType)) {
            throw new IllegalArgumentException("Missing required argument: trigger_type");
        }
        String normalized = triggerType.trim().toLowerCase();
        if (!List.of("manual", "webhook", "cron").contains(normalized)) {
            throw new IllegalArgumentException("trigger_type must be manual, webhook, or cron");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private String normalizeTriggerConfig(String triggerType, String configJson) {
        String raw = StringUtils.hasText(configJson) ? configJson : "{}";
        Object parsed = JsonUtils.fromJson(raw);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("config_json must be a JSON object");
        }
        Map<String, Object> config = new HashMap<>((Map<String, Object>) map);
        if ("cron".equalsIgnoreCase(normalizeTriggerType(triggerType))) {
            String cron = stringValue(config.get("cron"));
            if (!StringUtils.hasText(cron)) {
                throw new IllegalArgumentException("Cron trigger config_json.cron is required");
            }
            String timezone = StringUtils.hasText(stringValue(config.get("timezone")))
                    ? stringValue(config.get("timezone")).trim()
                    : "UTC";
            CronExpression expression = CronExpression.parse(cron.trim());
            if (!StringUtils.hasText(stringValue(config.get("nextRunAt")))) {
                ZonedDateTime next = expression.next(DateUtils.now().atZoneSameInstant(ZoneId.of(timezone)));
                if (next != null) {
                    config.put("nextRunAt", next.toOffsetDateTime().toString());
                }
            }
            config.put("cron", cron.trim());
            config.put("timezone", timezone);
        }
        return JsonUtils.toJson(config);
    }

    private ToolResult success(Object value) {
        return ToolResult.success(JsonUtils.toJson(value));
    }

    private String requiredString(List<ToolArgument> arguments, String name) {
        String value = optionalString(arguments, name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private String optionalString(List<ToolArgument> arguments, String name) {
        ToolArgument argument = findArgument(arguments, name);
        return argument != null ? argument.asString() : null;
    }

    private String optionalJson(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof String value) {
                return StringUtils.hasText(value) ? value : null;
            }
            return JsonUtils.toJson(argument.getValue());
        }
        return null;
    }

    private Map<String, Object> optionalObject(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof Map<?, ?>) {
                return argument.asObject();
            }
            if (argument.getValue() instanceof String value && StringUtils.hasText(value)) {
                Object parsed = JsonUtils.fromJson(value);
                if (parsed instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> object = (Map<String, Object>) map;
                    return object;
                }
            }
        }
        return Collections.emptyMap();
    }

    private List<Object> optionalArray(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof List<?>) {
                return argument.asArray();
            }
            if (argument.getValue() instanceof String value && StringUtils.hasText(value)) {
                Object parsed = JsonUtils.fromJson(value);
                if (parsed instanceof List<?> list) {
                    return List.copyOf(list);
                }
            }
        }
        return List.of();
    }

    private String triggerType(Map<String, Object> trigger) {
        String value = stringValue(trigger.get("trigger_type"));
        if (!StringUtils.hasText(value)) {
            value = stringValue(trigger.get("triggerType"));
        }
        if (!StringUtils.hasText(value)) {
            value = stringValue(trigger.get("type"));
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Trigger definition is missing trigger_type.");
        }
        return value;
    }

    private String triggerStartStepId(Map<String, Object> trigger) {
        String value = stringValue(trigger.get("start_step_id"));
        if (!StringUtils.hasText(value)) {
            value = stringValue(trigger.get("startStepId"));
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Trigger definition is missing start_step_id.");
        }
        return value;
    }

    private String jsonValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String stringValue) {
                return StringUtils.hasText(stringValue) ? stringValue : null;
            }
            return JsonUtils.toJson(value);
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private ToolArgument findArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .findFirst()
                .orElse(null);
    }
}
