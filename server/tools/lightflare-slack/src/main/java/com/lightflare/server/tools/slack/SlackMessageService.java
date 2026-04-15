package com.lightflare.server.tools.slack;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class SlackMessageService {

    private final Slack slack;
    private final String botToken;

    public String postMessage(String channelId, String text) throws IOException, SlackApiException {
        return postMessage(channelId, text, null);
    }

    public String postMessage(String channelId, String text, String threadTs) throws IOException, SlackApiException {
        log.info("Posting Slack message to channelId={}, textLength={}",
                channelId, text != null ? text.length() : 0);
        ChatPostMessageResponse response = slack.methods(botToken)
                .chatPostMessage(req -> req
                        .channel(channelId)
                        .text(text)
                        .threadTs(threadTs)
                );

        if (!response.isOk()) {
            throw new RuntimeException("Slack API error: " + response.getError());
        }
        log.info("Posted Slack message to channelId={}, ts={}", channelId, response.getTs());
        return response.getTs();
    }
}
