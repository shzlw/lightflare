package com.lightflare.server.skill;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final AuthService authService;

    public SkillPageResponse listSkills(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = skillRepository.countSkills();
        List<SkillResponse> items = skillRepository.findPage(normalizedSize, (long) normalizedPage * normalizedSize)
                .stream()
                .map(this::toResponse)
                .toList();

        return SkillPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public SkillResponse getSkill(String id) {
        return toResponse(findExistingSkill(id));
    }

    public SkillResponse createSkill(CreateSkillRequest request, HttpServletRequest httpRequest) {
        validate(request.getName(), request.getContent());
        UserContext userContext = authService.requireUserContext(httpRequest);

        Skill skill = new Skill();
        skill.setId(UUID.randomUUID().toString());
        skill.setUserId(userContext.userId());
        skill.setSource(Skill.SOURCE_MANUAL);
        applyUpdates(skill, request.getName(), request.getDescription(), request.getVisibility(), request.getContent(), userContext);
        skill.setCreatedAt(DateUtils.now());
        skill.setUpdatedAt(skill.getCreatedAt());

        int inserted = skillRepository.insertSkill(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getVisibility(),
                skill.getUserId(),
                skill.getSource(),
                skill.getContent(),
                skill.getCreatedAt(),
                skill.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one skill row to be inserted but got " + inserted);
        }
        return toResponse(findExistingSkill(skill.getId()));
    }

    public SkillResponse updateSkill(String id, UpdateSkillRequest request, HttpServletRequest httpRequest) {
        validate(request.getName(), request.getContent());
        UserContext userContext = authService.requireUserContext(httpRequest);

        Skill skill = findExistingSkill(id);
        applyUpdates(skill, request.getName(), request.getDescription(), request.getVisibility(), request.getContent(), userContext);
        skill.setUpdatedAt(DateUtils.now());

        int updated = skillRepository.updateSkill(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getVisibility(),
                skill.getUserId(),
                skill.getSource(),
                skill.getContent(),
                skill.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one skill row to be updated but got " + updated);
        }
        return toResponse(findExistingSkill(skill.getId()));
    }

    public void deleteSkill(String id) {
        Skill skill = findExistingSkill(id);
        int deleted = skillRepository.deleteSkillById(skill.getId());
        if (deleted != 1) {
            throw new IllegalStateException("Expected one skill row to be deleted but got " + deleted);
        }
    }

    private Skill findExistingSkill(String id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Skill not found: " + id));
    }

    private void validate(String name, String content) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Skill name must not be empty");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Skill content must not be empty");
        }
    }

    private void applyUpdates(Skill skill,
                              String name,
                              String description,
                              String visibility,
                              String content,
                              UserContext userContext) {
        skill.setName(name.trim());
        skill.setDescription(description);
        skill.setVisibility(resolveVisibility(visibility, userContext));
        skill.setContent(content);
    }

    private String resolveVisibility(String visibility, UserContext userContext) {
        String normalizedVisibility = normalize(visibility);
        if (!StringUtils.hasText(normalizedVisibility)) {
            return Skill.VISIBILITY_PRIVATE;
        }

        String upperVisibility = normalizedVisibility.toUpperCase();
        if (Skill.VISIBILITY_PRIVATE.equals(upperVisibility)) {
            return upperVisibility;
        }
        if (Skill.VISIBILITY_PUBLIC.equals(upperVisibility)) {
            if (!isAdmin(userContext)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins may create or update public skills");
            }
            return upperVisibility;
        }
        throw new IllegalArgumentException("Skill visibility must be one of: PRIVATE, PUBLIC");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private SkillResponse toResponse(Skill skill) {
        return SkillResponse.builder()
                .id(skill.getId())
                .name(skill.getName())
                .description(skill.getDescription())
                .visibility(skill.getVisibility())
                .userId(skill.getUserId())
                .source(skill.getSource())
                .content(skill.getContent())
                .createdAt(skill.getCreatedAt())
                .updatedAt(skill.getUpdatedAt())
                .build();
    }
}
