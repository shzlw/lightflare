package com.lightflare.server.scheduler;

import com.lightflare.server.agent.AgentService;
import com.lightflare.server.chat.ChatSession;
import com.lightflare.server.chat.ChatSessionRepository;
import com.lightflare.server.chat.CreateChatRequest;
import com.lightflare.server.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ScheduledAgentTaskHandler implements ScheduledTaskHandler {

    private static final String TASK_TYPE = "AGENT_PROMPT";

    private final AgentService agentService;
    private final ChatSessionRepository chatSessionRepository;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public void execute(ScheduledTask task) {
        String userId = requiredString(task.getUserId(), "userId");
        String prompt = requiredString(task.getTaskDetails(), "taskDetails");

        String sessionId = UUID.randomUUID().toString();
        OffsetDateTime now = DateUtils.now();
        String title = StringUtils.hasText(task.getTaskName()) ? task.getTaskName().trim() : "Scheduled task";

        int inserted = chatSessionRepository.insertChatSession(
                sessionId,
                title,
                userId,
                0,
                0,
                0,
                ChatSession.STATUS_ACTIVE,
                now,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one chat_session row to be inserted but got " + inserted);
        }

        CreateChatRequest request = new CreateChatRequest();
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setData(prompt);
        agentService.process(request);
    }

    private String requiredString(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Scheduled task is missing required field: " + fieldName);
        }
        return value.trim();
    }
}
