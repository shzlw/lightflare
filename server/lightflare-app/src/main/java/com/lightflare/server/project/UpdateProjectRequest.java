package com.lightflare.server.project;

import lombok.Data;

@Data
public class UpdateProjectRequest {

    private String title;
    private String description;
    private String status;
}
