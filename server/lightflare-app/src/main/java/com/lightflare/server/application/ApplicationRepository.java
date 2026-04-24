package com.lightflare.server.application;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends CrudRepository<Application, String> {

    @Modifying
    @Query("""
            INSERT INTO application (
                id,
                name,
                description,
                created_by,
                source_chat_session_id,
                published_version_id,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :name,
                :description,
                :createdBy,
                :sourceChatSessionId,
                :publishedVersionId,
                :createdAt,
                :updatedAt
            )
            """)
    int insertApplication(@Param("id") String id,
                          @Param("name") String name,
                          @Param("description") String description,
                          @Param("createdBy") String createdBy,
                          @Param("sourceChatSessionId") String sourceChatSessionId,
                          @Param("publishedVersionId") String publishedVersionId,
                          @Param("createdAt") OffsetDateTime createdAt,
                          @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM application
            WHERE (:isAdmin = TRUE OR created_by = :createdBy)
              AND (:query IS NULL
                   OR name ILIKE '%' || :query || '%'
                   OR description ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR created_by ILIKE '%' || :query || '%'
                   OR source_chat_session_id ILIKE '%' || :query || '%')
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Application> findPage(@Param("query") String query,
                               @Param("createdBy") String createdBy,
                               @Param("isAdmin") boolean isAdmin,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM application
            WHERE (:isAdmin = TRUE OR created_by = :createdBy)
              AND (:query IS NULL
                   OR name ILIKE '%' || :query || '%'
                   OR description ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR created_by ILIKE '%' || :query || '%'
                   OR source_chat_session_id ILIKE '%' || :query || '%')
            """)
    long countApplications(@Param("query") String query,
                           @Param("createdBy") String createdBy,
                           @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Query("""
            UPDATE application
            SET name = :name,
                description = :description,
                source_chat_session_id = :sourceChatSessionId,
                published_version_id = :publishedVersionId,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateApplication(@Param("id") String id,
                          @Param("name") String name,
                          @Param("description") String description,
                          @Param("sourceChatSessionId") String sourceChatSessionId,
                          @Param("publishedVersionId") String publishedVersionId,
                          @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            DELETE FROM application
            WHERE id = :id
            """)
    int deleteApplicationById(@Param("id") String id);
}
