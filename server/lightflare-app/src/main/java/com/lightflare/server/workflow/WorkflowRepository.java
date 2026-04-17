package com.lightflare.server.workflow;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends CrudRepository<Workflow, String> {

    @Modifying
    @Query("""
            INSERT INTO workflow (
                id,
                name,
                description,
                schema_definition,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :name,
                :description,
                :schemaDefinition,
                :createdAt,
                :updatedAt
            )
            """)
    int insertWorkflow(@Param("id") String id,
                       @Param("name") String name,
                       @Param("description") String description,
                       @Param("schemaDefinition") String schemaDefinition,
                       @Param("createdAt") java.time.OffsetDateTime createdAt,
                       @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE workflow
            SET name = :name,
                description = :description,
                schema_definition = :schemaDefinition,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateWorkflow(@Param("id") String id,
                       @Param("name") String name,
                       @Param("description") String description,
                       @Param("schemaDefinition") String schemaDefinition,
                       @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM workflow
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            """)
    List<Workflow> findAllOrdered();

    @Modifying
    @Query("""
            DELETE FROM workflow
            WHERE id = :id
            """)
    int deleteWorkflowById(@Param("id") String id);
}
