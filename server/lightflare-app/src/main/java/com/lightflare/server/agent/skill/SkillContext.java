package com.lightflare.server.agent.skill;

import com.lightflare.server.skill.Skill;
import com.lightflare.server.agent.prompts.SkillPromptItem;

import java.util.List;

public record SkillContext(List<Skill> availableSkills,
                           List<SkillPromptItem> plannerSkills) {

    public SkillContext {
        availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
        plannerSkills = plannerSkills == null ? List.of() : List.copyOf(plannerSkills);
    }
}
