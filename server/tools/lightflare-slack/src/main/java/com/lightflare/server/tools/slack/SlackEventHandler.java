package com.lightflare.server.tools.slack;

import com.lightflare.server.messaging.MessagingAppConnector;
import com.lightflare.server.messaging.MessagingAppConnectorRequest;
import com.lightflare.server.messaging.MessagingAppUserResolver;
import com.slack.api.bolt.App;
import com.slack.api.model.event.AppMentionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Slf4j
public class SlackEventHandler {

    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@[^>]+>");
    private static final String USER_NOT_LINKED_MESSAGE =
            "Your Slack user is not linked to an app user yet. Ask an admin to create an identity mapping.";

    public SlackEventHandler(App app,
                             SlackProperties properties,
                             SlackChannelPolicy channelPolicy,
                             MessagingAppUserResolver slackMessagingAppUserResolver,
                             MessagingAppConnector messagingAppConnector,
                             SlackMessageService slackMessageService,
                             Executor slackAgentExecutor) {
        app.event(AppMentionEvent.class, (payload, ctx) -> {
            AppMentionEvent event = payload.getEvent();
            if (shouldSkip(ctx.getRetryNum(), event, channelPolicy)) {
                return ctx.ack();
            }

            String prompt = extractPrompt(event.getText());
            if (!StringUtils.hasText(prompt)) {
                slackAgentExecutor.execute(() -> replyWithError(
                        slackMessageService,
                        event,
                        properties.isReplyInThread()
                                ? resolveReplyThreadTs(event)
                                : null,
                        "Mention me with a prompt after the @ mention."
                ));
                return ctx.ack();
            }

            slackAgentExecutor.execute(() -> handleMention(
                    event,
                    prompt,
                    properties,
                    slackMessagingAppUserResolver,
                    messagingAppConnector,
                    slackMessageService
            ));
            return ctx.ack();
        });
    }

    private boolean shouldSkip(Integer retryNum, AppMentionEvent event, SlackChannelPolicy channelPolicy) {
        if (retryNum != null && retryNum > 0) {
            log.info("Skipping Slack retry delivery for channelId={}, eventTs={}, retryNum={}",
                    event.getChannel(), event.getEventTs(), retryNum);
            return true;
        }
        if (event == null) {
            return true;
        }
        if (!channelPolicy.isTeamAllowed(event.getTeam())) {
            log.info("Ignoring Slack event from disallowed teamId={}", event.getTeam());
            return true;
        }
        if (!channelPolicy.isMentionChannelAllowed(event.getChannel())) {
            log.info("Ignoring Slack mention from disallowed channelId={}", event.getChannel());
            return true;
        }
        if (StringUtils.hasText(event.getBotId())) {
            log.info("Ignoring Slack bot-originated mention for channelId={}, eventTs={}",
                    event.getChannel(), event.getEventTs());
            return true;
        }
        if (StringUtils.hasText(event.getSubtype())) {
            log.info("Ignoring Slack mention subtype={} for channelId={}, eventTs={}",
                    event.getSubtype(), event.getChannel(), event.getEventTs());
            return true;
        }
        return !StringUtils.hasText(event.getUser());
    }

    private void handleMention(AppMentionEvent event,
                               String prompt,
                               SlackProperties properties,
                               MessagingAppUserResolver slackMessagingAppUserResolver,
                               MessagingAppConnector messagingAppConnector,
                               SlackMessageService slackMessageService) {
        String replyThreadTs = properties.isReplyInThread() ? resolveReplyThreadTs(event) : null;
        try {
            String appUserId = slackMessagingAppUserResolver.resolveAppUserId(event.getUser())
                    .orElse(null);
            if (!StringUtils.hasText(appUserId)) {
                replyWithError(slackMessageService, event, replyThreadTs, USER_NOT_LINKED_MESSAGE);
                return;
            }
            MessagingAppConnectorRequest request = new MessagingAppConnectorRequest(
                    buildSessionId(event, appUserId),
                    appUserId,
                    prompt
            );
            String response = messagingAppConnector.process(request);
            if (StringUtils.hasText(response)) {
                slackMessageService.postMessage(event.getChannel(), response, replyThreadTs);
            }
        } catch (Exception exception) {
            log.error("Failed to process Slack mention for teamId={}, channelId={}, eventTs={}",
                    event.getTeam(), event.getChannel(), event.getEventTs(), exception);
            replyWithError(slackMessageService, event, replyThreadTs, "I hit an error while processing that request.");
        }
    }

    private void replyWithError(SlackMessageService slackMessageService,
                                AppMentionEvent event,
                                String replyThreadTs,
                                String errorText) {
        try {
            slackMessageService.postMessage(event.getChannel(), errorText, replyThreadTs);
        } catch (Exception postException) {
            log.error("Failed to post Slack error reply for channelId={}, eventTs={}",
                    event.getChannel(), event.getEventTs(), postException);
        }
    }

    private String extractPrompt(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String withoutMentions = USER_MENTION_PATTERN.matcher(text).replaceAll(" ");
        String normalized = withoutMentions.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveConversationThreadTs(AppMentionEvent event) {
        return StringUtils.hasText(event.getThreadTs()) ? event.getThreadTs() : event.getTs();
    }

    private String resolveReplyThreadTs(AppMentionEvent event) {
        return resolveConversationThreadTs(event);
    }

    private String buildSessionId(AppMentionEvent event, String appUserId) {
        return "slack:" + event.getTeam() + ":" + event.getChannel() + ":" + resolveConversationThreadTs(event) + ":" + appUserId;
    }
}
