package com.lightflare.server.scheduler;

import com.lightflare.server.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduledTaskRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public List<ScheduledTask> leaseDueTasks(OffsetDateTime now,
                                             OffsetDateTime leaseUntil,
                                             String leaseOwner,
                                             int batchSize) {
        String sql = """
                WITH due_tasks AS (
                    SELECT id
                    FROM scheduled_task
                    WHERE enabled = TRUE
                      AND next_run_at <= :now
                      AND (lease_until IS NULL OR lease_until < :now)
                    ORDER BY next_run_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE scheduled_task scheduled
                SET lease_owner = :leaseOwner,
                    lease_until = :leaseUntil,
                    last_started_at = :now,
                    updated_at = :now
                FROM due_tasks
                WHERE scheduled.id = due_tasks.id
                RETURNING scheduled.*
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("leaseUntil", leaseUntil)
                .addValue("leaseOwner", leaseOwner)
                .addValue("batchSize", batchSize);
        return jdbcTemplate.query(sql, params, ScheduledTaskRepository::mapRow);
    }

    public int insertScheduledTask(String id,
                                   String userId,
                                   String taskName,
                                   String taskType,
                                   String taskDetails,
                                   boolean enabled,
                                   String cronExpression,
                                   OffsetDateTime nextRunAt,
                                   OffsetDateTime createdAt,
                                   OffsetDateTime updatedAt) {
        String sql = """
                INSERT INTO scheduled_task (
                    id,
                    user_id,
                    task_name,
                    task_type,
                    task_details,
                    enabled,
                    cron_expression,
                    next_run_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :userId,
                    :taskName,
                    :taskType,
                    :taskDetails,
                    :enabled,
                    :cronExpression,
                    :nextRunAt,
                    :createdAt,
                    :updatedAt
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("taskName", taskName)
                .addValue("taskType", taskType)
                .addValue("taskDetails", taskDetails)
                .addValue("enabled", enabled)
                .addValue("cronExpression", cronExpression)
                .addValue("nextRunAt", nextRunAt)
                .addValue("createdAt", createdAt)
                .addValue("updatedAt", updatedAt);
        return jdbcTemplate.update(sql, params);
    }

    public int deleteScheduledTaskById(String id) {
        String sql = """
                DELETE FROM scheduled_task
                WHERE id = :id
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("id", id));
    }

    public List<ScheduledTask> findScheduledTasks(int limit) {
        String sql = """
                SELECT *
                FROM scheduled_task
                ORDER BY next_run_at ASC, created_at ASC, id ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("limit", limit),
                ScheduledTaskRepository::mapRow
        );
    }

    public List<ScheduledTask> findPage(String query, int limit, long offset) {
        String sql = """
                SELECT *
                FROM scheduled_task
                WHERE (:query IS NULL
                       OR task_name ILIKE '%' || :query || '%')
                ORDER BY next_run_at ASC NULLS LAST, created_at DESC NULLS LAST, id DESC
                LIMIT :limit OFFSET :offset
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("query", query, Types.VARCHAR)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                ScheduledTaskRepository::mapRow
        );
    }

    public long countScheduledTasks(String query) {
        String sql = """
                SELECT COUNT(*)
                FROM scheduled_task
                WHERE (:query IS NULL
                       OR task_name ILIKE '%' || :query || '%')
                """;
        Long count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource().addValue("query", query, Types.VARCHAR),
                Long.class
        );
        return count != null ? count : 0L;
    }

    public Optional<ScheduledTask> findById(String id) {
        String sql = """
                SELECT *
                FROM scheduled_task
                WHERE id = :id
                """;
        List<ScheduledTask> tasks = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("id", id),
                ScheduledTaskRepository::mapRow
        );
        return tasks.stream().findFirst();
    }

    public boolean updateEnabled(String id, boolean enabled, OffsetDateTime updatedAt) {
        String sql = """
                UPDATE scheduled_task
                SET enabled = :enabled,
                    updated_at = :updatedAt
                WHERE id = :id
                """;
        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("enabled", enabled)
                        .addValue("updatedAt", updatedAt)
        ) == 1;
    }

    public boolean markSuccess(String taskId,
                               String leaseOwner,
                               OffsetDateTime completedAt,
                               OffsetDateTime nextRunAt) {
        String sql = """
                UPDATE scheduled_task
                SET next_run_at = :nextRunAt,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_completed_at = :completedAt,
                    last_success_at = :completedAt,
                    last_error = NULL,
                    updated_at = :completedAt
                WHERE id = :taskId
                  AND lease_owner = :leaseOwner
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("leaseOwner", leaseOwner)
                .addValue("completedAt", completedAt)
                .addValue("nextRunAt", nextRunAt);
        return jdbcTemplate.update(sql, params) == 1;
    }

    public boolean markFailure(String taskId,
                               String leaseOwner,
                               OffsetDateTime completedAt,
                               OffsetDateTime nextRunAt,
                               String errorMessage) {
        String sql = """
                UPDATE scheduled_task
                SET next_run_at = :nextRunAt,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_completed_at = :completedAt,
                    last_failure_at = :completedAt,
                    last_error = :errorMessage,
                    updated_at = :completedAt
                WHERE id = :taskId
                  AND lease_owner = :leaseOwner
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("leaseOwner", leaseOwner)
                .addValue("completedAt", completedAt)
                .addValue("nextRunAt", nextRunAt)
                .addValue("errorMessage", errorMessage);
        return jdbcTemplate.update(sql, params) == 1;
    }

    private static ScheduledTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        ScheduledTask task = new ScheduledTask();
        task.setId(rs.getString("id"));
        task.setUserId(rs.getString("user_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setTaskType(rs.getString("task_type"));
        task.setTaskDetails(rs.getString("task_details"));
        task.setEnabled(rs.getBoolean("enabled"));
        task.setCronExpression(rs.getString("cron_expression"));
        task.setNextRunAt(rs.getObject("next_run_at", OffsetDateTime.class));
        task.setLastStartedAt(rs.getObject("last_started_at", OffsetDateTime.class));
        task.setLastCompletedAt(rs.getObject("last_completed_at", OffsetDateTime.class));
        task.setLastSuccessAt(rs.getObject("last_success_at", OffsetDateTime.class));
        task.setLastFailureAt(rs.getObject("last_failure_at", OffsetDateTime.class));
        task.setLastError(rs.getString("last_error"));
        task.setLeaseOwner(rs.getString("lease_owner"));
        task.setLeaseUntil(rs.getObject("lease_until", OffsetDateTime.class));
        task.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        task.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return task;
    }
}
