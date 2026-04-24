package com.lightflare.server.application;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationStepRunRepository extends CrudRepository<ApplicationStepRun, String> {

    @Modifying
    @Query("""
            INSERT INTO application_step_run (
                id,
                application_run_id,
                step_id,
                status,
                input_json,
                output_json,
                error_message,
                started_at,
                completed_at
            )
            VALUES (
                :id,
                :applicationRunId,
                :stepId,
                :status,
                :inputJson,
                :outputJson,
                :errorMessage,
                :startedAt,
                :completedAt
            )
            """)
    int insertStepRun(@Param("id") String id,
                      @Param("applicationRunId") String applicationRunId,
                      @Param("stepId") String stepId,
                      @Param("status") String status,
                      @Param("inputJson") String inputJson,
                      @Param("outputJson") String outputJson,
                      @Param("errorMessage") String errorMessage,
                      @Param("startedAt") OffsetDateTime startedAt,
                      @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query("""
            UPDATE application_step_run
            SET status = :status,
                output_json = :outputJson,
                error_message = :errorMessage,
                completed_at = :completedAt
            WHERE id = :id
            """)
    int completeStepRun(@Param("id") String id,
                        @Param("status") String status,
                        @Param("outputJson") String outputJson,
                        @Param("errorMessage") String errorMessage,
                        @Param("completedAt") OffsetDateTime completedAt);

    @Query("""
            SELECT *
            FROM application_step_run
            WHERE application_run_id = :applicationRunId
            ORDER BY started_at ASC NULLS LAST, id ASC
            """)
    List<ApplicationStepRun> findByApplicationRunId(@Param("applicationRunId") String applicationRunId);

    @Modifying
    @Query("""
            DELETE FROM application_step_run
            WHERE application_run_id IN (
                SELECT id
                FROM application_run
                WHERE application_id = :applicationId
            )
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
