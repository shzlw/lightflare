package com.lightflare.server.project;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects")
public class InternalProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ProjectPageResponse listProjects(@RequestParam(name = "page", defaultValue = "0") int page,
                                            @RequestParam(name = "size", defaultValue = "20") int size,
                                            @RequestParam(name = "q", required = false) String q,
                                            HttpServletRequest httpRequest) {
        return projectService.listProjects(page, size, q, httpRequest);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@RequestBody CreateProjectRequest request, HttpServletRequest httpRequest) {
        return projectService.createProject(request, httpRequest);
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return projectService.getProject(id, httpRequest);
    }

    @PatchMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable("id") String id,
                                         @RequestBody UpdateProjectRequest request,
                                         HttpServletRequest httpRequest) {
        return projectService.updateProject(id, request, httpRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        projectService.deleteProject(id, httpRequest);
    }
}
