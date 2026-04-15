package com.lightflare.server.user;

import lombok.Data;

@Data
public class CreateUserRequest {

    private String username;

    private String email;

    private String displayName;

    private String password;

    private String status;

    private String role;
}
