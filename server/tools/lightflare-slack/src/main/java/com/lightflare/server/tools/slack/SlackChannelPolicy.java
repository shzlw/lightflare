package com.lightflare.server.tools.slack;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SlackChannelPolicy {

    private final SlackProperties properties;
    private final SlackChannelResolver slackChannelResolver;

    public SlackChannelPolicy(SlackProperties properties, SlackChannelResolver slackChannelResolver) {
        this.properties = properties;
        this.slackChannelResolver = slackChannelResolver;
    }

    public boolean isTeamAllowed(String teamId) {
        return matches(properties.getAllowedTeamIds(), teamId);
    }

    public boolean isMentionChannelAllowed(String channelId) {
        return matchesResolvedChannelId(properties.getAllowedMentionChannels(), channelId);
    }

    public boolean isPostChannelAllowed(String channelId) {
        return matchesResolvedChannelId(properties.getAllowedPostChannels(), channelId);
    }

    public void assertPostChannelAllowed(String channelId) {
        if (!isPostChannelAllowed(channelId)) {
            throw new IllegalArgumentException("Slack channel is not allowed for posting: " + channelId);
        }
    }

    private boolean matches(List<String> allowedValues, String actualValue) {
        if (!StringUtils.hasText(actualValue)) {
            return false;
        }
        List<String> normalizedAllowedValues = allowedValues == null ? List.of() : allowedValues.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (normalizedAllowedValues.isEmpty()) {
            return true;
        }
        String normalizedActualValue = actualValue.trim();
        return normalizedAllowedValues.contains(normalizedActualValue);
    }

    private boolean matchesResolvedChannelId(List<String> allowedChannelRefs, String actualChannelId) {
        if (!StringUtils.hasText(actualChannelId)) {
            return false;
        }
        if (allowedChannelRefs == null || allowedChannelRefs.stream().noneMatch(StringUtils::hasText)) {
            return true;
        }
        String normalizedActualChannelId = actualChannelId.trim();
        for (String allowedChannelRef : allowedChannelRefs) {
            Optional<String> resolvedAllowedChannelId = slackChannelResolver.resolveChannelId(allowedChannelRef);
            if (resolvedAllowedChannelId.isPresent() && normalizedActualChannelId.equals(resolvedAllowedChannelId.get())) {
                return true;
            }
        }
        return false;
    }

}
