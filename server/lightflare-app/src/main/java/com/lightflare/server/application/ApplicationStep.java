package com.lightflare.server.application;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_step")
public class ApplicationStep {

    @Id
    private String id;

    @Column("application_version_id")
    private String applicationVersionId;

    @Column("step_key")
    private String stepKey;

    private String name;

    @Column("step_type")
    private String stepType;

    @Column("config_json")
    private String configJson;
}
