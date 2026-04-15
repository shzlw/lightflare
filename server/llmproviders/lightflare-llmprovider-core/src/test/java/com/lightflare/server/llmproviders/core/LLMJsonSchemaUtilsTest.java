package com.lightflare.server.llmproviders.core;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMJsonSchemaUtilsTest {

    @Test
    void shouldGenerateStrictSchemaForStructuredResponse() {
        ObjectNode schema = LLMJsonSchemaUtils.generateSchema(LLMGetResponse.class);

        assertEquals("object", schema.path("type").asText());
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertTrue(schema.path("properties").has("thoughtProcess"));
        assertTrue(schema.path("properties").has("action"));
        assertTrue(schema.path("properties").has("selectedSkill"));
        assertTrue(schema.path("properties").has("toolCall"));
        assertTrue(schema.path("properties").has("missingInputs"));
        assertTrue(schema.path("properties").has("response"));

        JsonNode actionValues = schema.path("properties").path("action");
        assertTrue(containsText(actionValues, "USE_TOOL"));
        assertTrue(containsText(actionValues, "REQUEST_TOOL_INPUT"));
        assertTrue(containsText(actionValues, "REQUEST_SKILL_INSTRUCTIONS"));
        assertTrue(containsText(actionValues, "DESIGN_INSTRUCTIONS"));
        assertTrue(containsText(actionValues, "DIRECT_RESPONSE"));
    }

    @Test
    void shouldGenerateProviderSchemaObjectForStructuredOutput() {
        ObjectNode schema = LLMJsonSchemaUtils.structuredOutputSchema(LLMGetResponse.class);

        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.has("properties"));
        assertFalse(schema.has("schema"));
        assertFalse(schema.has("name"));
    }

    private boolean containsText(JsonNode values, String expected) {
        if (values.isTextual() && expected.equals(values.asText())) {
            return true;
        }
        if (values.isObject() || values.isArray()) {
            for (JsonNode value : values) {
                if (containsText(value, expected)) {
                    return true;
                }
            }
        }
        return false;
    }
}
