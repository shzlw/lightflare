package com.lightflare.server.agent.prompts;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightflare.server.tools.core.ToolDefinition;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetResponsePromptRequest {

    private String promptDescription;

    private List<SkillPromptItem> skills;

    private List<MemoryPromptItem> memoryList;

    private List<ToolDefinition> tools;

    private String task;
}
