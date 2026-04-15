package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMStepResponse;
import com.lightflare.server.agent.prompts.StepExecutionStatePrompt;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class StepResponseCanonicalizer {

    public LLMStepResponse canonicalize(LLMStepResponse response, StepExecutionStatePrompt stepState) {
        if (response == null || response.getAction() == null) {
            return response;
        }

        if (!StringUtils.hasText(response.getResponse())) {
            response.setResponse(null);
        }
        if (response.getMissingInputs() == null) {
            response.setMissingInputs(List.of());
        }

        return switch (response.getAction()) {
            case USE_TOOL -> canonicalizeUseTool(response, stepState);
            case DIRECT_RESPONSE -> canonicalizeDirectResponse(response, stepState);
            case REQUEST_TOOL_INPUT -> canonicalizeRequestToolInput(response);
            case DESIGN_INSTRUCTIONS -> canonicalizeDesignInstructions(response);
        };
    }

    private LLMStepResponse canonicalizeUseTool(LLMStepResponse response, StepExecutionStatePrompt stepState) {
        LLMGetResponse.ToolCall toolCall = response.getToolCall();
        if (toolCall != null && toolCall.getArguments() == null) {
            toolCall.setArguments(List.of());
        }

        boolean hasValidToolName = toolCall != null && StringUtils.hasText(toolCall.getToolName());
        if (hasValidToolName) {
            if (response.getStepComplete() == null) {
                response.setStepComplete(Boolean.FALSE);
            }
            return response;
        }

        boolean successfulToolResultAvailable = stepState != null && stepState.isSuccessfulToolResultAvailable();
        if (successfulToolResultAvailable || StringUtils.hasText(response.getResponse()) || Boolean.TRUE.equals(response.getStepComplete())) {
            response.setAction(LLMStepResponse.Action.DIRECT_RESPONSE);
            response.setToolCall(null);
            if (response.getStepComplete() == null) {
                response.setStepComplete(Boolean.TRUE);
            }
            return response;
        }

        response.setAction(LLMStepResponse.Action.DIRECT_RESPONSE);
        response.setToolCall(null);
        response.setMissingInputs(List.of());
        if (!StringUtils.hasText(response.getResponse())) {
            response.setResponse("I cannot complete this step because no available tool matches the requested action.");
        }
        response.setStepComplete(Boolean.FALSE);
        return response;
    }

    private LLMStepResponse canonicalizeDirectResponse(LLMStepResponse response, StepExecutionStatePrompt stepState) {
        response.setToolCall(null);
        if (response.getStepComplete() == null) {
            boolean successfulToolResultAvailable = stepState != null && stepState.isSuccessfulToolResultAvailable();
            response.setStepComplete(successfulToolResultAvailable && StringUtils.hasText(response.getResponse()));
        }
        return response;
    }

    private LLMStepResponse canonicalizeRequestToolInput(LLMStepResponse response) {
        if (response.getToolCall() != null && response.getToolCall().getArguments() == null) {
            response.getToolCall().setArguments(List.of());
        }
        boolean missingToolName = response.getToolCall() == null
                || !StringUtils.hasText(response.getToolCall().getToolName())
                || response.getMissingInputs().stream().anyMatch(input -> "toolName".equalsIgnoreCase(input));
        if (missingToolName) {
            response.setAction(LLMStepResponse.Action.DIRECT_RESPONSE);
            response.setToolCall(null);
            response.setMissingInputs(List.of());
            if (!StringUtils.hasText(response.getResponse())) {
                response.setResponse("I cannot complete this step because no available tool matches the requested action.");
            }
            response.setStepComplete(Boolean.FALSE);
            return response;
        }
        response.setStepComplete(Boolean.FALSE);
        return response;
    }

    private LLMStepResponse canonicalizeDesignInstructions(LLMStepResponse response) {
        response.setToolCall(null);
        if (response.getStepComplete() == null) {
            response.setStepComplete(Boolean.TRUE);
        }
        return response;
    }
}
