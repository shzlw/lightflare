package com.lightflare.server.chat;

import com.lightflare.server.chat.ChatSession;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends CrudRepository<ChatSession, String> {

    @Modifying
    @Query("""
            INSERT INTO chat_session (
                id,
                project_id,
                title,
                user_id,
                total_tokens,
                total_input_tokens,
                total_output_tokens,
                status,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :projectId,
                :title,
                :userId,
                :totalTokens,
                :totalInputTokens,
                :totalOutputTokens,
                :status,
                :createdAt,
                :updatedAt
            )
            """)
    int insertChatSession(@Param("id") String id,
                          @Param("projectId") String projectId,
                          @Param("title") String title,
                          @Param("userId") String userId,
                          @Param("totalTokens") Integer totalTokens,
                          @Param("totalInputTokens") Integer totalInputTokens,
                          @Param("totalOutputTokens") Integer totalOutputTokens,
                          @Param("status") String status,
                          @Param("createdAt") java.time.OffsetDateTime createdAt,
                          @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM chat_session
            WHERE status = 'active'
              AND (:projectId IS NULL OR project_id = :projectId)
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR project_id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ChatSession> findPage(@Param("query") String query,
                               @Param("projectId") String projectId,
                               @Param("userId") String userId,
                               @Param("isAdmin") boolean isAdmin,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM chat_session
            WHERE status = 'active'
              AND (:projectId IS NULL OR project_id = :projectId)
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR project_id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            """)
    long countChatSessions(@Param("query") String query,
                           @Param("projectId") String projectId,
                           @Param("userId") String userId,
                           @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET total_tokens = COALESCE(chat_session.total_tokens, 0) + COALESCE(:totalTokens, 0),
                total_input_tokens = COALESCE(chat_session.total_input_tokens, 0) + COALESCE(:inputTokens, 0),
                total_output_tokens = COALESCE(chat_session.total_output_tokens, 0) + COALESCE(:outputTokens, 0),
                updated_at = (NOW() AT TIME ZONE 'UTC')
            WHERE id = :sessionId
            """)
    int recordTokenUsage(@Param("sessionId") String sessionId,
                         @Param("totalTokens") Long totalTokens,
                         @Param("inputTokens") Long inputTokens,
                         @Param("outputTokens") Long outputTokens);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET status = 'deleted',
                updated_at = (NOW() AT TIME ZONE 'UTC')
            WHERE id = :id
              AND status = 'active'
            """)
    int deleteChatSessionById(@Param("id") String id);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET status = 'archived',
                updated_at = (NOW() AT TIME ZONE 'UTC')
            WHERE id = :id
              AND status = 'active'
            """)
    int archiveChatSessionById(@Param("id") String id);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET title = :title,
                updated_at = :updatedAt
            WHERE id = :id
              AND status = 'active'
            """)
    int updateChatSessionTitle(@Param("id") String id,
                               @Param("title") String title,
                               @Param("updatedAt") java.time.OffsetDateTime updatedAt);
}
