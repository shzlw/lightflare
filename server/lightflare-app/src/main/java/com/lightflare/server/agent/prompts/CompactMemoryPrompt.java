package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "promptDescription",
        "inputsToCompact"
})
public class CompactMemoryPrompt {

    private String promptDescription;

    private List<MemoryPromptItem> inputsToCompact;
}
