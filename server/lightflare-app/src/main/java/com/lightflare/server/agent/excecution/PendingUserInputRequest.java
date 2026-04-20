package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMGetResponse;
import java.util.List;
import lombok.Data;

@Data
public class PendingUserInputRequest {

    private String stepId;
    private String toolName;
    private List<String> missingInputs = List.of();
    private String question;
    private LLMGetResponse.ToolCall partialToolCall;

}
