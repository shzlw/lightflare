package com.lightflare.server.auth;

import lombok.Data;

@Data
public class UpdatePasswordRequest {

    private String newPassword;
}
