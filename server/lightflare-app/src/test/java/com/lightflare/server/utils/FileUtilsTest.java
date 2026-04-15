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
    void shouldThrowWhenPromptTemplateDoesNotExist() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileUtils.loadPromptTemplate("missing-template.txt")
        );

        assertEquals("Resource not found: prompt-templates/missing-template.txt", exception.getMessage());
    }
}
