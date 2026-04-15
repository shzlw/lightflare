package com.lightflare.server.chat;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("chat_message")
public class ChatMessage {

    @Id
    private String id;

    @Column("session_id")
    private String sessionId;

    private String source;

    private String content;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
