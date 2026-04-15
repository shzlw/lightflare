package com.lightflare.server.user;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("app_user")
public class AppUser {

    @Id
    private String id;

    private String username;

    private String email;

    @Column("display_name")
    private String displayName;

    @Column("password_hash")
    private String passwordHash;

    private String status;

    private String role;

    @Column("must_change_password")
    private Boolean mustChangePassword;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
