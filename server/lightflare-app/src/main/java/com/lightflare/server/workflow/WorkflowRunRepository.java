package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends CrudRepository<WorkflowRun, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow_run (
                id,
                workflow_id,
                trigger_id,
                trigger_type,
                status,
                input_json,
                output_json,
                error_message,
                started_by,
                source_id,
                started_at,
                completed_at,
                created_at
            )
            VALUES (
                :id,
                :workflowId,
                :triggerId,
                :triggerType,
                :status,
                :inputJson,
                :outputJson,
                :errorMessage,
                :startedBy,
                :sourceId,
                :startedAt,
                :completedAt,
                :createdAt
            )
            """)
    int insertRun(@Param("id") String id,
                  @Param("workflowId") String workflowId,
                  @Param("triggerId") String triggerId,
                  @Param("triggerType") String triggerType,
                  @Param("status") String status,
                  @Param("inputJson") String inputJson,
                  @Param("outputJson") String outputJson,
                  @Param("errorMessage") String errorMessage,
                  @Param("startedBy") String startedBy,
                  @Param("sourceId") String sourceId,
                  @Param("startedAt") OffsetDateTime startedAt,
                  @Param("completedAt") OffsetDateTime completedAt,
                  @Param("createdAt") OffsetDateTime createdAt);

    @Modifying
    @Query("""
            UPDATE workflow_run
            SET status = :status,
                output_json = :outputJson,
                error_message = :errorMessage,
                completed_at = :completedAt
            WHERE id = :id
            """)
    int completeRun(@Param("id") String id,
                    @Param("status") String status,
                    @Param("outputJson") String outputJson,
                    @Param("errorMessage") String errorMessage,
                    @Param("completedAt") OffsetDateTime completedAt);

    @Query("""
            SELECT *
            FROM workflow_run
            WHERE workflow_id = :workflowId
            ORDER BY started_at DESC NULLS LAST, created_at DESC, id DESC
            LIMIT :limit
            """)
    List<WorkflowRun> findRecentByWorkflowId(@Param("workflowId") String workflowId,
                                             @Param("limit") int limit);

    @Modifying
    @Query("""
            DELETE FROM workflow_run
            WHERE workflow_id = :workflowId
            """)
    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}
