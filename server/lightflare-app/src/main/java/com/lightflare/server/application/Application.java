package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application")
public class Application {

    @Id
    private String id;

    private String name;

    private String description;

    @Column("created_by")
    private String createdBy;

    @Column("source_chat_session_id")
    private String sourceChatSessionId;

    @Column("published_version_id")
    private String publishedVersionId;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
