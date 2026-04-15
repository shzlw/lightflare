package com.lightflare.server.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExecutionCheckpointRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int insert(String id,
                      String executionId,
                      String executionType,
                      String status,
                      String referenceType,
                      String referenceId,
                      String payload,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt) {
        String sql = """
                INSERT INTO execution_checkpoint (
                    id,
                    execution_id,
                    execution_type,
                    status,
                    reference_type,
                    reference_id,
                    payload,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :executionId,
                    :executionType,
                    :status,
                    :referenceType,
                    :referenceId,
                    :payload,
                    :createdAt,
                    :updatedAt
                )
                """;

        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("executionId", executionId)
                .addValue("executionType", executionType)
                .addValue("status", status)
                .addValue("referenceType", referenceType)
                .addValue("referenceId", referenceId)
                .addValue("payload", payload)
                .addValue("createdAt", createdAt)
                .addValue("updatedAt", updatedAt));
    }

    public boolean update(String id, String status, String payload, OffsetDateTime updatedAt) {
        String sql = """
                UPDATE execution_checkpoint
                SET status = :status,
                    payload = :payload,
                    updated_at = :updatedAt
                WHERE id = :id
                """;

        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("payload", payload)
                .addValue("updatedAt", updatedAt)) == 1;
    }

    public Optional<ExecutionCheckpoint> findById(String id) {
        String sql = """
                SELECT *
                FROM execution_checkpoint
                WHERE id = :id
                """;
        List<ExecutionCheckpoint> checkpoints = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("id", id),
                ExecutionCheckpointRepository::mapRow
        );
        return checkpoints.stream().findFirst();
    }

    public Optional<ExecutionCheckpoint> findLatestByReferenceAndStatuses(String executionType,
                                                                          String referenceType,
                                                                          String referenceId,
                                                                          List<String> statuses) {
        String sql = """
                SELECT *
                FROM execution_checkpoint
                WHERE execution_type = :executionType
                  AND reference_type = :referenceType
                  AND reference_id = :referenceId
                  AND status IN (:statuses)
                ORDER BY updated_at DESC, created_at DESC, id DESC
                LIMIT 1
                """;
        List<ExecutionCheckpoint> checkpoints = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("executionType", executionType)
                        .addValue("referenceType", referenceType)
                        .addValue("referenceId", referenceId)
                        .addValue("statuses", statuses),
                ExecutionCheckpointRepository::mapRow
        );
        return checkpoints.stream().findFirst();
    }

    private static ExecutionCheckpoint mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExecutionCheckpoint(
                resultSet.getString("id"),
                resultSet.getString("execution_id"),
                resultSet.getString("execution_type"),
                resultSet.getString("status"),
                resultSet.getString("reference_type"),
                resultSet.getString("reference_id"),
                resultSet.getString("payload"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
