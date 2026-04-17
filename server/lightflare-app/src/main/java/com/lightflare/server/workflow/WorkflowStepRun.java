package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_step_run")
public class WorkflowStepRun {

    @Id
    private String id;

    @Column("workflow_run_id")
    private String workflowRunId;

    @Column("step_id")
    private String stepId;

    @Column("step_name")
    private String stepName;

    @Column("step_type")
    private String stepType;

    private String status;

    @Column("input_json")
    private String inputJson;

    @Column("output_json")
    private String outputJson;

    @Column("error_message")
    private String errorMessage;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;
}
