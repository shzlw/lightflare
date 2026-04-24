package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_step_run")
public class ApplicationStepRun {

    @Id
    private String id;

    @Column("application_run_id")
    private String applicationRunId;

    @Column("step_id")
    private String stepId;

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
