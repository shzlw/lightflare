package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryRepository extends CrudRepository<Memory, String> {

    @Modifying
    @Query("""
            INSERT INTO memory (
                id,
                owner_user_id,
                session_id,
                scope,
                kind,
                retention_policy,
                source,
                status,
                status_reason,
                status_changed_at,
                status_changed_by,
                content,
                embedding_vector
            )
            VALUES (
                :id,
                :ownerUserId,
                :sessionId,
                :scope,
                :kind,
                :retentionPolicy,
                :source,
                :status,
                :statusReason,
                :statusChangedAt,
                :statusChangedBy,
                :content,
                CAST(:embeddingVector AS vector)
            )
            """)
    int insert(@Param("id") String id,
               @Param("ownerUserId") String ownerUserId,
               @Param("sessionId") String sessionId,
               @Param("scope") String scope,
               @Param("kind") String kind,
               @Param("retentionPolicy") String retentionPolicy,
               @Param("source") String source,
               @Param("status") String status,
               @Param("statusReason") String statusReason,
               @Param("statusChangedAt") OffsetDateTime statusChangedAt,
               @Param("statusChangedBy") String statusChangedBy,
               @Param("content") String content,
               @Param("embeddingVector") String embeddingVector);

    @Query("""
            SELECT m.*
            FROM memory m
            WHERE (:query IS NULL
                    OR m.content ILIKE '%' || :query || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM document d
                        WHERE d.memory_id = m.id
                          AND d.file_name ILIKE '%' || :query || '%'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM document d
                        JOIN document_chunk dc ON dc.document_id = d.id
                        WHERE d.memory_id = m.id
                          AND dc.content ILIKE '%' || :query || '%'
                    ))
              AND (
                    :isAdmin = TRUE
                    OR m.owner_user_id = :requesterUserId
                    OR m.scope = 'public'
                  )
              AND (:sessionId IS NULL OR m.session_id = :sessionId)
              AND (:ownerUserId IS NULL OR m.owner_user_id = :ownerUserId)
              AND (:scope IS NULL OR m.scope = :scope)
              AND (:kind IS NULL OR m.kind = :kind)
              AND (:status IS NULL OR m.status = :status)
            ORDER BY
                CASE WHEN :createdAtSort = 'asc' THEN m.created_at END ASC,
                CASE WHEN :createdAtSort = 'desc' THEN m.created_at END DESC,
                m.id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Memory> findMemoriesPage(@Param("query") String query,
                                  @Param("requesterUserId") String requesterUserId,
                                  @Param("isAdmin") boolean isAdmin,
                                  @Param("sessionId") String sessionId,
                                  @Param("ownerUserId") String ownerUserId,
                                  @Param("scope") String scope,
                                  @Param("kind") String kind,
                                  @Param("status") String status,
                                  @Param("createdAtSort") String createdAtSort,
                                  @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM memory m
            WHERE (:query IS NULL
                    OR m.content ILIKE '%' || :query || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM document d
                        WHERE d.memory_id = m.id
                          AND d.file_name ILIKE '%' || :query || '%'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM document d
                        JOIN document_chunk dc ON dc.document_id = d.id
                        WHERE d.memory_id = m.id
                          AND dc.content ILIKE '%' || :query || '%'
                    ))
              AND (
                    :isAdmin = TRUE
                    OR m.owner_user_id = :requesterUserId
                    OR m.scope = 'public'
                  )
              AND (:sessionId IS NULL OR m.session_id = :sessionId)
              AND (:ownerUserId IS NULL OR m.owner_user_id = :ownerUserId)
              AND (:scope IS NULL OR m.scope = :scope)
              AND (:kind IS NULL OR m.kind = :kind)
              AND (:status IS NULL OR m.status = :status)
            """)
    long countMemories(@Param("query") String query,
                       @Param("requesterUserId") String requesterUserId,
                       @Param("isAdmin") boolean isAdmin,
                       @Param("sessionId") String sessionId,
                       @Param("ownerUserId") String ownerUserId,
                       @Param("scope") String scope,
                       @Param("kind") String kind,
                       @Param("status") String status);

    @Query("""
            SELECT *
            FROM memory
            WHERE session_id = :sessionId
              AND owner_user_id = :ownerUserId
              AND scope = 'session'
              AND status = 'active'
            ORDER BY created_at ASC
            LIMIT :limit
            """)
    List<Memory> findActiveBySessionIdAndOwnerUserId(@Param("sessionId") String sessionId,
                                                     @Param("ownerUserId") String ownerUserId,
                                                     @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM memory
            WHERE id IN (:memoryIds)
            """)
    List<Memory> findByMemoryIds(@Param("memoryIds") List<String> memoryIds);

    @Modifying
    @Query("""
            UPDATE memory
            SET embedding_vector = CAST(:embedding AS vector),
                updated_at = :updatedAt
            WHERE id = :id
            """)
    boolean updateEmbeddingById(@Param("id") String id,
                                @Param("embedding") String embeddingVector,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE memory
            SET status = :status,
                status_reason = :statusReason,
                status_changed_at = :statusChangedAt,
                status_changed_by = :statusChangedBy,
                updated_at = :updatedAt
            WHERE id IN (:memoryIds)
              AND status = 'active'
            """)
    int updateStatusByMemoryIds(@Param("memoryIds") List<String> memoryIds,
                                @Param("status") String status,
                                @Param("statusReason") String statusReason,
                                @Param("statusChangedAt") OffsetDateTime statusChangedAt,
                                @Param("statusChangedBy") String statusChangedBy,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE memory
            SET status = :status,
                status_reason = :statusReason,
                status_changed_at = :statusChangedAt,
                status_changed_by = :statusChangedBy,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("statusReason") String statusReason,
                         @Param("statusChangedAt") OffsetDateTime statusChangedAt,
                         @Param("statusChangedBy") String statusChangedBy,
                         @Param("updatedAt") OffsetDateTime updatedAt);
}
