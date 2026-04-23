package com.lightflare.server.chat;

import com.lightflare.server.agent.AgentService;
import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.project.Project;
import com.lightflare.server.project.ProjectRepository;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
public class ChatSessionService {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final AuthService authService;

    public ChatSessionPageResponse listChatSessions(int page,
                                                    int size,
                                                    String query,
                                                    String projectId,
                                                    HttpServletRequest httpRequest) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedQuery = normalize(query);
        String normalizedProjectId = normalize(projectId);
        UserContext userContext = authService.requireUserContext(httpRequest);
        if (normalizedProjectId != null) {
            findAccessibleProject(normalizedProjectId, userContext);
        }
        long totalItems = chatSessionRepository.countChatSessions(
                normalizedQuery,
                normalizedProjectId,
                userContext.userId(),
                isAdmin(userContext)
        );
        List<ChatSessionResponse> items = chatSessionRepository.findPage(
                        normalizedQuery,
                        normalizedProjectId,
                        userContext.userId(),
                        isAdmin(userContext),
                        normalizedSize,
                        (long) normalizedPage * normalizedSize
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return ChatSessionPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public ChatSessionResponse createChatSession(CreateChatSessionRequest request, HttpServletRequest httpRequest) {
        if (request == null || !StringUtils.hasText(request.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat sessions require a projectId");
        }
        UserContext userContext = authService.requireUserContext(httpRequest);
        Project project = findAccessibleProject(request.getProjectId().trim(), userContext);
        ChatSession chatSession = new ChatSession();
        chatSession.setId(StringUtils.hasText(request.getId()) ? request.getId().trim() : UUID.randomUUID().toString());
        chatSession.setProjectId(project.getId());
        chatSession.setTitle(normalize(request.getTitle()));
        chatSession.setUserId(resolveUserId(request.getUserId(), project, userContext));
        chatSession.setTotalTokens(0);
        chatSession.setTotalInputTokens(0);
        chatSession.setTotalOutputTokens(0);
        chatSession.setStatus(ChatSession.STATUS_ACTIVE);
        chatSession.setCreatedAt(DateUtils.now());
        chatSession.setUpdatedAt(chatSession.getCreatedAt());

        int inserted = chatSessionRepository.insertChatSession(
                chatSession.getId(),
                chatSession.getProjectId(),
                chatSession.getTitle(),
                chatSession.getUserId(),
                chatSession.getTotalTokens(),
                chatSession.getTotalInputTokens(),
                chatSession.getTotalOutputTokens(),
                chatSession.getStatus(),
                chatSession.getCreatedAt(),
                chatSession.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one chat_session row to be inserted but got " + inserted);
        }

        return toResponse(findAccessibleChatSession(chatSession.getId(), userContext));
    }

    public ChatMessagePageResponse listChatMessages(String sessionId, Integer limit, String before, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        findAccessibleChatSession(sessionId, userContext);

        int normalizedLimit = normalizeMessagePageSize(limit);
        List<ChatMessage> messagesDescending = loadMessagesDescending(sessionId, normalizedLimit + 1, before);
        boolean hasMore = messagesDescending.size() > normalizedLimit;
        if (hasMore) {
            messagesDescending = new ArrayList<>(messagesDescending.subList(0, normalizedLimit));
        }

        List<ChatMessage> messagesAscending = new ArrayList<>(messagesDescending);
        Collections.reverse(messagesAscending);
        String nextBefore = hasMore && !messagesAscending.isEmpty()
                ? toCursor(messagesAscending.getFirst())
                : null;

        return ChatMessagePageResponse.builder()
                .items(messagesAscending.stream().map(this::toResponse).toList())
                .nextBefore(nextBefore)
                .hasMore(hasMore)
                .build();
    }

    public void archiveChatSession(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession session = findAccessibleChatSession(id, userContext);
        if (!ChatSession.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new IllegalStateException("Only active chat sessions can be archived");
        }
        int updated = chatSessionRepository.archiveChatSessionById(id);
        if (updated != 1) {
            throw new IllegalStateException("Expected one chat_session row to be archived but got " + updated);
        }
    }

    public void deleteChatSession(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession session = findAccessibleChatSession(id, userContext);
        if (!ChatSession.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new IllegalStateException("Only active chat sessions can be deleted");
        }
        int deleted = chatSessionRepository.deleteChatSessionById(id);
        if (deleted != 1) {
            throw new IllegalStateException("Expected one chat_session row to be deleted but got " + deleted);
        }
    }

    public ChatSessionResponse updateChatSession(String id, UpdateChatSessionRequest request, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession session = findAccessibleChatSession(id, userContext);
        if (!ChatSession.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new IllegalStateException("Only active chat sessions can be updated");
        }
        int updated = chatSessionRepository.updateChatSessionTitle(
                id,
                normalize(request != null ? request.getTitle() : null),
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one chat_session row to be updated but got " + updated);
        }
        return toResponse(findAccessibleChatSession(id, userContext));
    }

    private ChatSession findExistingChatSession(String id) {
        return chatSessionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Chat session not found: " + id));
    }

    private ChatSession findAccessibleChatSession(String id, UserContext userContext) {
        ChatSession chatSession = findExistingChatSession(id);
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), chatSession.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
        return chatSession;
    }

    private List<ChatMessage> loadMessagesDescending(String sessionId, int limit, String before) {
        if (!StringUtils.hasText(before)) {
            return chatMessageRepository.findLatestPageBySessionId(sessionId, limit);
        }

        MessageCursor cursor = parseCursor(before);
        return chatMessageRepository.findPageBySessionIdBeforeCursor(
                sessionId,
                cursor.createdAt(),
                cursor.id(),
                limit
        );
    }

    private int normalizeMessagePageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.max(1, Math.min(limit, MAX_MESSAGE_PAGE_SIZE));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveUserId(String requestedUserId, Project project, UserContext userContext) {
        if (!isAdmin(userContext)) {
            if (StringUtils.hasText(requestedUserId) && !requestedUserId.trim().equals(userContext.userId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create chat sessions for another user");
            }
            return project.getUserId();
        }
        if (StringUtils.hasText(requestedUserId)) {
            String normalizedUserId = requestedUserId.trim();
            if (StringUtils.hasText(project.getUserId()) && !project.getUserId().equals(normalizedUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session userId must match project owner");
            }
            return normalizedUserId;
        }
        return project.getUserId();
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private Project findAccessibleProject(String id, UserContext userContext) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (Project.STATUS_DELETED.equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), project.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private MessageCursor parseCursor(String value) {
        int separatorIndex = value.lastIndexOf('|');
        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            throw new IllegalArgumentException("Invalid before cursor");
        }

        try {
            OffsetDateTime createdAt = OffsetDateTime.parse(value.substring(0, separatorIndex));
            String id = value.substring(separatorIndex + 1);
            if (!StringUtils.hasText(id)) {
                throw new IllegalArgumentException("Invalid before cursor");
            }
            return new MessageCursor(createdAt, id);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid before cursor", exception);
        }
    }

    private String toCursor(ChatMessage chatMessage) {
        return chatMessage.getCreatedAt() + "|" + chatMessage.getId();
    }

    private ChatMessageResponse toResponse(ChatMessage chatMessage) {
        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .sessionId(chatMessage.getSessionId())
                .source(chatMessage.getSource())
                .content(chatMessage.getContent())
                .createdAt(chatMessage.getCreatedAt())
                .build();
    }

    private ChatSessionResponse toResponse(ChatSession chatSession) {
        return ChatSessionResponse.builder()
                .id(chatSession.getId())
                .projectId(chatSession.getProjectId())
                .title(chatSession.getTitle())
                .userId(chatSession.getUserId())
                .totalTokens(chatSession.getTotalTokens())
                .totalInputTokens(chatSession.getTotalInputTokens())
                .totalOutputTokens(chatSession.getTotalOutputTokens())
                .status(chatSession.getStatus())
                .createdAt(chatSession.getCreatedAt())
                .updatedAt(chatSession.getUpdatedAt())
                .build();
    }

    private record MessageCursor(OffsetDateTime createdAt, String id) {
    }
}
