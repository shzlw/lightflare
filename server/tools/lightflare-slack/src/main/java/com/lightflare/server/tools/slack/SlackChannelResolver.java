package com.lightflare.server.tools.slack;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SlackChannelResolver {

    private final Slack slack;
    private final String botToken;
    private final Map<String, String> channelIdByNormalizedName = new ConcurrentHashMap<>();

    public SlackChannelResolver(Slack slack, String botToken) {
        this.slack = slack;
        this.botToken = botToken;
    }

    public Optional<String> resolveChannelId(String channelRef) {
        String normalizedRef = normalizeChannelRef(channelRef);
        if (normalizedRef == null) {
            return Optional.empty();
        }
        if (looksLikeChannelId(normalizedRef)) {
            return Optional.of(normalizedRef);
        }

        String cachedChannelId = channelIdByNormalizedName.get(normalizedRef);
        if (StringUtils.hasText(cachedChannelId)) {
            return Optional.of(cachedChannelId);
        }

        try {
            String cursor = null;
            do {
                String requestCursor = cursor;
                ConversationsListResponse response = slack.methods(botToken)
                        .conversationsList(req -> req
                                .limit(1000)
                                .cursor(requestCursor)
                                .excludeArchived(true)
                                .types(java.util.List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                        );
                if (!response.isOk()) {
                    throw new IllegalStateException("Slack API error: " + response.getError());
                }

                if (response.getChannels() != null) {
                    for (Conversation channel : response.getChannels()) {
                        if (channel == null || !StringUtils.hasText(channel.getId()) || !StringUtils.hasText(channel.getName())) {
                            continue;
                        }
                        String normalizedChannelName = normalizeChannelName(channel.getName());
                        channelIdByNormalizedName.putIfAbsent(normalizedChannelName, channel.getId());
                        if (normalizedRef.equals(normalizedChannelName)) {
                            return Optional.of(channel.getId());
                        }
                    }
                }
                cursor = response.getResponseMetadata() != null
                        ? response.getResponseMetadata().getNextCursor()
                        : null;
            } while (StringUtils.hasText(cursor));
        } catch (IOException | SlackApiException exception) {
            throw new RuntimeException("Failed to resolve Slack channel: " + channelRef, exception);
        }

        return Optional.empty();
    }

    private boolean looksLikeChannelId(String value) {
        return value.matches("^[CGD][A-Z0-9]+$");
    }

    private String normalizeChannelRef(String channelRef) {
        if (!StringUtils.hasText(channelRef)) {
            return null;
        }
        String trimmed = channelRef.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return looksLikeChannelId(trimmed) ? trimmed : normalizeChannelName(trimmed);
    }

    private String normalizeChannelName(String channelName) {
        return channelName.trim().toLowerCase(Locale.ROOT);
    }
}
