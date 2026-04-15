package com.lightflare.server.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/scheduled-tasks")
public class InternalScheduledTaskController {

    private final ScheduledTaskAdminService scheduledTaskAdminService;

    @GetMapping
    public ScheduledTaskPageResponse listScheduledTasks(@RequestParam(name = "page", defaultValue = "0") int page,
                                                        @RequestParam(name = "size", defaultValue = "20") int size,
                                                        @RequestParam(name = "q", required = false) String query) {
        return scheduledTaskAdminService.listScheduledTasks(page, size, query);
    }

    @GetMapping("/{id}")
    public ScheduledTaskResponse getScheduledTask(@PathVariable("id") String id) {
        return scheduledTaskAdminService.getScheduledTask(id);
    }

    @PatchMapping("/{id}/enabled")
    public ScheduledTaskResponse updateScheduledTaskEnabled(@PathVariable("id") String id,
                                                            @RequestBody UpdateScheduledTaskEnabledRequest request) {
        return scheduledTaskAdminService.updateScheduledTaskEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScheduledTask(@PathVariable("id") String id) {
        scheduledTaskAdminService.deleteScheduledTask(id);
    }
}
