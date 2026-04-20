package com.lightflare.server.llmproviders.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class LLMPlanResponse {

    private String thoughtProcess;

    private String selectedSkill;

    private List<PlanStep> steps;

    private String response;

    @Data
    public static class PlanStep {
        private String id;
        private String content;
        private String toolCategory;
        private List<String> dependsOn;
        private boolean parallelizable;
        private Status status;

        public enum Status {
            PENDING,
            RUNNING,
            WAITING_FOR_USER,
            COMPLETED,
            FAILED
        }
    }
}
