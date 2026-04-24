package com.lightflare.server.llmproviders.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMArtifactPayload {

    private String artifactType;
    private String title;
    private Object content;
    private Object metadata;
}
