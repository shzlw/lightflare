package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolArgument;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpToolUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String getStringArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public static Map<String, String> getHeadersArgument(List<ToolArgument> arguments) {
        ToolArgument headersArgument = arguments == null ? null : arguments.stream()
                .filter(argument -> "headers".equals(argument.getName()))
                .findFirst()
                .orElse(null);
        if (headersArgument == null || headersArgument.getValue() == null) {
            return Map.of();
        }
        if (headersArgument.getValue() instanceof Map<?, ?> headers) {
            return validateHeaders(headers);
        }
        return parseHeaders(headersArgument.asString());
    }

    private static Map<String, String> parseHeaders(String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return Map.of();
        }

        try {
            return validateHeaders(OBJECT_MAPPER.readValue(headersJson, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            throw new IllegalArgumentException("headers must be a JSON object of string values", e);
        }
    }

    private static Map<String, String> validateHeaders(Map<?, ?> headers) {
        Map<String, String> validatedHeaders = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !StringUtils.hasText(name)
                    || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException("headers must be a JSON object of string values");
            }
            validatedHeaders.put(name, value);
        }
        return Map.copyOf(validatedHeaders);
    }
}
