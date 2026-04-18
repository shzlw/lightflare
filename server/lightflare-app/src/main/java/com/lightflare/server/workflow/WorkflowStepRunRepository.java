package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowStepRunRepository extends CrudRepository<WorkflowStepRun, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow_step_run (
                id,
                workflow_run_id,
                step_id,
                step_name,
                step_type,
                status,
                input_json,
                output_json,
                error_message,
                started_at,
                completed_at
            )
            VALUES (
                :id,
                :workflowRunId,
                :stepId,
                :stepName,
                :stepType,
                :status,
                :inputJson,
                :outputJson,
                :errorMessage,
                :startedAt,
                :completedAt
            )
            """)
    int insertStepRun(@Param("id") String id,
                      @Param("workflowRunId") String workflowRunId,
                      @Param("stepId") String stepId,
                      @Param("stepName") String stepName,
                      @Param("stepType") String stepType,
                      @Param("status") String status,
                      @Param("inputJson") String inputJson,
                      @Param("outputJson") String outputJson,
                      @Param("errorMessage") String errorMessage,
                      @Param("startedAt") OffsetDateTime startedAt,
                      @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query("""
            UPDATE workflow_step_run
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
            FROM workflow_step_run
            WHERE workflow_run_id = :workflowRunId
            ORDER BY started_at ASC, id ASC
            """)
    List<WorkflowStepRun> findByWorkflowRunId(@Param("workflowRunId") String workflowRunId);

    @Modifying
    @Query("""
            DELETE FROM workflow_step_run
            WHERE workflow_run_id IN (
                SELECT id
                FROM workflow_run
                WHERE workflow_id = :workflowId
            )
            """)
    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}
