package com.lightflare.server.application;

import lombok.Data;

@Data
public class CreateApplicationRequest {

    private String id;
    private String name;
    private String description;
    private String createdBy;
    private String sourceChatSessionId;
}
