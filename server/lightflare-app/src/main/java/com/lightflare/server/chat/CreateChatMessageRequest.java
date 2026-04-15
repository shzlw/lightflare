package com.lightflare.server.chat;

import lombok.Data;

@Data
public class CreateChatMessageRequest {

    private String source;
    private String content;
}
