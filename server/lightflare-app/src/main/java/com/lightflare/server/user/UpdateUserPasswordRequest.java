package com.lightflare.server.user;

import lombok.Data;

@Data
public class UpdateUserPasswordRequest {

    private String newPassword;

    private Boolean mustChangePassword;
}
