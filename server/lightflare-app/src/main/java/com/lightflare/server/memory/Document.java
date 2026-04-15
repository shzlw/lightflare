package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("document")
public class Document {

    @Id
    private String id;

    @Column("memory_id")
    private String memoryId;

    @Column("file_name")
    private String fileName;

    @Column("file_path")
    private String filePath;

    @Column("file_size")
    private Long fileSize;

    @Column("file_content_type")
    private String fileContentType;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
