package com.lightflare.server.skill;

import lombok.Data;

@Data
public class CreateSkillRequest {

    private String name;

    private String description;

    private String visibility;

    private String content;
}
