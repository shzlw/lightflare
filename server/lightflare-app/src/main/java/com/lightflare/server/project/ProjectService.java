package com.lightflare.server.project;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AuthService authService;

    public ProjectPageResponse listProjects(int page, int size, String query, HttpServletRequest httpRequest) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedQuery = normalize(query);
        UserContext userContext = authService.requireUserContext(httpRequest);
        long totalItems = projectRepository.countProjects(
                normalizedQuery,
                userContext.userId(),
                isAdmin(userContext)
        );
        return ProjectPageResponse.builder()
                .items(projectRepository.findPage(
                                normalizedQuery,
                                userContext.userId(),
                                isAdmin(userContext),
                                normalizedSize,
                                (long) normalizedPage * normalizedSize
                        ).stream()
                        .map(this::toResponse)
                        .toList())
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public ProjectResponse createProject(CreateProjectRequest request, HttpServletRequest httpRequest) {
        Project project = new Project();
        project.setId(hasText(request != null ? request.getId() : null) ? request.getId().trim() : UUID.randomUUID().toString());
        project.setTitle(normalize(request != null ? request.getTitle() : null));
        project.setDescription(normalize(request != null ? request.getDescription() : null));
        project.setUserId(resolveUserId(request != null ? request.getUserId() : null, httpRequest));
        project.setStatus(Project.STATUS_ACTIVE);
        project.setCreatedAt(DateUtils.now());
        project.setUpdatedAt(project.getCreatedAt());

        int inserted = projectRepository.insertProject(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getUserId(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one project row to be inserted but got " + inserted);
        }

        return toResponse(findAccessibleProject(project.getId(), authService.requireUserContext(httpRequest)));
    }

    public ProjectResponse getProject(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        return toResponse(findAccessibleProject(id, userContext));
    }

    public ProjectResponse updateProject(String id, UpdateProjectRequest request, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Project current = findAccessibleProject(id, userContext);
        String nextStatus = normalizeStatus(request != null ? request.getStatus() : null, current.getStatus());
        int updated = projectRepository.updateProject(
                current.getId(),
                request != null && request.getTitle() != null ? normalize(request.getTitle()) : current.getTitle(),
                request != null && request.getDescription() != null ? normalize(request.getDescription()) : current.getDescription(),
                nextStatus,
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one project row to be updated but got " + updated);
        }
        return toResponse(findAccessibleProject(id, userContext));
    }

    public void deleteProject(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        findAccessibleProject(id, userContext);
        int deleted = projectRepository.deleteProjectById(id);
        if (deleted != 1) {
            throw new IllegalStateException("Expected one project row to be deleted but got " + deleted);
        }
    }

    private Project findExistingProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + id));
    }

    private Project findAccessibleProject(String id, UserContext userContext) {
        Project project = findExistingProject(id);
        if (Project.STATUS_DELETED.equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), project.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private String resolveUserId(String requestedUserId, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        if (!isAdmin(userContext)) {
            if (hasText(requestedUserId) && !requestedUserId.trim().equals(userContext.userId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create projects for another user");
            }
            return userContext.userId();
        }
        return hasText(requestedUserId) ? requestedUserId.trim() : userContext.userId();
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String normalizeStatus(String requestedStatus, String currentStatus) {
        if (!hasText(requestedStatus)) {
            return currentStatus;
        }
        String normalizedStatus = requestedStatus.trim().toLowerCase();
        if (!Project.STATUS_ACTIVE.equals(normalizedStatus)
                && !Project.STATUS_ARCHIVED.equals(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project status must be active or archived");
        }
        return normalizedStatus;
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .userId(project.getUserId())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
