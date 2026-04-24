package com.lightflare.server.application;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/internal-api/v1/applications")
public class InternalApplicationController {

    private final ApplicationService applicationService;
    private final Executor applicationExecutionExecutor;

    public InternalApplicationController(ApplicationService applicationService,
                                         @Qualifier("applicationExecutionExecutor") Executor applicationExecutionExecutor) {
        this.applicationService = applicationService;
        this.applicationExecutionExecutor = applicationExecutionExecutor;
    }

    public record StartApplicationExecutionRequest(
            Map<String, Object> inputData,
            Map<String, Object> initialData,
            String versionId,
            String startStepId
    ) {
    }

    public record ApplicationStreamEvent(
            String type,
            String applicationId,
            String executionId,
            String stepRunId,
            String stepId,
            String stepName,
            String stepType,
            String status,
            Object input,
            Object output,
            String errorMessage
    ) {
    }

    @GetMapping
    public ApplicationPageResponse listApplications(@RequestParam(name = "page", defaultValue = "0") int page,
                                                    @RequestParam(name = "size", defaultValue = "20") int size,
                                                    @RequestParam(name = "q", required = false) String q,
                                                    HttpServletRequest httpRequest) {
        return applicationService.listApplications(page, size, q, httpRequest);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(@RequestBody CreateApplicationRequest request,
                                                 HttpServletRequest httpRequest) {
        return applicationService.createApplication(request, httpRequest);
    }

    @GetMapping("/{id}")
    public ApplicationDetailResponse getApplication(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return applicationService.getApplication(id, httpRequest);
    }

    @GetMapping("/{id}/versions")
    public List<ApplicationVersionResponse> listVersions(@PathVariable("id") String id,
                                                         HttpServletRequest httpRequest) {
        return applicationService.listVersions(id, httpRequest);
    }

    @GetMapping("/{id}/versions/{versionId}/triggers")
    public List<ApplicationTriggerResponse> listTriggers(@PathVariable("id") String id,
                                                         @PathVariable("versionId") String versionId,
                                                         HttpServletRequest httpRequest) {
        return applicationService.listTriggers(id, versionId, httpRequest);
    }

    @PostMapping("/{id}/versions/{versionId}/triggers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationTriggerResponse createTrigger(@PathVariable("id") String id,
                                                    @PathVariable("versionId") String versionId,
                                                    @RequestBody CreateApplicationTriggerRequest request,
                                                    HttpServletRequest httpRequest) {
        return applicationService.createTrigger(id, versionId, request, httpRequest);
    }

    @PutMapping("/{id}/versions/{versionId}/triggers/{triggerId}")
    public ApplicationTriggerResponse updateTrigger(@PathVariable("id") String id,
                                                    @PathVariable("versionId") String versionId,
                                                    @PathVariable("triggerId") String triggerId,
                                                    @RequestBody UpdateApplicationTriggerRequest request,
                                                    HttpServletRequest httpRequest) {
        return applicationService.updateTrigger(id, versionId, triggerId, request, httpRequest);
    }

    @DeleteMapping("/{id}/versions/{versionId}/triggers/{triggerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrigger(@PathVariable("id") String id,
                              @PathVariable("versionId") String versionId,
                              @PathVariable("triggerId") String triggerId,
                              HttpServletRequest httpRequest) {
        applicationService.deleteTrigger(id, versionId, triggerId, httpRequest);
    }

    @GetMapping("/{id}/runs")
    public List<ApplicationRunResponse> listRuns(@PathVariable("id") String id,
                                                 @RequestParam(name = "limit", defaultValue = "20") int limit,
                                                 HttpServletRequest httpRequest) {
        return applicationService.listRuns(id, limit, httpRequest);
    }

    @PostMapping("/{id}/executions")
    public ResponseEntity<Map<String, String>> startExecution(@PathVariable("id") String id,
                                                              @RequestBody(required = false) StartApplicationExecutionRequest request,
                                                              HttpServletRequest httpRequest) {
        String executionId = applicationService.startExecution(
                id,
                request != null ? request.versionId() : null,
                initialData(request),
                request != null ? request.startStepId() : null,
                httpRequest
        );
        return ResponseEntity.ok(Map.of("executionId", executionId));
    }

    @PostMapping(value = "/{id}/executions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecution(@PathVariable("id") String id,
                                      @RequestBody(required = false) StartApplicationExecutionRequest request,
                                      HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        Map<String, Object> initialData = initialData(request);
        String versionId = request != null ? request.versionId() : null;
        String startStepId = request != null ? request.startStepId() : null;
        applicationExecutionExecutor.execute(() -> {
            ApplicationExecutionListener listener = sseApplicationExecutionListener(emitter, id);
            try {
                applicationService.startExecution(id, versionId, initialData, startStepId, httpRequest, listener);
                emitter.complete();
            } catch (Exception exception) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "RUN_FAILED",
                        id,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "FAILED",
                        null,
                        null,
                        exception.getMessage()
                ));
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    @PostMapping("/{id}/versions/{versionId}/triggers/{triggerId}/executions")
    public ResponseEntity<Map<String, String>> startTriggerExecution(@PathVariable("id") String id,
                                                                     @PathVariable("versionId") String versionId,
                                                                     @PathVariable("triggerId") String triggerId,
                                                                     @RequestBody(required = false) StartApplicationExecutionRequest request,
                                                                     HttpServletRequest httpRequest) {
        String executionId = applicationService.startTriggerExecution(
                id,
                versionId,
                triggerId,
                initialData(request),
                request != null ? request.startStepId() : null,
                httpRequest
        );
        return ResponseEntity.ok(Map.of("executionId", executionId));
    }

    @PostMapping(value = "/{id}/versions/{versionId}/triggers/{triggerId}/executions/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTriggerExecution(@PathVariable("id") String id,
                                             @PathVariable("versionId") String versionId,
                                             @PathVariable("triggerId") String triggerId,
                                             @RequestBody(required = false) StartApplicationExecutionRequest request,
                                             HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        Map<String, Object> initialData = initialData(request);
        String startStepId = request != null ? request.startStepId() : null;
        applicationExecutionExecutor.execute(() -> {
            ApplicationExecutionListener listener = sseApplicationExecutionListener(emitter, id);
            try {
                applicationService.startTriggerExecution(id, versionId, triggerId, initialData, startStepId, httpRequest, listener);
                emitter.complete();
            } catch (Exception exception) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "RUN_FAILED",
                        id,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "FAILED",
                        null,
                        null,
                        exception.getMessage()
                ));
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    @PatchMapping("/{id}")
    public ApplicationResponse updateApplication(@PathVariable("id") String id,
                                                 @RequestBody UpdateApplicationRequest request,
                                                 HttpServletRequest httpRequest) {
        return applicationService.updateApplication(id, request, httpRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplication(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        applicationService.deleteApplication(id, httpRequest);
    }

    private Map<String, Object> initialData(StartApplicationExecutionRequest request) {
        if (request != null && request.inputData() != null) {
            return request.inputData();
        }
        if (request != null && request.initialData() != null) {
            return request.initialData();
        }
        return Collections.emptyMap();
    }

    private ApplicationExecutionListener sseApplicationExecutionListener(SseEmitter emitter, String applicationId) {
        return new ApplicationExecutionListener() {
            @Override
            public void runStarted(String runId) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "RUN_STARTED",
                        applicationId,
                        runId,
                        null,
                        null,
                        null,
                        null,
                        "RUNNING",
                        null,
                        null,
                        null
                ));
            }

            @Override
            public void stepStarted(String runId, String stepRunId, ApplicationStepDefinition step, Map<String, Object> input) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "STEP_STARTED",
                        applicationId,
                        runId,
                        stepRunId,
                        step.resolvedId(),
                        step.name(),
                        step.type(),
                        "RUNNING",
                        input,
                        null,
                        null
                ));
            }

            @Override
            public void stepCompleted(String runId, String stepRunId, ApplicationStepDefinition step, Object output) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "STEP_COMPLETED",
                        applicationId,
                        runId,
                        stepRunId,
                        step.resolvedId(),
                        step.name(),
                        step.type(),
                        "COMPLETED",
                        null,
                        output,
                        null
                ));
            }

            @Override
            public void stepFailed(String runId, String stepRunId, ApplicationStepDefinition step, String errorMessage) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "STEP_FAILED",
                        applicationId,
                        runId,
                        stepRunId,
                        step.resolvedId(),
                        step.name(),
                        step.type(),
                        "FAILED",
                        null,
                        null,
                        errorMessage
                ));
            }

            @Override
            public void runCompleted(String runId, Map<String, Object> output) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "RUN_COMPLETED",
                        applicationId,
                        runId,
                        null,
                        null,
                        null,
                        null,
                        "COMPLETED",
                        null,
                        output,
                        null
                ));
            }

            @Override
            public void runFailed(String runId, String errorMessage) {
                sendApplicationEvent(emitter, new ApplicationStreamEvent(
                        "RUN_FAILED",
                        applicationId,
                        runId,
                        null,
                        null,
                        null,
                        null,
                        "FAILED",
                        null,
                        null,
                        errorMessage
                ));
            }
        };
    }

    private void sendApplicationEvent(SseEmitter emitter, ApplicationStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name("application").data(event));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to emit application stream event", exception);
        }
    }
}
