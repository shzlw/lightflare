package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("application_version")
public class ApplicationVersion {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    @Id
    private String id;

    @Column("application_id")
    private String applicationId;

    @Column("version_number")
    private Integer versionNumber;

    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
