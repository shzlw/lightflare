package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_execution")
public class WorkflowExecution {

    @Id
    private String id;

    @Column("workflow_id")
    private String workflowId;

    private int version;

    private String status;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;
}
