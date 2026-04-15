package com.lightflare.server.chat;

import com.lightflare.server.chat.ChatMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessage, String> {

    @Query("""
            SELECT *
            FROM chat_message
            WHERE session_id = :sessionId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """)
    List<ChatMessage> findLatestPageBySessionId(@Param("sessionId") String sessionId,
                                                @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM chat_message
            WHERE session_id = :sessionId
              AND (
                  created_at < :beforeCreatedAt
                  OR (created_at = :beforeCreatedAt AND id < :beforeId)
              )
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """)
    List<ChatMessage> findPageBySessionIdBeforeCursor(@Param("sessionId") String sessionId,
                                                      @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
                                                      @Param("beforeId") String beforeId,
                                                      @Param("limit") int limit);

    @Modifying
    @Query("""
            INSERT INTO chat_message (id, session_id, source, content, created_at)
            VALUES (:id, :sessionId, :source, :content, :createdAt)
            """)
    int insert(@Param("id") String id,
               @Param("sessionId") String sessionId,
               @Param("source") String source,
               @Param("content") String content,
               @Param("createdAt") OffsetDateTime createdAt);
}
