package com.lightflare.server.agent.prompts;

import lombok.Data;

@Data
public class SkillPromptItem {

    private String name;
    private String description;
    private boolean hasInstructions;
}
