package com.lightflare.server.agent.skill;

import com.lightflare.server.memory.Memory;
import com.lightflare.server.skill.Skill;
import com.lightflare.server.agent.prompts.SkillPromptItem;
import com.lightflare.server.skill.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillSelectionService {

    private final SkillRepository skillRepository;

    public SkillContext buildSkillContext(Memory currentMemory) {
        List<Skill> similarSkills = new ArrayList<>();
        if (currentMemory != null && StringUtils.hasText(currentMemory.getEmbeddingVector())) {
            log.info("Loading similar skills using embedding for memoryId={}", currentMemory.getId());
            similarSkills = skillRepository.findByCosineSimilarity(currentMemory.getEmbeddingVector());
        }

        List<Skill> availableSkills = mergeSkills(similarSkills, loadAllSkills());
        List<SkillPromptItem> plannerSkills = availableSkills.stream()
                .map(this::toPlannerSkillPromptItem)
                .toList();
        log.info("Built skill context with {} similar skills, {} total available skills",
                similarSkills.size(), availableSkills.size());

        return new SkillContext(availableSkills, plannerSkills);
    }

    public Skill resolveSelectedSkill(String selectedSkillName, List<Skill> availableSkills) {
        if (!StringUtils.hasText(selectedSkillName)) {
            log.info("No selected skill requested");
            return null;
        }

        for (Skill skill : availableSkills) {
            if (selectedSkillName.equalsIgnoreCase(skill.getName())) {
                log.info("Resolved selected skill '{}' from available skills", selectedSkillName);
                return skill;
            }
        }

        for (Skill skill : skillRepository.findAll()) {
            if (selectedSkillName.equalsIgnoreCase(skill.getName())) {
                log.info("Resolved selected skill '{}' from repository fallback", selectedSkillName);
                return skill;
            }
        }

        log.info("Could not resolve selected skill '{}'", selectedSkillName);
        return null;
    }

    private List<Skill> mergeSkills(List<Skill> prioritizedSkills, List<Skill> allSkills) {
        List<Skill> mergedSkills = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        addSkills(mergedSkills, seenKeys, prioritizedSkills);
        addSkills(mergedSkills, seenKeys, allSkills);
        return mergedSkills;
    }

    private void addSkills(List<Skill> mergedSkills, Set<String> seenKeys, List<Skill> skills) {
        if (CollectionUtils.isEmpty(skills)) {
            return;
        }

        for (Skill skill : skills) {
            if (skill == null) {
                continue;
            }

            String key = StringUtils.hasText(skill.getId()) ? skill.getId() : skill.getName();
            if (!StringUtils.hasText(key) || !seenKeys.add(key)) {
                continue;
            }

            mergedSkills.add(skill);
        }
    }

    private List<Skill> loadAllSkills() {
        List<Skill> skills = new ArrayList<>();
        for (Skill skill : skillRepository.findAll()) {
            skills.add(skill);
        }
        log.info("Loaded {} skills from repository", skills.size());
        return skills;
    }

    private SkillPromptItem toPlannerSkillPromptItem(Skill skill) {
        SkillPromptItem promptItem = new SkillPromptItem();
        promptItem.setName(skill.getName());
        promptItem.setDescription(skill.getDescription());
        promptItem.setHasInstructions(StringUtils.hasText(skill.getContent()));
        return promptItem;
    }
}
