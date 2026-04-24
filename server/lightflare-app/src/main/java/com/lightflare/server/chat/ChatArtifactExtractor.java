package com.lightflare.server.chat;

import com.lightflare.server.utils.JsonUtils;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatArtifactExtractor {

    private static final Pattern NUMBERED_PLAN_PATTERN = Pattern.compile("(?m)^\\s*(\\d+\\.|-\\s+\\[[ xX]\\]|-\\s+)\\s+.+");
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?is)```json\\s*([\\s\\S]*?)```");
    private static final Pattern DIFF_BLOCK_PATTERN = Pattern.compile("(?is)```diff\\s*([\\s\\S]*?)```");
    private static final Pattern ARTIFACT_BLOCK_PATTERN = Pattern.compile("(?is)```artifact\\s*([\\s\\S]*?)```");

    public ChatArtifactCandidate extract(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String normalized = content.trim();
        ChatArtifactCandidate structuredArtifact = extractStructuredArtifact(normalized);
        if (structuredArtifact != null) {
            return structuredArtifact;
        }
        String jsonBlock = extractBlock(JSON_BLOCK_PATTERN, normalized);
        if (StringUtils.hasText(jsonBlock)) {
            return new ChatArtifactCandidate("json", "Generated JSON", jsonBlock, "{\"renderer\":\"json\"}");
        }

        String diffBlock = extractBlock(DIFF_BLOCK_PATTERN, normalized);
        if (StringUtils.hasText(diffBlock)) {
            return new ChatArtifactCandidate("diff", "Generated Diff", diffBlock, "{\"renderer\":\"diff\"}");
        }

        if (looksLikeJson(normalized)) {
            return new ChatArtifactCandidate("json", "Generated JSON", normalized, "{\"renderer\":\"json\"}");
        }
        if (looksLikeDiff(normalized)) {
            return new ChatArtifactCandidate("diff", "Generated Diff", normalized, "{\"renderer\":\"diff\"}");
        }
        if (looksLikePlan(normalized)) {
            return new ChatArtifactCandidate("plan", "Generated Plan", normalized, "{\"renderer\":\"plan\"}");
        }
        if (looksLikeLargeArtifact(normalized)) {
            return new ChatArtifactCandidate("text", "Generated Artifact", normalized, "{\"renderer\":\"text\"}");
        }
        return null;
    }

    private boolean looksLikeJson(String content) {
        return (content.startsWith("{") && content.endsWith("}"))
                || (content.startsWith("[") && content.endsWith("]"));
    }

    private boolean looksLikeDiff(String content) {
        return content.contains("```diff")
                || content.contains("@@")
                || content.contains("\n+")
                || content.contains("\n-");
    }

    private boolean looksLikePlan(String content) {
        return NUMBERED_PLAN_PATTERN.matcher(content).find()
                && content.lines().filter(line -> NUMBERED_PLAN_PATTERN.matcher(line).find()).count() >= 3;
    }

    private boolean looksLikeLargeArtifact(String content) {
        long lineCount = content.lines().count();
        return content.length() >= 280 || lineCount >= 8;
    }

    private String extractBlock(Pattern pattern, String content) {
        var matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String extracted = matcher.group(1);
        return StringUtils.hasText(extracted) ? extracted.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private ChatArtifactCandidate extractStructuredArtifact(String content) {
        String artifactBlock = extractBlock(ARTIFACT_BLOCK_PATTERN, content);
        if (!StringUtils.hasText(artifactBlock)) {
            return null;
        }
        try {
            Object parsed = JsonUtils.fromJson(artifactBlock);
            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                return null;
            }
            String artifactType = asString(parsedMap.get("artifactType"));
            if (!StringUtils.hasText(artifactType)) {
                return null;
            }
            String title = asString(parsedMap.get("title"));
            Object artifactContent = parsedMap.get("content");
            String artifactContentJson = artifactContent instanceof String text
                    ? text
                    : JsonUtils.toJson(artifactContent);
            if (!StringUtils.hasText(artifactContentJson)) {
                return null;
            }
            Object metadata = parsedMap.get("metadata");
            String metadataJson = metadata == null
                    ? null
                    : metadata instanceof String text ? text : JsonUtils.toJson(metadata);
            return new ChatArtifactCandidate(
                    artifactType.trim().toLowerCase(),
                    StringUtils.hasText(title) ? title.trim() : defaultTitleFor(artifactType),
                    artifactContentJson,
                    metadataJson
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String defaultTitleFor(String artifactType) {
        return switch (artifactType == null ? "" : artifactType.trim().toLowerCase()) {
            case "chart" -> "Generated Chart";
            case "json" -> "Generated JSON";
            case "diff" -> "Generated Diff";
            case "plan" -> "Generated Plan";
            default -> "Generated Artifact";
        };
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    public record ChatArtifactCandidate(
            String artifactType,
            String title,
            String content,
            String metadata
    ) {
    }
}
