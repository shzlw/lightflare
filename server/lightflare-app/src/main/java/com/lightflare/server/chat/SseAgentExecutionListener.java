package com.lightflare.server.chat;

import com.lightflare.server.agent.excecution.AgentExecutionListener;
import com.lightflare.server.chat.events.ChatStreamEvent;
import com.lightflare.server.chat.events.ChatStreamEventType;
import com.lightflare.server.chat.events.ChatStreamFinalResponseEvent;
import com.lightflare.server.chat.events.ChatStreamPlanCreatedEvent;
import com.lightflare.server.chat.events.ChatStreamStepCompletedEvent;
import com.lightflare.server.chat.events.ChatStreamStepProgressEvent;
import com.lightflare.server.chat.events.ChatStreamStepStartedEvent;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseAgentExecutionListener implements AgentExecutionListener {

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SseAgentExecutionListener(SseEmitter emitter) {
        this.emitter = emitter;
        this.emitter.onCompletion(() -> closed.set(true));
        this.emitter.onTimeout(() -> closed.set(true));
        this.emitter.onError(error -> closed.set(true));
    }

    @Override
    public void onPlanCreated(String executionId,
                              String thoughtProcess,
                              String selectedSkill,
                              List<LLMPlanResponse.PlanStep> steps) {
        send(ChatStreamEvent.builder()
                .type(ChatStreamEventType.PLAN_CREATED)
                .executionId(executionId)
                .payload(ChatStreamPlanCreatedEvent.builder()
                        .executionId(executionId)
                        .thoughtProcess(thoughtProcess)
                        .selectedSkill(selectedSkill)
                        .steps(steps)
                        .build())
                .build());
    }

    @Override
    public void onStepStarted(String executionId, LLMPlanResponse.PlanStep step) {
        send(ChatStreamEvent.builder()
                .type(ChatStreamEventType.STEP_STARTED)
                .executionId(executionId)
                .payload(ChatStreamStepStartedEvent.builder()
                        .executionId(executionId)
                        .step(step)
                        .build())
                .build());
    }

    @Override
    public void onStepProgress(String executionId,
                               LLMPlanResponse.PlanStep step,
                               String progressType,
                               String message) {
        send(ChatStreamEvent.builder()
                .type(ChatStreamEventType.STEP_PROGRESS)
                .executionId(executionId)
                .payload(ChatStreamStepProgressEvent.builder()
                        .executionId(executionId)
                        .step(step)
                        .progressType(progressType)
                        .message(message)
                        .build())
                .build());
    }

    @Override
    public void onStepCompleted(String executionId,
                                LLMPlanResponse.PlanStep step,
                                String status,
                                String terminalResponse,
                                List<String> executionLogEntries) {
        send(ChatStreamEvent.builder()
                .type(ChatStreamEventType.STEP_COMPLETED)
                .executionId(executionId)
                .payload(ChatStreamStepCompletedEvent.builder()
                        .executionId(executionId)
                        .step(step)
                        .status(status)
                        .terminalResponse(terminalResponse)
                        .executionLogEntries(executionLogEntries)
                        .build())
                .build());
    }

    @Override
    public void onFinalResponse(String executionId, String response) {
        send(ChatStreamEvent.builder()
                .type(ChatStreamEventType.FINAL_RESPONSE)
                .executionId(executionId)
                .payload(ChatStreamFinalResponseEvent.builder()
                        .executionId(executionId)
                        .content(response)
                        .build())
                .build());
    }

    private void send(ChatStreamEvent event) {
        if (closed.get()) {
            return;
        }

        synchronized (emitter) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("chat_event").data(event));
            } catch (IOException exception) {
                if (closed.compareAndSet(false, true)) {
                    log.debug("Closing SSE listener after send failure for event {}", event.type(), exception);
                }
            }
        }
    }
}
