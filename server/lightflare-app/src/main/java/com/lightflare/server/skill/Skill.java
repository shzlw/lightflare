package com.lightflare.server.skill;

import com.lightflare.server.memory.EmbeddingVector;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("skill")
public class Skill {

    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    private String id;

    private String name;

    private String description;

    private String visibility;

    @Column("user_id")
    private String userId;

    private String source;

    private String content;

    @Column("embedding_vector")
    private EmbeddingVector embeddingVectorValue;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    public void setEmbeddingVectorFromList(List<Float> vector) {
        this.embeddingVectorValue = EmbeddingVector.fromList(vector);
    }

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
