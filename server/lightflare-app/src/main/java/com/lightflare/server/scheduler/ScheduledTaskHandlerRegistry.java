package com.lightflare.server.scheduler;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ScheduledTaskHandlerRegistry {

    private final Map<String, ScheduledTaskHandler> handlersByType;

    public ScheduledTaskHandlerRegistry(List<ScheduledTaskHandler> handlers) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(ScheduledTaskHandler::taskType, Function.identity()));
    }

    public Optional<ScheduledTaskHandler> find(String taskType) {
        return Optional.ofNullable(handlersByType.get(taskType));
    }
}
