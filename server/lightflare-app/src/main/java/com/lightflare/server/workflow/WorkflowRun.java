package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_run")
public class WorkflowRun {

    @Id
    private String id;

    @Column("workflow_id")
    private String workflowId;

    @Column("trigger_id")
    private String triggerId;

    @Column("trigger_type")
    private String triggerType;

    private String status;

    @Column("input_json")
    private String inputJson;

    @Column("output_json")
    private String outputJson;

    @Column("error_message")
    private String errorMessage;

    @Column("started_by")
    private String startedBy;

    @Column("source_id")
    private String sourceId;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
