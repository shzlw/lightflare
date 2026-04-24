package com.lightflare.server.application;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.utils.DateUtils;
import com.lightflare.server.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationRepository applicationRepository;
    private final ApplicationVersionRepository applicationVersionRepository;
    private final ApplicationStepRepository applicationStepRepository;
    private final ApplicationTriggerRepository applicationTriggerRepository;
    private final ApplicationEdgeRepository applicationEdgeRepository;
    private final ApplicationRunRepository applicationRunRepository;
    private final ApplicationStepRunRepository applicationStepRunRepository;
    private final ApplicationEngine applicationEngine;
    private final AuthService authService;

    public ApplicationPageResponse listApplications(int page, int size, String query, HttpServletRequest httpRequest) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String normalizedQuery = normalize(query);
        UserContext userContext = authService.requireUserContext(httpRequest);
        long totalItems = applicationRepository.countApplications(
                normalizedQuery,
                userContext.userId(),
                isAdmin(userContext)
        );
        List<ApplicationResponse> items = applicationRepository.findPage(
                        normalizedQuery,
                        userContext.userId(),
                        isAdmin(userContext),
                        normalizedSize,
                        (long) normalizedPage * normalizedSize
                ).stream()
                .map(this::toResponse)
                .toList();
        return ApplicationPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        OffsetDateTime now = DateUtils.now();
        String applicationId = hasText(request != null ? request.getId() : null)
                ? request.getId().trim()
                : UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String createdBy = resolveCreatedBy(request != null ? request.getCreatedBy() : null, userContext);

        int insertedApplication = applicationRepository.insertApplication(
                applicationId,
                defaultName(request != null ? request.getName() : null),
                normalize(request != null ? request.getDescription() : null),
                createdBy,
                normalize(request != null ? request.getSourceChatSessionId() : null),
                null,
                now,
                now
        );
        if (insertedApplication != 1) {
            throw new IllegalStateException("Expected one application row to be inserted but got " + insertedApplication);
        }

        int insertedVersion = applicationVersionRepository.insertVersion(
                versionId,
                applicationId,
                1,
                ApplicationVersion.STATUS_DRAFT,
                now
        );
        if (insertedVersion != 1) {
            throw new IllegalStateException("Expected one application_version row to be inserted but got " + insertedVersion);
        }

        return toResponse(findAccessibleApplication(applicationId, userContext));
    }

    public ApplicationDetailResponse getApplication(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(id, userContext);
        return ApplicationDetailResponse.builder()
                .application(toResponse(application))
                .versions(applicationVersionRepository.findByApplicationId(application.getId()).stream()
                        .map(this::toVersionResponse)
                        .toList())
                .recentRuns(applicationRunRepository.findRecentByApplicationId(application.getId(), DEFAULT_PAGE_SIZE).stream()
                        .map(this::toRunResponse)
                        .toList())
                .build();
    }

    public List<ApplicationVersionResponse> listVersions(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(id, userContext);
        return applicationVersionRepository.findByApplicationId(application.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    public List<ApplicationRunResponse> listRuns(String id, int limit, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(id, userContext);
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return applicationRunRepository.findRecentByApplicationId(application.getId(), normalizedLimit).stream()
                .map(this::toRunResponse)
                .toList();
    }

    @Transactional
    public ApplicationResponse updateApplication(String id, UpdateApplicationRequest request, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application current = findAccessibleApplication(id, userContext);
        String nextPublishedVersionId = current.getPublishedVersionId();
        if (request != null && request.getPublishedVersionId() != null) {
            String requestedVersionId = normalize(request.getPublishedVersionId());
            nextPublishedVersionId = requestedVersionId != null
                    ? findVersionForApplication(current.getId(), requestedVersionId).getId()
                    : null;
        }
        int updated = applicationRepository.updateApplication(
                current.getId(),
                request != null && request.getName() != null ? defaultName(request.getName()) : current.getName(),
                request != null && request.getDescription() != null ? normalize(request.getDescription()) : current.getDescription(),
                request != null && request.getSourceChatSessionId() != null
                        ? normalize(request.getSourceChatSessionId())
                        : current.getSourceChatSessionId(),
                nextPublishedVersionId,
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one application row to be updated but got " + updated);
        }
        return toResponse(findAccessibleApplication(id, userContext));
    }

    @Transactional
    public void deleteApplication(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(id, userContext);
        applicationStepRunRepository.deleteByApplicationId(application.getId());
        applicationRunRepository.deleteByApplicationId(application.getId());
        applicationTriggerRepository.deleteByApplicationId(application.getId());
        applicationEdgeRepository.deleteByApplicationId(application.getId());
        applicationStepRepository.deleteByApplicationId(application.getId());
        applicationVersionRepository.deleteByApplicationId(application.getId());
        int deleted = applicationRepository.deleteApplicationById(application.getId());
        if (deleted != 1) {
            throw new IllegalStateException("Expected one application row to be deleted but got " + deleted);
        }
    }

    public List<ApplicationTriggerResponse> listTriggers(String applicationId,
                                                         String versionId,
                                                         HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationVersion version = findVersionForAccessibleApplication(applicationId, versionId, userContext);
        return applicationTriggerRepository.findByApplicationVersionId(version.getId()).stream()
                .map(this::toTriggerResponse)
                .toList();
    }

    @Transactional
    public ApplicationTriggerResponse createTrigger(String applicationId,
                                                    String versionId,
                                                    CreateApplicationTriggerRequest request,
                                                    HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationVersion version = findVersionForAccessibleApplication(applicationId, versionId, userContext);
        String triggerId = UUID.randomUUID().toString();
        String triggerType = normalizeTriggerType(request != null ? request.getTriggerType() : null);
        String startStepId = validateStartStep(version.getId(), request != null ? request.getStartStepId() : null);
        String configJson = normalizeTriggerConfig(triggerType, request != null ? request.getConfigJson() : null);
        int inserted = applicationTriggerRepository.insertTrigger(
                triggerId,
                version.getId(),
                triggerType,
                startStepId,
                configJson
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one application_trigger row to be inserted but got " + inserted);
        }
        return toTriggerResponse(applicationTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new IllegalStateException("Application trigger not found after insert: " + triggerId)));
    }

    @Transactional
    public ApplicationTriggerResponse updateTrigger(String applicationId,
                                                    String versionId,
                                                    String triggerId,
                                                    UpdateApplicationTriggerRequest request,
                                                    HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationVersion version = findVersionForAccessibleApplication(applicationId, versionId, userContext);
        ApplicationTrigger current = findTriggerForVersion(version.getId(), triggerId);
        String triggerType = request != null && request.getTriggerType() != null
                ? normalizeTriggerType(request.getTriggerType())
                : current.getTriggerType();
        String startStepId = request != null && request.getStartStepId() != null
                ? validateStartStep(version.getId(), request.getStartStepId())
                : current.getStartStepId();
        String configJson = request != null && request.getConfigJson() != null
                ? normalizeTriggerConfig(triggerType, request.getConfigJson())
                : normalizeTriggerConfig(triggerType, current.getConfigJson());
        int updated = applicationTriggerRepository.updateTrigger(
                current.getId(),
                triggerType,
                startStepId,
                configJson
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one application_trigger row to be updated but got " + updated);
        }
        return toTriggerResponse(applicationTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new IllegalStateException("Application trigger not found after update: " + triggerId)));
    }

    @Transactional
    public void deleteTrigger(String applicationId, String versionId, String triggerId, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationVersion version = findVersionForAccessibleApplication(applicationId, versionId, userContext);
        findTriggerForVersion(version.getId(), triggerId);
        int deleted = applicationTriggerRepository.deleteTriggerById(triggerId);
        if (deleted != 1) {
            throw new IllegalStateException("Expected one application_trigger row to be deleted but got " + deleted);
        }
    }

    public ApplicationRunResponse getRun(String runId, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationRun run = applicationRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Application run not found: " + runId));
        findAccessibleApplication(run.getApplicationId(), userContext);
        return toRunResponse(run);
    }

    public List<ApplicationStepRunResponse> getStepRuns(String runId, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ApplicationRun run = applicationRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Application run not found: " + runId));
        findAccessibleApplication(run.getApplicationId(), userContext);
        return applicationStepRunRepository.findByApplicationRunId(runId).stream()
                .map(this::toStepRunResponse)
                .toList();
    }

    public String startExecution(String applicationId,
                                 String versionId,
                                 Map<String, Object> initialData,
                                 String startStepId,
                                 HttpServletRequest httpRequest) {
        return startExecution(
                applicationId,
                versionId,
                initialData,
                startStepId,
                httpRequest,
                ApplicationExecutionListener.NOOP
        );
    }

    public String startExecution(String applicationId,
                                 String versionId,
                                 Map<String, Object> initialData,
                                 String startStepId,
                                 HttpServletRequest httpRequest,
                                 ApplicationExecutionListener listener) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(applicationId, userContext);
        ApplicationVersion version = resolveExecutionVersion(application, versionId);
        String validatedStartStepId = startStepId != null ? validateStartStep(version.getId(), startStepId) : null;
        return applicationEngine.execute(
                application.getId(),
                initialData != null ? initialData : Map.of(),
                version.getId(),
                validatedStartStepId,
                userContext.userId(),
                null,
                listener
        );
    }

    public String startTriggerExecution(String applicationId,
                                        String versionId,
                                        String triggerId,
                                        Map<String, Object> initialData,
                                        String startStepId,
                                        HttpServletRequest httpRequest) {
        return startTriggerExecution(
                applicationId,
                versionId,
                triggerId,
                initialData,
                startStepId,
                httpRequest,
                ApplicationExecutionListener.NOOP
        );
    }

    public String startTriggerExecution(String applicationId,
                                        String versionId,
                                        String triggerId,
                                        Map<String, Object> initialData,
                                        String startStepId,
                                        HttpServletRequest httpRequest,
                                        ApplicationExecutionListener listener) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Application application = findAccessibleApplication(applicationId, userContext);
        ApplicationVersion version = findVersionForApplication(application.getId(), versionId);
        ApplicationTrigger trigger = findTriggerForVersion(version.getId(), triggerId);
        String validatedStartStepId = startStepId != null ? validateStartStep(version.getId(), startStepId) : null;
        return applicationEngine.execute(
                application.getId(),
                initialData != null ? initialData : Map.of(),
                version.getId(),
                validatedStartStepId,
                userContext.userId(),
                trigger.getId(),
                listener
        );
    }

    private Application findAccessibleApplication(String id, UserContext userContext) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + id));
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), application.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return application;
    }

    private ApplicationVersion findVersionForAccessibleApplication(String applicationId,
                                                                   String versionId,
                                                                   UserContext userContext) {
        Application application = findAccessibleApplication(applicationId, userContext);
        return findVersionForApplication(application.getId(), versionId);
    }

    private ApplicationVersion findVersionForApplication(String applicationId, String versionId) {
        ApplicationVersion version = applicationVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Application version not found: " + versionId));
        if (!applicationId.equals(version.getApplicationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application version not found");
        }
        return version;
    }

    private ApplicationVersion resolveExecutionVersion(Application application, String requestedVersionId) {
        if (hasText(requestedVersionId)) {
            return findVersionForApplication(application.getId(), requestedVersionId.trim());
        }
        if (hasText(application.getPublishedVersionId())) {
            return findVersionForApplication(application.getId(), application.getPublishedVersionId());
        }
        return applicationVersionRepository.findLatestByApplicationId(application.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application has no versions"));
    }

    private ApplicationTrigger findTriggerForVersion(String applicationVersionId, String triggerId) {
        ApplicationTrigger trigger = applicationTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new NoSuchElementException("Application trigger not found: " + triggerId));
        if (!applicationVersionId.equals(trigger.getApplicationVersionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application trigger not found");
        }
        return trigger;
    }

    private String validateStartStep(String applicationVersionId, String startStepId) {
        String normalizedStartStepId = normalize(startStepId);
        if (!hasText(normalizedStartStepId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startStepId is required");
        }
        boolean exists = applicationStepRepository.findByApplicationVersionId(applicationVersionId).stream()
                .anyMatch(step -> normalizedStartStepId.equals(step.getId()));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startStepId must belong to the application version");
        }
        return normalizedStartStepId;
    }

    private String normalizeTriggerType(String triggerType) {
        String normalized = normalize(triggerType);
        if (!hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "triggerType is required");
        }
        String value = normalized.toLowerCase();
        if (!"manual".equals(value) && !"webhook".equals(value) && !"cron".equals(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "triggerType must be manual, webhook, or cron");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private String normalizeTriggerConfig(String triggerType, String configJson) {
        String rawConfig = hasText(configJson) ? configJson.trim() : "{}";
        Object parsed = JsonUtils.fromJson(rawConfig);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson must be a JSON object");
        }
        Map<String, Object> normalized = new HashMap<>((Map<String, Object>) map);
        if ("cron".equals(triggerType)) {
            String cron = normalize(stringValue(normalized.get("cron")));
            if (!hasText(cron)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron trigger configJson.cron is required");
            }
            String timezone = hasText(stringValue(normalized.get("timezone")))
                    ? stringValue(normalized.get("timezone")).trim()
                    : "UTC";
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timezone);
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron timezone");
            }
            CronExpression expression;
            try {
                expression = CronExpression.parse(cron);
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression");
            }
            normalized.put("cron", cron);
            normalized.put("timezone", timezone);
            if (!normalized.containsKey("nextRunAt") || !hasText(stringValue(normalized.get("nextRunAt")))) {
                ZonedDateTime next = expression.next(DateUtils.now().atZoneSameInstant(zoneId));
                if (next == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron expression does not produce a future run");
                }
                normalized.put("nextRunAt", next.toOffsetDateTime().toString());
            }
        }
        return JsonUtils.toJson(normalized);
    }

    private String resolveCreatedBy(String requestedCreatedBy, UserContext userContext) {
        if (!isAdmin(userContext)) {
            if (hasText(requestedCreatedBy) && !requestedCreatedBy.trim().equals(userContext.userId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create applications for another user");
            }
            return userContext.userId();
        }
        return hasText(requestedCreatedBy) ? requestedCreatedBy.trim() : userContext.userId();
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private String defaultName(String name) {
        return hasText(name) ? name.trim() : "Untitled application";
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private ApplicationResponse toResponse(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .name(application.getName())
                .description(application.getDescription())
                .createdBy(application.getCreatedBy())
                .sourceChatSessionId(application.getSourceChatSessionId())
                .publishedVersionId(application.getPublishedVersionId())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    private ApplicationVersionResponse toVersionResponse(ApplicationVersion version) {
        return ApplicationVersionResponse.builder()
                .id(version.getId())
                .applicationId(version.getApplicationId())
                .versionNumber(version.getVersionNumber() != null ? version.getVersionNumber() : 0)
                .status(version.getStatus())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private ApplicationTriggerResponse toTriggerResponse(ApplicationTrigger trigger) {
        return ApplicationTriggerResponse.builder()
                .id(trigger.getId())
                .applicationVersionId(trigger.getApplicationVersionId())
                .triggerType(trigger.getTriggerType())
                .startStepId(trigger.getStartStepId())
                .configJson(trigger.getConfigJson())
                .build();
    }

    private ApplicationRunResponse toRunResponse(ApplicationRun run) {
        return ApplicationRunResponse.builder()
                .id(run.getId())
                .applicationId(run.getApplicationId())
                .applicationVersionId(run.getApplicationVersionId())
                .triggerId(run.getTriggerId())
                .status(run.getStatus())
                .inputJson(run.getInputJson())
                .outputJson(run.getOutputJson())
                .errorMessage(run.getErrorMessage())
                .startedBy(run.getStartedBy())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    private ApplicationStepRunResponse toStepRunResponse(ApplicationStepRun stepRun) {
        return ApplicationStepRunResponse.builder()
                .id(stepRun.getId())
                .applicationRunId(stepRun.getApplicationRunId())
                .stepId(stepRun.getStepId())
                .status(stepRun.getStatus())
                .inputJson(stepRun.getInputJson())
                .outputJson(stepRun.getOutputJson())
                .errorMessage(stepRun.getErrorMessage())
                .startedAt(stepRun.getStartedAt())
                .completedAt(stepRun.getCompletedAt())
                .build();
    }
}
