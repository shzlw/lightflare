package com.lightflare.server.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EmbeddingVector {

    private final String value;

    public EmbeddingVector(String value) {
        this.value = value;
    }

    public static EmbeddingVector of(String value) {
        if (value == null) {
            return null;
        }
        return new EmbeddingVector(value);
    }

    public static EmbeddingVector fromList(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return new EmbeddingVector("[]");
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            sb.append(vector.get(i));
            if (i < vector.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return new EmbeddingVector(sb.toString());
    }

    public String value() {
        return value;
    }

    public List<Float> asList() {
        if (value == null || value.isEmpty() || "[]".equals(value)) {
            return List.of();
        }

        String trimmed = value.replaceAll("\\[|\\]", "");
        if (trimmed.isEmpty()) {
            return List.of();
        }

        String[] parts = trimmed.split(",");
        List<Float> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(Float.parseFloat(part));
        }
        return result;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddingVector that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
