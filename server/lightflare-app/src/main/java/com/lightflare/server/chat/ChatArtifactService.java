package com.lightflare.server.chat;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.project.Project;
import com.lightflare.server.project.ProjectRepository;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatArtifactService {

    private final ChatArtifactRepository chatArtifactRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final AuthService authService;

    public List<ChatArtifactResponse> listArtifacts(String sessionId, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession session = findAccessibleChatSession(sessionId, userContext);
        return chatArtifactRepository.findBySessionId(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChatArtifactResponse createArtifact(String sessionId,
                                               String messageId,
                                               String artifactType,
                                               String title,
                                               String content,
                                               String metadata,
                                               boolean pinned,
                                               int displayOrder,
                                               String createdBy) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Chat session not found: " + sessionId));
        validateMessageBelongsToSession(session.getId(), messageId);
        String id = UUID.randomUUID().toString();
        var now = DateUtils.now();
        int inserted = chatArtifactRepository.insertArtifact(
                id,
                session.getId(),
                normalize(messageId),
                normalizeArtifactType(artifactType),
                normalize(title),
                requireContent(content),
                normalize(metadata),
                pinned,
                displayOrder,
                normalize(createdBy),
                now,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one chat_artifact row to be inserted but got " + inserted);
        }
        return toResponse(chatArtifactRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Chat artifact not found after insert: " + id)));
    }

    @Transactional
    public ChatArtifactResponse createArtifact(String sessionId,
                                               CreateChatArtifactRequest request,
                                               HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession session = findAccessibleChatSession(sessionId, userContext);
        validateMessageBelongsToSession(session.getId(), request != null ? request.getMessageId() : null);

        String id = hasText(request != null ? request.getId() : null)
                ? request.getId().trim()
                : UUID.randomUUID().toString();
        var now = DateUtils.now();
        int inserted = chatArtifactRepository.insertArtifact(
                id,
                session.getId(),
                normalize(request != null ? request.getMessageId() : null),
                normalizeArtifactType(request != null ? request.getArtifactType() : null),
                normalize(request != null ? request.getTitle() : null),
                requireContent(request != null ? request.getContent() : null),
                normalize(request != null ? request.getMetadata() : null),
                request != null && Boolean.TRUE.equals(request.getPinned()),
                request != null && request.getDisplayOrder() != null ? request.getDisplayOrder() : 0,
                userContext.userId(),
                now,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one chat_artifact row to be inserted but got " + inserted);
        }
        return toResponse(findAccessibleArtifact(id, userContext));
    }

    @Transactional
    public ChatArtifactResponse updateArtifact(String id,
                                               UpdateChatArtifactRequest request,
                                               HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatArtifact current = findAccessibleArtifact(id, userContext);
        validateMessageBelongsToSession(
                current.getSessionId(),
                request != null && request.getMessageId() != null ? request.getMessageId() : current.getMessageId()
        );

        int updated = chatArtifactRepository.updateArtifact(
                current.getId(),
                request != null && request.getMessageId() != null ? normalize(request.getMessageId()) : current.getMessageId(),
                request != null && request.getArtifactType() != null
                        ? normalizeArtifactType(request.getArtifactType())
                        : current.getArtifactType(),
                request != null && request.getTitle() != null ? normalize(request.getTitle()) : current.getTitle(),
                request != null && request.getContent() != null ? requireContent(request.getContent()) : current.getContent(),
                request != null && request.getMetadata() != null ? normalize(request.getMetadata()) : current.getMetadata(),
                request != null && request.getPinned() != null ? request.getPinned() : Boolean.TRUE.equals(current.getPinned()),
                request != null && request.getDisplayOrder() != null ? request.getDisplayOrder() : defaultDisplayOrder(current.getDisplayOrder()),
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one chat_artifact row to be updated but got " + updated);
        }
        return toResponse(findAccessibleArtifact(id, userContext));
    }

    @Transactional
    public void deleteArtifact(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        findAccessibleArtifact(id, userContext);
        int deleted = chatArtifactRepository.deleteArtifactById(id);
        if (deleted != 1) {
            throw new IllegalStateException("Expected one chat_artifact row to be deleted but got " + deleted);
        }
    }

    private ChatArtifact findAccessibleArtifact(String id, UserContext userContext) {
        ChatArtifact artifact = chatArtifactRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Chat artifact not found: " + id));
        findAccessibleChatSession(artifact.getSessionId(), userContext);
        return artifact;
    }

    private ChatSession findAccessibleChatSession(String id, UserContext userContext) {
        ChatSession chatSession = chatSessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        if (ChatSession.STATUS_DELETED.equals(chatSession.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), chatSession.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
        Project project = projectRepository.findById(chatSession.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (Project.STATUS_DELETED.equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return chatSession;
    }

    private void validateMessageBelongsToSession(String sessionId, String messageId) {
        String normalizedMessageId = normalize(messageId);
        if (normalizedMessageId == null) {
            return;
        }
        ChatMessage message = chatMessageRepository.findById(normalizedMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message not found"));
        if (!sessionId.equals(message.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageId must belong to the chat session");
        }
    }

    private String normalizeArtifactType(String artifactType) {
        String normalized = normalize(artifactType);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactType is required");
        }
        return normalized.toLowerCase();
    }

    private String requireContent(String content) {
        String normalized = normalize(content);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        return normalized;
    }

    private int defaultDisplayOrder(Integer value) {
        return value != null ? value : 0;
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

    private ChatArtifactResponse toResponse(ChatArtifact artifact) {
        return ChatArtifactResponse.builder()
                .id(artifact.getId())
                .sessionId(artifact.getSessionId())
                .messageId(artifact.getMessageId())
                .artifactType(artifact.getArtifactType())
                .title(artifact.getTitle())
                .content(artifact.getContent())
                .metadata(artifact.getMetadata())
                .pinned(artifact.getPinned())
                .displayOrder(artifact.getDisplayOrder())
                .createdBy(artifact.getCreatedBy())
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .build();
    }
}
