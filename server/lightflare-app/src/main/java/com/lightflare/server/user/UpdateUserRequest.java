package com.lightflare.server.user;

import lombok.Data;

@Data
public class UpdateUserRequest {

    private String username;

    private String email;

    private String displayName;

    private String status;
}
