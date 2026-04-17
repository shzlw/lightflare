package com.lightflare.server.workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class WorkflowSchedulerTriggerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public List<WorkflowTrigger> leaseDueSchedulerTriggers(OffsetDateTime now,
                                                           OffsetDateTime leaseUntil,
                                                           String leaseOwner,
                                                           int batchSize) {
        String sql = """
                WITH due_triggers AS (
                    SELECT id
                    FROM workflow_trigger
                    WHERE trigger_type = 'scheduler'
                      AND enabled = TRUE
                      AND (
                          config_json::jsonb ->> 'nextRunAt' IS NULL
                          OR (config_json::jsonb ->> 'nextRunAt')::timestamptz <= :now
                      )
                      AND (
                          config_json::jsonb ->> 'leaseUntil' IS NULL
                          OR (config_json::jsonb ->> 'leaseUntil')::timestamptz < :now
                      )
                    ORDER BY (config_json::jsonb ->> 'nextRunAt')::timestamptz ASC NULLS FIRST,
                             created_at ASC,
                             id ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE workflow_trigger wt
                SET config_json = jsonb_set(
                        jsonb_set(
                            jsonb_set(wt.config_json::jsonb, '{leaseOwner}', to_jsonb(:leaseOwner::text), true),
                            '{leaseUntil}',
                            to_jsonb(:leaseUntil::text),
                            true
                        ),
                        '{lastStartedAt}',
                        to_jsonb(:nowText::text),
                        true
                    )::text,
                    updated_at = :now
                FROM due_triggers
                WHERE wt.id = due_triggers.id
                RETURNING wt.*
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("nowText", now.toString())
                .addValue("leaseUntil", leaseUntil.toString())
                .addValue("leaseOwner", leaseOwner)
                .addValue("batchSize", batchSize);
        return jdbcTemplate.query(sql, params, WorkflowSchedulerTriggerRepository::mapRow);
    }

    public boolean updateLeasedConfig(String triggerId,
                                      String leaseOwner,
                                      String configJson,
                                      OffsetDateTime updatedAt) {
        String sql = """
                UPDATE workflow_trigger
                SET config_json = :configJson,
                    updated_at = :updatedAt
                WHERE id = :triggerId
                  AND config_json::jsonb ->> 'leaseOwner' = :leaseOwner
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("triggerId", triggerId)
                .addValue("leaseOwner", leaseOwner)
                .addValue("configJson", configJson)
                .addValue("updatedAt", updatedAt);
        return jdbcTemplate.update(sql, params) == 1;
    }

    private static WorkflowTrigger mapRow(ResultSet rs, int rowNum) throws SQLException {
        WorkflowTrigger trigger = new WorkflowTrigger();
        trigger.setId(rs.getString("id"));
        trigger.setWorkflowId(rs.getString("workflow_id"));
        trigger.setTriggerType(rs.getString("trigger_type"));
        trigger.setName(rs.getString("name"));
        trigger.setEnabled(rs.getBoolean("enabled"));
        trigger.setConfigJson(rs.getString("config_json"));
        trigger.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        trigger.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return trigger;
    }
}
