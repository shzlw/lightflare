package com.lightflare.server.chat;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("chat_artifact")
public class ChatArtifact {

    @Id
    private String id;

    @Column("session_id")
    private String sessionId;

    @Column("message_id")
    private String messageId;

    @Column("artifact_type")
    private String artifactType;

    private String title;

    private String content;

    private String metadata;

    private Boolean pinned;

    @Column("display_order")
    private Integer displayOrder;

    @Column("created_by")
    private String createdBy;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
