package com.lightflare.server.application;

import lombok.Data;

@Data
public class UpdateApplicationRequest {

    private String name;
    private String description;
    private String sourceChatSessionId;
    private String publishedVersionId;
}
