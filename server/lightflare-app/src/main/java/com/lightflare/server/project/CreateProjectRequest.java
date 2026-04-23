package com.lightflare.server.project;

import lombok.Data;

@Data
public class CreateProjectRequest {

    private String id;
    private String title;
    private String description;
    private String userId;
}
