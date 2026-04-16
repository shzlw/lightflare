package com.lightflare.server.tools.slack;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lightflare.tools.slack", name = "enabled", havingValue = "true")
public class SlackPostMessageTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("post-message")
            .description("Post a message to an allowed Slack channel.")
            .category("communication")
            .integrationId("slack")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("channel")
                            .type("string")
                            .description("The Slack channel name or ID.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("text")
                            .type("string")
                            .description("The message text to post.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("thread_ts")
                            .type("string")
                            .description("Optional timestamp of a parent message to reply in a thread.")
                            .required(false)
                            .build()
            ))
            .build();

    private final SlackChannelPolicy slackChannelPolicy;
    private final SlackChannelResolver slackChannelResolver;
    private final SlackMessageService slackMessageService;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String channelRef = getStringArgument(arguments, "channel");
        String text = getStringArgument(arguments, "text");
        String threadTs = getOptionalStringArgument(arguments, "thread_ts");
        if (channelRef == null) {
            return ToolResult.failure("Missing required argument: channel");
        }
        if (text == null) {
            return ToolResult.failure("Missing required argument: text");
        }

        try {
            String channelId = slackChannelResolver.resolveChannelId(channelRef)
                    .orElseThrow(() -> new IllegalArgumentException("Slack channel not found: " + channelRef));
            slackChannelPolicy.assertPostChannelAllowed(channelId);
            String ts = slackMessageService.postMessage(channelId, text, threadTs);
            return ToolResult.success("Posted Slack message to channel=" + channelRef + " (channelId=" + channelId + "), ts=" + ts);
        } catch (Exception exception) {
            return ToolResult.failure(exception.getMessage() != null
                    ? exception.getMessage()
                    : "Failed to post Slack message");
        }
    }

    private String getStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
