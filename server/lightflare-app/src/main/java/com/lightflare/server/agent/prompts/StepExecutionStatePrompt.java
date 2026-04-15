package com.lightflare.server.agent.prompts;

import lombok.Data;

@Data
public class StepExecutionStatePrompt {

    private int attemptNumber;

    private boolean successfulToolResultAvailable;

    private String latestToolName;

    private String latestToolOutcome;

    private String latestToolResult;
}
