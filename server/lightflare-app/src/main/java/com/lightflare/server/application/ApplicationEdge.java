package com.lightflare.server.application;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_edge")
public class ApplicationEdge {

    @Id
    private String id;

    @Column("application_version_id")
    private String applicationVersionId;

    @Column("from_step_id")
    private String fromStepId;

    @Column("to_step_id")
    private String toStepId;

    @Column("condition_type")
    private String conditionType;

    @Column("condition_json")
    private String conditionJson;
}
