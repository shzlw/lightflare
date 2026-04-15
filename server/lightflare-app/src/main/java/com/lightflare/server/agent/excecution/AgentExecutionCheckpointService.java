package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.execution.ExecutionCheckpoint;
import com.lightflare.server.execution.ExecutionCheckpointRepository;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.skill.Skill;
import com.lightflare.server.utils.DateUtils;
import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentExecutionCheckpointService {

    private static final String EXECUTION_TYPE_AGENT_CHAT = "agent_chat";
    private static final String REFERENCE_TYPE_CHAT_SESSION = "chat_session";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    private final ExecutionCheckpointRepository repository;

    public Optional<ExecutionCheckpoint> findResumableChatCheckpoint(String sessionId) {
        return repository.findLatestByReferenceAndStatuses(
                EXECUTION_TYPE_AGENT_CHAT,
                REFERENCE_TYPE_CHAT_SESSION,
                sessionId,
                List.of(STATUS_RUNNING)
        );
    }

    public AgentRunCheckpoint parse(ExecutionCheckpoint checkpoint) {
        return JsonUtils.fromJson(checkpoint.payload(), AgentRunCheckpoint.class);
    }

    public String create(AgentRunContext runContext,
                         ConversationContext conversationContext,
                         Skill selectedSkill,
                         List<LLMPlanResponse.PlanStep> steps,
                         List<String> executionLog) {
        String id = UUID.randomUUID().toString();
        AgentRunCheckpoint payload = new AgentRunCheckpoint();
        payload.setTask(runContext.task());
        payload.setSessionId(runContext.sessionId());
        payload.setUserId(runContext.userId());
        payload.setPromptMemories(conversationContext.promptMemories());
        payload.setSelectedSkillName(selectedSkill != null ? selectedSkill.getName() : null);
        payload.setSelectedSkillInstructions(selectedSkill != null ? selectedSkill.getContent() : null);
        payload.setSteps(steps);
        payload.setExecutionLog(executionLog);

        OffsetDateTime now = DateUtils.now();
        int inserted = repository.insert(
                id,
                id,
                EXECUTION_TYPE_AGENT_CHAT,
                STATUS_RUNNING,
                REFERENCE_TYPE_CHAT_SESSION,
                runContext.sessionId(),
                JsonUtils.toJson(payload),
                now,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one execution_checkpoint row to be inserted but got " + inserted);
        }
        return id;
    }

    public void saveRunning(String checkpointId, AgentRunCheckpoint checkpoint) {
        save(checkpointId, STATUS_RUNNING, checkpoint);
    }

    public void saveCompleted(String checkpointId, AgentRunCheckpoint checkpoint, String finalResponse) {
        checkpoint.setFinalResponse(finalResponse);
        checkpoint.setError(null);
        save(checkpointId, STATUS_COMPLETED, checkpoint);
    }

    public void saveFailed(String checkpointId, AgentRunCheckpoint checkpoint, String error) {
        checkpoint.setError(error);
        save(checkpointId, STATUS_FAILED, checkpoint);
    }

    private void save(String checkpointId, String status, AgentRunCheckpoint checkpoint) {
        boolean updated = repository.update(checkpointId, status, JsonUtils.toJson(checkpoint), DateUtils.now());
        if (!updated) {
            throw new IllegalStateException("Execution checkpoint not found: " + checkpointId);
        }
    }
}
