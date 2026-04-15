package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMStepResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepResponseCanonicalizerTest {

    private final StepResponseCanonicalizer canonicalizer = new StepResponseCanonicalizer();

    @Test
    void useToolWithoutToolNameBecomesDirectResponseLimitation() {
        LLMStepResponse response = new LLMStepResponse();
        response.setAction(LLMStepResponse.Action.USE_TOOL);

        LLMStepResponse result = canonicalizer.canonicalize(response, null);

        assertEquals(LLMStepResponse.Action.DIRECT_RESPONSE, result.getAction());
        assertEquals(List.of(), result.getMissingInputs());
        assertFalse(result.getStepComplete());
        assertTrue(result.getResponse().contains("no available tool matches"));
    }

    @Test
    void requestToolInputForToolNameBecomesDirectResponseLimitation() {
        LLMStepResponse response = new LLMStepResponse();
        response.setAction(LLMStepResponse.Action.REQUEST_TOOL_INPUT);
        response.setMissingInputs(List.of("toolName"));

        LLMStepResponse result = canonicalizer.canonicalize(response, null);

        assertEquals(LLMStepResponse.Action.DIRECT_RESPONSE, result.getAction());
        assertEquals(List.of(), result.getMissingInputs());
        assertFalse(result.getStepComplete());
        assertTrue(result.getResponse().contains("no available tool matches"));
    }
}
