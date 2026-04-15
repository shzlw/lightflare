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
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ChatSession> findPage(@Param("query") String query,
                               @Param("userId") String userId,
                               @Param("isAdmin") boolean isAdmin,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM chat_session
            WHERE status = 'active'
              AND (:isAdmin = TRUE OR user_id = :userId)
              AND (:query IS NULL
                   OR title ILIKE '%' || :query || '%'
                   OR id ILIKE '%' || :query || '%'
                   OR user_id ILIKE '%' || :query || '%')
            """)
    long countChatSessions(@Param("query") String query,
                           @Param("userId") String userId,
                           @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Query("""
            INSERT INTO chat_session (
                id,
                user_id,
                total_tokens,
                total_input_tokens,
                total_output_tokens,
                status
            )
            VALUES (
                :sessionId,
                :userId,
                COALESCE(:totalTokens, 0),
                COALESCE(:inputTokens, 0),
                COALESCE(:outputTokens, 0),
                'active'
            )
            ON CONFLICT (id) DO UPDATE
            SET user_id = COALESCE(chat_session.user_id, EXCLUDED.user_id),
                total_tokens = COALESCE(chat_session.total_tokens, 0) + COALESCE(EXCLUDED.total_tokens, 0),
                total_input_tokens = COALESCE(chat_session.total_input_tokens, 0) + COALESCE(EXCLUDED.total_input_tokens, 0),
                total_output_tokens = COALESCE(chat_session.total_output_tokens, 0) + COALESCE(EXCLUDED.total_output_tokens, 0),
                updated_at = (NOW() AT TIME ZONE 'UTC')
            """)
    int recordTokenUsage(@Param("sessionId") String sessionId,
                         @Param("userId") String userId,
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
}
