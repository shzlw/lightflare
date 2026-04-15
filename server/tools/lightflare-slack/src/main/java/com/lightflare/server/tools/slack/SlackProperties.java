package com.lightflare.server.tools.slack;

import com.lightflare.server.tools.core.ToolSelection;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "lightflare.tools.slack")
public class SlackProperties implements ToolSelection {

    private boolean enabled = false;
    private List<String> enabledTools = List.of();
    private String botToken;
    private String signingSecret;
    private String appToken;
    private boolean replyInThread = true;
    private List<String> allowedTeamIds = List.of();
    private List<String> allowedMentionChannels = List.of();
    private List<String> allowedPostChannels = List.of();
    private int agentExecutorCorePoolSize = 2;
    private int agentExecutorMaxPoolSize = 4;
    private int agentExecutorQueueCapacity = 100;

    @Override
    public String integrationId() {
        return "slack";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public List<String> enabledTools() {
        return enabledTools;
    }
}
