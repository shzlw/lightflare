package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("document_chunk")
public class DocumentChunk {

    @Id
    private String id;

    @Column("document_id")
    private String documentId;

    @Column("chunk_index")
    private Integer chunkIndex;

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
