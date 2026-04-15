package com.lightflare.server.chat;

import com.lightflare.server.chat.ChatSessionService;
import com.lightflare.server.chat.ChatResponseStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/chat-sessions")
public class InternalChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatResponseStreamService chatResponseStreamService;

    @GetMapping
    public ChatSessionPageResponse listChatSessions(@RequestParam(name = "page", defaultValue = "0") int page,
                                                    @RequestParam(name = "size", defaultValue = "20") int size,
                                                    @RequestParam(name = "q", required = false) String q,
                                                    HttpServletRequest httpRequest) {
        return chatSessionService.listChatSessions(page, size, q, httpRequest);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createChatSession(@RequestBody CreateChatSessionRequest request,
                                                 HttpServletRequest httpRequest) {
        return chatSessionService.createChatSession(request, httpRequest);
    }

    @GetMapping("/{sessionId}/messages")
    public ChatMessagePageResponse listChatMessages(@PathVariable("sessionId") String sessionId,
                                                    @RequestParam(name = "limit", required = false) Integer limit,
                                                    @RequestParam(name = "before", required = false) String before,
                                                    HttpServletRequest httpRequest) {
        return chatSessionService.listChatMessages(sessionId, limit, before, httpRequest);
    }

    @PostMapping(value = "/{sessionId}/responses/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssistantResponse(@PathVariable("sessionId") String sessionId,
                                              @RequestBody CreateChatMessageRequest request,
                                              HttpServletRequest httpRequest) {
        return chatResponseStreamService.streamAssistantResponse(sessionId, request, httpRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChatSession(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        chatSessionService.deleteChatSession(id, httpRequest);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveChatSession(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        chatSessionService.archiveChatSession(id, httpRequest);
    }
}
