package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends CrudRepository<Document, String> {

    @Modifying
    @Query("""
            INSERT INTO document (
                id,
                memory_id,
                file_name,
                file_path,
                file_size,
                file_content_type,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :memoryId,
                :fileName,
                :filePath,
                :fileSize,
                :fileContentType,
                :createdAt,
                :updatedAt
            )
            """)
    int insert(@Param("id") String id,
               @Param("memoryId") String memoryId,
               @Param("fileName") String fileName,
               @Param("filePath") String filePath,
               @Param("fileSize") Long fileSize,
               @Param("fileContentType") String fileContentType,
               @Param("createdAt") OffsetDateTime createdAt,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM document
            WHERE memory_id = :memoryId
            """)
    Optional<Document> findByMemoryId(@Param("memoryId") String memoryId);
}
