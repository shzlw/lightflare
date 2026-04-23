package com.lightflare.server.project;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends CrudRepository<Project, String> {

    @Modifying
    @Query("""
            INSERT INTO project (
                id,
                title,
                description,
                user_id,
                status,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :title,
                :description,
                :userId,
                :status,
                :createdAt,
                :updatedAt
            )
            """)
    int insertProject(@Param("id") String id,
                      @Param("title") String title,
                      @Param("description") String description,
                      @Param("userId") String userId,
                      @Param("status") String status,
                      @Param("createdAt") OffsetDateTime createdAt,
                      @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM project
            WHERE status <> 'deleted'
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR description ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Project> findPage(@Param("query") String query,
                           @Param("userId") String userId,
                           @Param("isAdmin") boolean isAdmin,
                           @Param("limit") int limit,
                           @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM project
            WHERE status <> 'deleted'
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR description ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            """)
    long countProjects(@Param("query") String query,
                       @Param("userId") String userId,
                       @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Query("""
            UPDATE project
            SET title = :title,
                description = :description,
                status = :status,
                updated_at = :updatedAt
            WHERE id = :id
              AND status <> 'deleted'
            """)
    int updateProject(@Param("id") String id,
                      @Param("title") String title,
                      @Param("description") String description,
                      @Param("status") String status,
                      @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE project
            SET status = 'deleted',
                updated_at = (NOW() AT TIME ZONE 'UTC')
            WHERE id = :id
              AND status <> 'deleted'
            """)
    int deleteProjectById(@Param("id") String id);
}
