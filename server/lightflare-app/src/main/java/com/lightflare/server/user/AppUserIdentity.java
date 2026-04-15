package com.lightflare.server.user;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("app_user_identity")
public class AppUserIdentity {

    @Id
    private String id;

    @Column("app_user_id")
    private String appUserId;

    private String provider;

    @Column("external_user_id")
    private String externalUserId;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
