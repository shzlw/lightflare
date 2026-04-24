package com.lightflare.server.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/application-executions")
public class InternalApplicationExecutionController {

    private final ApplicationService applicationService;

    @GetMapping("/{id}")
    public ApplicationRunResponse getExecution(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return applicationService.getRun(id, httpRequest);
    }

    @GetMapping("/{id}/steps")
    public List<ApplicationStepRunResponse> getExecutionSteps(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return applicationService.getStepRuns(id, httpRequest);
    }
}
