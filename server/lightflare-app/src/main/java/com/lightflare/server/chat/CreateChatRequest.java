package com.lightflare.server.chat;

import lombok.Data;

@Data
public class CreateChatRequest {

    private String sessionId;
    private String userId;
    private String data;
}
