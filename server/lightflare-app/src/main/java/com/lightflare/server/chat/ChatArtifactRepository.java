package com.lightflare.server.chat;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatArtifactRepository extends CrudRepository<ChatArtifact, String> {

    @Query("""
            SELECT *
            FROM chat_artifact
            WHERE session_id = :sessionId
            ORDER BY pinned DESC, display_order ASC, updated_at DESC, created_at DESC, id DESC
            """)
    List<ChatArtifact> findBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            INSERT INTO chat_artifact (
                id,
                session_id,
                message_id,
                artifact_type,
                title,
                content,
                metadata,
                pinned,
                display_order,
                created_by,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :sessionId,
                :messageId,
                :artifactType,
                :title,
                :content,
                :metadata,
                :pinned,
                :displayOrder,
                :createdBy,
                :createdAt,
                :updatedAt
            )
            """)
    int insertArtifact(@Param("id") String id,
                       @Param("sessionId") String sessionId,
                       @Param("messageId") String messageId,
                       @Param("artifactType") String artifactType,
                       @Param("title") String title,
                       @Param("content") String content,
                       @Param("metadata") String metadata,
                       @Param("pinned") boolean pinned,
                       @Param("displayOrder") int displayOrder,
                       @Param("createdBy") String createdBy,
                       @Param("createdAt") OffsetDateTime createdAt,
                       @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE chat_artifact
            SET message_id = :messageId,
                artifact_type = :artifactType,
                title = :title,
                content = :content,
                metadata = :metadata,
                pinned = :pinned,
                display_order = :displayOrder,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateArtifact(@Param("id") String id,
                       @Param("messageId") String messageId,
                       @Param("artifactType") String artifactType,
                       @Param("title") String title,
                       @Param("content") String content,
                       @Param("metadata") String metadata,
                       @Param("pinned") boolean pinned,
                       @Param("displayOrder") int displayOrder,
                       @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            DELETE FROM chat_artifact
            WHERE id = :id
            """)
    int deleteArtifactById(@Param("id") String id);

    @Modifying
    @Query("""
            DELETE FROM chat_artifact
            WHERE session_id = :sessionId
            """)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
