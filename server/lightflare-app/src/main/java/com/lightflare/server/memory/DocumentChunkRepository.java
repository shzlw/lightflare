package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentChunkRepository extends CrudRepository<DocumentChunk, String> {

    @Modifying
    @Query("""
            INSERT INTO document_chunk (
                id,
                document_id,
                chunk_index,
                content,
                embedding_vector,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :documentId,
                :chunkIndex,
                :content,
                CAST(:embeddingVector AS vector),
                :createdAt,
                :updatedAt
            )
            """)
    int insert(@Param("id") String id,
               @Param("documentId") String documentId,
               @Param("chunkIndex") int chunkIndex,
               @Param("content") String content,
               @Param("embeddingVector") String embeddingVector,
               @Param("createdAt") OffsetDateTime createdAt,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE document_chunk
            SET embedding_vector = CAST(:embedding AS vector),
                updated_at = :updatedAt
            WHERE id = :id
            """)
    boolean updateEmbeddingById(@Param("id") String id,
                                @Param("embedding") String embeddingVector,
                                @Param("updatedAt") OffsetDateTime updatedAt);

}
