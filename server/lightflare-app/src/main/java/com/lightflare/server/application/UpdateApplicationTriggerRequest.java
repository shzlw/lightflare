package com.lightflare.server.application;

import lombok.Data;

@Data
public class UpdateApplicationTriggerRequest {

    private String triggerType;
    private String startStepId;
    private String configJson;
}
