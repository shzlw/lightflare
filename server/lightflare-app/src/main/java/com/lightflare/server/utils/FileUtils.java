package com.lightflare.server.utils;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class FileUtils {

    private static final String PROMPT_TEMPLATES_PATH = "prompt-templates/";
    private static final Map<String, String> PROMPT_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    public String loadResourceAsString(String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try {
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + resourcePath, e);
        }
    }

    public String loadPromptTemplate(String templateName) {
        return PROMPT_TEMPLATE_CACHE.computeIfAbsent(
                templateName,
                key -> loadResourceAsString(PROMPT_TEMPLATES_PATH + key)
        );
    }
}
