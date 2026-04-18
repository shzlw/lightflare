package com.lightflare.server.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUtilsTest {

    @Test
    void shouldLoadPromptTemplateFromResources() {
        String content = FileUtils.loadPromptTemplate("sample-template.txt");

        assertEquals("sample prompt template\n", content);
    }

    @Test
    void shouldLoadToolPromptTemplateFromResources() {
        String content = FileUtils.loadToolPromptTemplate("sample-tool-template.txt");

        assertEquals("sample tool prompt template\n", content);
    }

    @Test
    void shouldThrowWhenPromptTemplateDoesNotExist() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileUtils.loadPromptTemplate("missing-template.txt")
        );

        assertEquals("Resource not found: agent-prompt-templates/missing-template.txt", exception.getMessage());
    }

    @Test
    void shouldThrowWhenToolPromptTemplateDoesNotExist() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileUtils.loadToolPromptTemplate("missing-tool-template.txt")
        );

        assertEquals("Resource not found: tool-prompt-templates/missing-tool-template.txt", exception.getMessage());
    }
}
