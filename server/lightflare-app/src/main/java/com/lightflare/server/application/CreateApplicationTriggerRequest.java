package com.lightflare.server.application;

import lombok.Data;

@Data
public class CreateApplicationTriggerRequest {

    private String triggerType;
    private String startStepId;
    private String configJson;
}
