package com.lightflare.server.workflow;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface WorkflowExecutionRepository extends CrudRepository<WorkflowExecution, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow_execution (id, workflow_id, version, status, started_at)
            VALUES (:id, :workflowId, :version, :status, :startedAt)
            """)
    void insertExecution(@Param("id") String id,
                         @Param("workflowId") String workflowId,
                         @Param("version") int version,
                         @Param("status") String status,
                         @Param("startedAt") OffsetDateTime startedAt);

    @Modifying
    @Query("""
            UPDATE workflow_execution
            SET status = :status, completed_at = :completedAt
            WHERE id = :id
            """)
    void updateStatus(@Param("id") String id,
                      @Param("status") String status,
                      @Param("completedAt") OffsetDateTime completedAt);
}
