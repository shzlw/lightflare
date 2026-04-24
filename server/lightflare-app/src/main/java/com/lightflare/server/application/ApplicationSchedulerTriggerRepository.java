package com.lightflare.server.application;

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
public class ApplicationSchedulerTriggerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public List<ApplicationTrigger> leaseDueCronTriggers(OffsetDateTime now,
                                                         OffsetDateTime leaseUntil,
                                                         String leaseOwner,
                                                         int batchSize) {
        String sql = """
                WITH due_triggers AS (
                    SELECT at.id
                    FROM application_trigger at
                    JOIN application_version av ON av.id = at.application_version_id
                    JOIN application a ON a.published_version_id = av.id
                    WHERE at.trigger_type = 'cron'
                      AND av.status = 'published'
                      AND (
                          at.config_json::jsonb ->> 'nextRunAt' IS NULL
                          OR (at.config_json::jsonb ->> 'nextRunAt')::timestamptz <= :now
                      )
                      AND (
                          at.config_json::jsonb ->> 'leaseUntil' IS NULL
                          OR (at.config_json::jsonb ->> 'leaseUntil')::timestamptz < :now
                      )
                    ORDER BY (at.config_json::jsonb ->> 'nextRunAt')::timestamptz ASC NULLS FIRST,
                             at.id ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE application_trigger at
                SET config_json = jsonb_set(
                        jsonb_set(
                            jsonb_set(at.config_json::jsonb, '{leaseOwner}', to_jsonb(:leaseOwner::text), true),
                            '{leaseUntil}',
                            to_jsonb(:leaseUntilText::text),
                            true
                        ),
                        '{lastStartedAt}',
                        to_jsonb(:nowText::text),
                        true
                    )::text
                FROM due_triggers
                WHERE at.id = due_triggers.id
                RETURNING at.*
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("nowText", now.toString())
                .addValue("leaseUntilText", leaseUntil.toString())
                .addValue("leaseOwner", leaseOwner)
                .addValue("batchSize", batchSize);
        return jdbcTemplate.query(sql, params, ApplicationSchedulerTriggerRepository::mapRow);
    }

    public boolean updateLeasedConfig(String triggerId, String leaseOwner, String configJson) {
        String sql = """
                UPDATE application_trigger
                SET config_json = :configJson
                WHERE id = :triggerId
                  AND config_json::jsonb ->> 'leaseOwner' = :leaseOwner
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("triggerId", triggerId)
                .addValue("leaseOwner", leaseOwner)
                .addValue("configJson", configJson);
        return jdbcTemplate.update(sql, params) == 1;
    }

    private static ApplicationTrigger mapRow(ResultSet rs, int rowNum) throws SQLException {
        ApplicationTrigger trigger = new ApplicationTrigger();
        trigger.setId(rs.getString("id"));
        trigger.setApplicationVersionId(rs.getString("application_version_id"));
        trigger.setTriggerType(rs.getString("trigger_type"));
        trigger.setStartStepId(rs.getString("start_step_id"));
        trigger.setConfigJson(rs.getString("config_json"));
        return trigger;
    }
}
