package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("memory")
public class Memory {

    public static final String SCOPE_SESSION = "session";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_PUBLIC = "public";

    public static final String KIND_CHAT_MESSAGE = "chat_message";
    public static final String KIND_KNOWLEDGE_NOTE = "knowledge_note";
    public static final String KIND_SUMMARY = "summary";
    public static final String KIND_FACT = "fact";
    public static final String KIND_TOOL_RESULT = "tool_result";
    public static final String KIND_DOCUMENT = "document";
    public static final String SOURCE_USER = "user";
    public static final String SOURCE_AGENT = "agent";
    public static final String SOURCE_SYSTEM = "system";
    public static final String SOURCE_IMPORT = "import";

    public static final String RETENTION_POLICY_COMPACTABLE = "compactable";
    public static final String RETENTION_POLICY_PRESERVE_RAW = "preserve_raw";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_DELETED = "deleted";

    public static final String STATUS_REASON_COMPACTED = "compacted";
    public static final String STATUS_REASON_USER_DELETED = "user_deleted";
    public static final String STATUS_REASON_ADMIN_DELETED = "admin_deleted";
    public static final String STATUS_REASON_EXPIRED = "expired";
    public static final String STATUS_REASON_MANUAL = "manual";

    @Id
    private String id;

    @Column("owner_user_id")
    private String ownerUserId;

    @Column("session_id")
    private String sessionId;

    private String scope;

    private String kind;

    @Column("retention_policy")
    private String retentionPolicy;

    private String source;

    private String status;

    @Column("status_reason")
    private String statusReason;

    @Column("status_changed_at")
    private OffsetDateTime statusChangedAt;

    @Column("status_changed_by")
    private String statusChangedBy;

    private String content;

    @Column("embedding_vector")
    private EmbeddingVector embeddingVectorValue;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    // Convert List<Float> -> String when saving
    public void setEmbeddingVectorFromList(List<Float> vector) {
        this.embeddingVectorValue = EmbeddingVector.fromList(vector);
    }

    // Convert back to List<Float> when reading
    public List<Float> getEmbeddingVectorAsList() {
        return embeddingVectorValue == null ? List.of() : embeddingVectorValue.asList();
    }

    public String getEmbeddingVector() {
        return embeddingVectorValue == null ? null : embeddingVectorValue.value();
    }

    public void setEmbeddingVector(String embeddingVector) {
        this.embeddingVectorValue = EmbeddingVector.of(embeddingVector);
    }
}
