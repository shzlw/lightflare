package com.lightflare.server.workflow;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface WorkflowStepExecutionRepository extends CrudRepository<WorkflowStepExecution, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow_step_execution (
                id, workflow_execution_id, step_id, version, status, input_data, started_at
            )
            VALUES (
                :id, :executionId, :stepId, :version, :status, :inputData, :startedAt
            )
            """)
    void insertStepExecution(@Param("id") String id,
                             @Param("executionId") String executionId,
                             @Param("stepId") String stepId,
                             @Param("version") int version,
                             @Param("status") String status,
                             @Param("inputData") String inputData,
                             @Param("startedAt") OffsetDateTime startedAt);

    @Modifying
    @Query("""
            UPDATE workflow_step_execution
            SET status = :status,
                output_data = :outputData,
                error_message = :errorMessage,
                completed_at = :completedAt
            WHERE id = :id
            """)
    void updateStepResult(@Param("id") String id,
                          @Param("status") String status,
                          @Param("outputData") String outputData,
                          @Param("errorMessage") String errorMessage,
                          @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query("""
            UPDATE workflow_step_execution
            SET status = :status,
                error_message = :errorMessage,
                completed_at = :completedAt
            WHERE id = :id
            """)
    void updateStepFailure(@Param("id") String id,
                           @Param("status") String status,
                           @Param("errorMessage") String errorMessage,
                           @Param("completedAt") OffsetDateTime completedAt);

    @Query("""
            SELECT * FROM workflow_step_execution
            WHERE workflow_execution_id = :executionId
            ORDER BY started_at ASC
            """)
    List<WorkflowStepExecution> findByWorkflowExecutionId(@Param("executionId") String executionId);
}
