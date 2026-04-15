package com.lightflare.server.messaging;

import com.lightflare.server.agent.AgentService;
import com.lightflare.server.chat.CreateChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMessagingAppConnector implements MessagingAppConnector {

    private final AgentService agentService;

    @Override
    public String process(MessagingAppConnectorRequest request) {
        CreateChatRequest chatRequest = new CreateChatRequest();
        chatRequest.setSessionId(request.sessionId());
        chatRequest.setUserId(request.userId());
        chatRequest.setData(request.message());
        log.info("Processing messaging request sessionId={}, userId={}",
                request.sessionId(),
                request.userId());
        return agentService.process(chatRequest);
    }
}
