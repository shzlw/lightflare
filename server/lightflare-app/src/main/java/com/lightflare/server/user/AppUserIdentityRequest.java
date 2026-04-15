package com.lightflare.server.user;

import lombok.Data;

@Data
public class AppUserIdentityRequest {

    private String provider;
    private String externalUserId;
}
