package com.lightflare.server.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkflowInputResolver {

    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*\\}\\}");

    /**
     * Resolves variables in the input mapping using the provided context.
     * 
     * @param inputMapping The raw input mapping containing {{ path.to.value }} templates.
     * @param context The execution context containing results of previous steps.
     * @return A map with all templates resolved to their actual values.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolve(Map<String, Object> inputMapping, Map<String, Object> context) {
        if (inputMapping == null) {
            return new HashMap<>();
        }
        return (Map<String, Object>) resolveRecursive(inputMapping, context);
    }

    @SuppressWarnings("unchecked")
    private Object resolveRecursive(Object value, Map<String, Object> context) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolved = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolved.put(entry.getKey(), resolveRecursive(entry.getValue(), context));
            }
            return resolved;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                resolved.add(resolveRecursive(item, context));
            }
            return resolved;
        } else if (value instanceof String) {
            return resolveString((String) value, context);
        }
        return value;
    }

    private Object resolveString(String value, Map<String, Object> context) {
        Matcher matcher = PATTERN.matcher(value);
        
        // Optimistic handling: if the string is EXACTLY "{{ some.path }}", 
        // return the object directly to preserve types (e.g. if the resolved value is a Map or Boolean).
        if (matcher.matches()) {
            String path = matcher.group(1);
            return getValueByPath(path, context);
        }

        // Otherwise, substitute inside the string (forced string conversion)
        StringBuilder sb = new StringBuilder();
        int lastPos = 0;
        matcher.reset();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            sb.append(value, lastPos, matcher.start());
            String path = matcher.group(1);
            Object resolved = getValueByPath(path, context);
            sb.append(resolved != null ? resolved.toString() : "");
            lastPos = matcher.end();
        }
        
        if (!found) {
            return value;
        }
        
        sb.append(value.substring(lastPos));
        return sb.toString();
    }

    private Object getValueByPath(String path, Map<String, Object> context) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        String[] parts = path.split("\\.");
        Object current = context;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
}
