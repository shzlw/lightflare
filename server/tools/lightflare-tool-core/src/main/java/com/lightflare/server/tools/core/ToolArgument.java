package com.lightflare.server.tools.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolArgument {

    private String name;

    private Object value;

    public String asString() {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue.isBlank() ? null : stringValue;
        }
        return String.valueOf(value);
    }

    public Integer asInteger() {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Long longValue) {
            return Math.toIntExact(longValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            return Integer.valueOf(stringValue);
        }
        throw new IllegalArgumentException("Argument '" + name + "' is not an integer");
    }

    public Boolean asBoolean() {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.valueOf(stringValue);
        }
        throw new IllegalArgumentException("Argument '" + name + "' is not a boolean");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asObject() {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        throw new IllegalArgumentException("Argument '" + name + "' is not an object");
    }

    @SuppressWarnings("unchecked")
    public List<Object> asArray() {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> listValue) {
            return (List<Object>) listValue;
        }
        throw new IllegalArgumentException("Argument '" + name + "' is not an array");
    }
}
