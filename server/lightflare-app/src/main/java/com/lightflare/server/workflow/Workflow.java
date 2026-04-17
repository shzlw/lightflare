package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow")
public class Workflow {

    @Id
    private String id;

    private String name;

    private String description;

    @Column("schema_definition")
    private String schemaDefinition;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
