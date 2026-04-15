package com.lightflare.server.skill;

import com.lightflare.server.skill.SkillService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/skills")
public class InternalSkillController {

    private final SkillService skillService;

    @GetMapping
    public SkillPageResponse listSkills(@RequestParam(name = "page", defaultValue = "0") int page,
                                        @RequestParam(name = "size", defaultValue = "20") int size) {
        return skillService.listSkills(page, size);
    }

    @GetMapping("/{id}")
    public SkillResponse getSkill(@PathVariable("id") String id) {
        return skillService.getSkill(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse createSkill(@RequestBody CreateSkillRequest request, HttpServletRequest httpRequest) {
        return skillService.createSkill(request, httpRequest);
    }

    @PutMapping("/{id}")
    public SkillResponse updateSkill(@PathVariable("id") String id,
                                     @RequestBody UpdateSkillRequest request,
                                     HttpServletRequest httpRequest) {
        return skillService.updateSkill(id, request, httpRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable("id") String id) {
        skillService.deleteSkill(id);
    }
}
