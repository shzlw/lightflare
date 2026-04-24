package com.lightflare.server.chat;

import lombok.Data;

@Data
public class CreateChatArtifactRequest {

    private String id;
    private String messageId;
    private String artifactType;
    private String title;
    private String content;
    private String metadata;
    private Boolean pinned;
    private Integer displayOrder;
}
