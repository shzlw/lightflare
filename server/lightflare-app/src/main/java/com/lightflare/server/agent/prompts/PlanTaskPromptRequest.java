package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanTaskPromptRequest {

    private String promptDescription;

    private List<SkillPromptItem> skills;

    private List<MemoryPromptItem> memoryList;

    private List<PlanToolPromptItem> tools;

    private String task;
}
