package com.lightflare.server.scheduler;

import com.lightflare.server.utils.DateUtils;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScheduledTaskAdminService {

    private final ScheduledTaskRepository scheduledTaskRepository;

    public ScheduledTaskPageResponse listScheduledTasks(int page, int size, String query) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedQuery = normalize(query);

        long totalItems = scheduledTaskRepository.countScheduledTasks(normalizedQuery);
        List<ScheduledTaskResponse> items = scheduledTaskRepository.findPage(
                        normalizedQuery,
                        normalizedSize,
                        (long) normalizedPage * normalizedSize
                ).stream()
                .map(this::toResponse)
                .toList();

        return ScheduledTaskPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public ScheduledTaskResponse getScheduledTask(String id) {
        return scheduledTaskRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Scheduled task not found: " + id));
    }

    public void deleteScheduledTask(String id) {
        int deleted = scheduledTaskRepository.deleteScheduledTaskById(id);
        if (deleted != 1) {
            throw new NoSuchElementException("Scheduled task not found: " + id);
        }
    }

    public ScheduledTaskResponse updateScheduledTaskEnabled(String id, boolean enabled) {
        boolean updated = scheduledTaskRepository.updateEnabled(id, enabled, DateUtils.now());
        if (!updated) {
            throw new NoSuchElementException("Scheduled task not found: " + id);
        }
        return getScheduledTask(id);
    }

    private ScheduledTaskResponse toResponse(ScheduledTask task) {
        return ScheduledTaskResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .taskDetails(task.getTaskDetails())
                .enabled(task.isEnabled())
                .cronExpression(task.getCronExpression())
                .nextRunAt(task.getNextRunAt())
                .lastStartedAt(task.getLastStartedAt())
                .lastCompletedAt(task.getLastCompletedAt())
                .lastSuccessAt(task.getLastSuccessAt())
                .lastFailureAt(task.getLastFailureAt())
                .lastError(task.getLastError())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
