package com.lightflare.server.chat;

import lombok.Data;

@Data
public class CreateChatSessionRequest {

    private String id;
    private String title;
    private String userId;
}
