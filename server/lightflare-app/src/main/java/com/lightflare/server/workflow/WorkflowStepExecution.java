package com.lightflare.server.workflow;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_step_execution")
public class WorkflowStepExecution {

    @Id
    private String id;

    @Column("workflow_execution_id")
    private String workflowExecutionId;

    @Column("step_id")
    private String stepId;

    private int version;

    private String status;

    @Column("input_data")
    private String inputData;

    @Column("output_data")
    private String outputData;

    @Column("error_message")
    private String errorMessage;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;
}
