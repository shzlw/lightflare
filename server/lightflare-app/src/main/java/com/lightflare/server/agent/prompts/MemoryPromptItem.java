package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonPropertyOrder({
        "type",
        "source",
        "content",
        "createdAt"
})
public class MemoryPromptItem {

    public static final String TYPE_SESSION_HISTORY = "session_history";
    public static final String TYPE_RETRIEVED_MEMORY = "retrieved_memory";
    public static final String TYPE_RETRIEVED_DOCUMENT = "retrieved_document";

    private String type;
    private String source;
    private String content;
    private OffsetDateTime createdAt;
}