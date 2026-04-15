package com.lightflare.server.memory;

import lombok.Data;

@Data
public class CreateMemoryRequest {

    private String ownerUserId;

    private String sessionId;

    private String scope;

    private String kind;

    private String source;

    private String retentionPolicy;

    private String content;
}
