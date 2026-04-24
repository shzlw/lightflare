package com.lightflare.server.chat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1")
public class InternalChatArtifactController {

    private final ChatArtifactService chatArtifactService;

    @GetMapping("/chat-sessions/{sessionId}/artifacts")
    public List<ChatArtifactResponse> listArtifacts(@PathVariable("sessionId") String sessionId,
                                                    HttpServletRequest httpRequest) {
        return chatArtifactService.listArtifacts(sessionId, httpRequest);
    }

    @PostMapping("/chat-sessions/{sessionId}/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatArtifactResponse createArtifact(@PathVariable("sessionId") String sessionId,
                                               @RequestBody CreateChatArtifactRequest request,
                                               HttpServletRequest httpRequest) {
        return chatArtifactService.createArtifact(sessionId, request, httpRequest);
    }

    @PatchMapping("/chat-artifacts/{id}")
    public ChatArtifactResponse updateArtifact(@PathVariable("id") String id,
                                               @RequestBody UpdateChatArtifactRequest request,
                                               HttpServletRequest httpRequest) {
        return chatArtifactService.updateArtifact(id, request, httpRequest);
    }

    @DeleteMapping("/chat-artifacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArtifact(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        chatArtifactService.deleteArtifact(id, httpRequest);
    }
}
