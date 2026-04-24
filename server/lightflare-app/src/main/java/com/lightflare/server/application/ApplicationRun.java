package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_run")
public class ApplicationRun {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    @Id
    private String id;

    @Column("application_id")
    private String applicationId;

    @Column("application_version_id")
    private String applicationVersionId;

    @Column("trigger_id")
    private String triggerId;

    private String status;

    @Column("input_json")
    private String inputJson;

    @Column("output_json")
    private String outputJson;

    @Column("error_message")
    private String errorMessage;

    @Column("started_by")
    private String startedBy;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;
}
