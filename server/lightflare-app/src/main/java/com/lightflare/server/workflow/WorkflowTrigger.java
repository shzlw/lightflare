package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_trigger")
public class WorkflowTrigger {

    @Id
    private String id;

    @Column("workflow_id")
    private String workflowId;

    @Column("trigger_type")
    private String triggerType;

    private String name;
    private boolean enabled;

    @Column("config_json")
    private String configJson;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
