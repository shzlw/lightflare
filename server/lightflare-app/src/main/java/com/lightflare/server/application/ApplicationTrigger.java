package com.lightflare.server.application;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_trigger")
public class ApplicationTrigger {

    @Id
    private String id;

    @Column("application_version_id")
    private String applicationVersionId;

    @Column("trigger_type")
    private String triggerType;

    @Column("start_step_id")
    private String startStepId;

    @Column("config_json")
    private String configJson;
}
