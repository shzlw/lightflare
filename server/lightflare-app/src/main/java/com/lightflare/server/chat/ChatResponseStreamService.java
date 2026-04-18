package com.lightflare.server.chat;

import com.lightflare.server.agent.AgentService;
import com.lightflare.server.agent.excecution.AgentExecutionListener;
import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.chat.events.ChatStreamEvent;
import com.lightflare.server.chat.events.ChatStreamEventType;
import com.lightflare.server.chat.events.ChatStreamMessageCompleteEvent;
import com.lightflare.server.chat.events.ChatStreamMessageErrorEvent;
import com.lightflare.server.chat.events.ChatStreamMessageStartEvent;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatResponseStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 0L;
    private static final String ASSISTANT_SOURCE = "llm";

    private final AgentService agentService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AuthService authService;

    @Qualifier("chatStreamExecutor")
    private final Executor streamExecutor;

    public SseEmitter streamAssistantResponse(String sessionId, CreateChatMessageRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        ChatSession chatSession = findAccessibleChatSession(sessionId, userContext);
        validateStreamRequest(request);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CreateChatRequest chatRequest = new CreateChatRequest();
        chatRequest.setSessionId(chatSession.getId());
        chatRequest.setUserId(chatSession.getUserId());
        chatRequest.setData(request.getContent().trim());
        streamExecutor.execute(() -> streamAssistantResponseInternal(chatSession, chatRequest, emitter));
        return emitter;
    }

    private void streamAssistantResponseInternal(ChatSession chatSession,
                                                 CreateChatRequest chatRequest,
                                                 SseEmitter emitter) {
        String assistantMessageId = UUID.randomUUID().toString();
        AgentExecutionListener listener = new SseAgentExecutionListener(emitter);

        try {
            sendEvent(emitter, ChatStreamEvent.builder()
                    .type(ChatStreamEventType.MESSAGE_START)
                    .executionId(chatSession.getId())
                    .payload(ChatStreamMessageStartEvent.builder()
                            .messageId(assistantMessageId)
                            .executionId(chatSession.getId())
                            .source(ASSISTANT_SOURCE)
                            .build())
                    .build());

            String assistantResponse = agentService.process(chatRequest, listener);

            ChatMessage assistantMessage = findLatestAssistantMessage(chatSession.getId(), assistantResponse);
            sendEvent(emitter, ChatStreamEvent.builder()
                    .type(ChatStreamEventType.MESSAGE_COMPLETE)
                    .executionId(chatSession.getId())
                    .payload(ChatStreamMessageCompleteEvent.builder()
                            .messageId(assistantMessage != null ? assistantMessage.getId() : assistantMessageId)
                            .executionId(chatSession.getId())
                            .source(ASSISTANT_SOURCE)
                            .content(assistantMessage != null ? assistantMessage.getContent() : assistantResponse)
                            .createdAt(assistantMessage != null ? assistantMessage.getCreatedAt() : null)
                            .build())
                    .build());
            emitter.complete();
        } catch (Exception exception) {
            log.error("Failed to stream assistant response for sessionId={}", chatSession.getId(), exception);
            try {
                sendEvent(emitter, ChatStreamEvent.builder()
                        .type(ChatStreamEventType.MESSAGE_ERROR)
                        .executionId(chatSession.getId())
                        .payload(ChatStreamMessageErrorEvent.builder()
                                .executionId(chatSession.getId())
                                .message(exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName())
                                .build())
                        .build());
            } catch (Exception sendException) {
                log.debug("Failed to send stream error event for sessionId={}", chatSession.getId(), sendException);
            }
            emitter.completeWithError(exception);
        }
    }

    private ChatSession findExistingChatSession(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Chat session not found: " + sessionId));
    }

    private ChatSession findAccessibleChatSession(String sessionId, UserContext userContext) {
        ChatSession chatSession = findExistingChatSession(sessionId);
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), chatSession.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
        return chatSession;
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private void validateStreamRequest(CreateChatMessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Chat message request must not be null");
        }
        if (!"user".equals(request.getSource())) {
            throw new IllegalArgumentException("Streaming chat responses require source=user");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("Chat message content must not be empty");
        }
    }

    private ChatMessage findLatestAssistantMessage(String sessionId, String assistantResponse) {
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestPageBySessionId(sessionId, 10);
        return latestMessages.stream()
                .filter(message -> ASSISTANT_SOURCE.equals(message.getSource()))
                .filter(message -> assistantResponse == null || assistantResponse.equals(message.getContent()))
                .findFirst()
                .orElseGet(() -> latestMessages.stream()
                        .filter(message -> ASSISTANT_SOURCE.equals(message.getSource()))
                        .findFirst()
                        .orElse(null));
    }

    private void sendEvent(SseEmitter emitter, ChatStreamEvent payload) throws IOException {
        emitter.send(SseEmitter.event().name("chat_event").data(payload));
    }
}
