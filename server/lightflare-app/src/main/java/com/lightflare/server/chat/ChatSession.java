package com.lightflare.server.chat;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("chat_session")
public class ChatSession {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_DELETED = "deleted";

    @Id
    private String id;

    private String title;

    @Column("user_id")
    private String userId;

    @Column("total_tokens")
    private Integer totalTokens;

    @Column("total_input_tokens")
    private Integer totalInputTokens;

    @Column("total_output_tokens")
    private Integer totalOutputTokens;

    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
