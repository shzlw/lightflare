package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowTriggerRepository extends CrudRepository<WorkflowTrigger, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow_trigger (
                id,
                workflow_id,
                trigger_type,
                name,
                enabled,
                config_json,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :workflowId,
                :triggerType,
                :name,
                :enabled,
                :configJson,
                :createdAt,
                :updatedAt
            )
            """)
    int insertTrigger(@Param("id") String id,
                      @Param("workflowId") String workflowId,
                      @Param("triggerType") String triggerType,
                      @Param("name") String name,
                      @Param("enabled") boolean enabled,
                      @Param("configJson") String configJson,
                      @Param("createdAt") OffsetDateTime createdAt,
                      @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE workflow_trigger
            SET trigger_type = :triggerType,
                name = :name,
                enabled = :enabled,
                config_json = :configJson,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateTrigger(@Param("id") String id,
                      @Param("triggerType") String triggerType,
                      @Param("name") String name,
                      @Param("enabled") boolean enabled,
                      @Param("configJson") String configJson,
                      @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM workflow_trigger
            WHERE workflow_id = :workflowId
            ORDER BY created_at ASC, id ASC
            """)
    List<WorkflowTrigger> findByWorkflowId(@Param("workflowId") String workflowId);

    @Query("""
            SELECT *
            FROM workflow_trigger
            WHERE workflow_id = :workflowId
              AND trigger_type = :triggerType
            ORDER BY created_at ASC, id ASC
            """)
    List<WorkflowTrigger> findByWorkflowIdAndType(@Param("workflowId") String workflowId,
                                                  @Param("triggerType") String triggerType);

    @Modifying
    @Query("""
            DELETE FROM workflow_trigger
            WHERE id = :id
            """)
    int deleteTriggerById(@Param("id") String id);

    @Modifying
    @Query("""
            DELETE FROM workflow_trigger
            WHERE workflow_id = :workflowId
            """)
    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}
